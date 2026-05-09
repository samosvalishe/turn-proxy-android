#!/bin/bash
# vk-turn-proxy server control script.
# Streamed via SSH stdin from Android (bash -s -- <subcmd> <args>).
# Output protocol: KEY=VALUE lines, "LOG: ..." for free text, final RESULT=ok|err.
#
# Runtime modes:
#   systemd  — unit /etc/systemd/system/vk-turn-proxy.service + launch.sh + run.args
#   nohup    — legacy; nohup + PIDFILE (для машин без systemd)
# Маркер режима — $PREFIX/runtime (одна строка: systemd|nohup).

set -eu

PREFIX="/opt/vk-turn"
PIDFILE="$PREFIX/proxy.pid"
WRAPFILE="$PREFIX/wrap.key"
LOGFILE="$PREFIX/server.log"
VERFILE="$PREFIX/version"
RUNTIMEFILE="$PREFIX/runtime"
ARGSFILE="$PREFIX/run.args"
LAUNCHER="$PREFIX/launch.sh"
UNIT_PATH="/etc/systemd/system/vk-turn-proxy.service"
UNIT_NAME="vk-turn-proxy.service"
BASE_URL="https://github.com/Moroka8/vk-turn-proxy/releases/latest/download"

log()  { echo "LOG: $*"; }
emit() { echo "$1=$2"; }
die()  { echo "ERR=$*"; echo "RESULT=err"; trap - EXIT; exit 1; }

trap 'rc=$?; if [ $rc -ne 0 ]; then echo "ERR=script exit $rc"; echo "RESULT=err"; fi' EXIT

detect_arch() {
    local m
    m=$(uname -m)
    case "$m" in
        x86_64|amd64) echo "server-linux-amd64" ;;
        aarch64|arm64) echo "server-linux-arm64" ;;
        *) die "unsupported arch: $m" ;;
    esac
}

binpath() { echo "$PREFIX/$(detect_arch)"; }

# Доступен ли systemd на этой машине.
has_systemd() {
    command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]
}

current_runtime() {
    if [ -f "$RUNTIMEFILE" ]; then
        cat "$RUNTIMEFILE"
    elif has_systemd; then
        echo "systemd"
    else
        echo "nohup"
    fi
}

# --- состояние ---

is_running_nohup() {
    if [ -f "$PIDFILE" ]; then
        local pid
        pid=$(cat "$PIDFILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
        rm -f "$PIDFILE"
    fi
    if pgrep -f "^$PREFIX/server-linux-" >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

is_running_systemd() {
    systemctl is-active --quiet "$UNIT_NAME"
}

# echo текущий PID процесса (или пусто).
current_pid() {
    case "$(current_runtime)" in
        systemd)
            local p
            p=$(systemctl show -p MainPID --value "$UNIT_NAME" 2>/dev/null || echo 0)
            [ "$p" != "0" ] && [ -n "$p" ] && echo "$p"
            ;;
        nohup)
            [ -f "$PIDFILE" ] && cat "$PIDFILE" 2>/dev/null
            ;;
    esac
}

# Возвращает cmdline текущего процесса (или пусто).
current_cmdline() {
    local p
    p=$(current_pid)
    if [ -n "$p" ] && [ -r "/proc/$p/cmdline" ]; then
        tr '\0' ' ' < "/proc/$p/cmdline"
    fi
}

cmd_probe() {
    local bin
    bin=$(binpath)
    if [ -x "$bin" ]; then
        emit INSTALLED yes
        local sha
        sha=$(sha256sum "$bin" 2>/dev/null | awk '{print $1}')
        [ -n "$sha" ] && emit BIN_SHA256 "$sha"
        if [ -f "$VERFILE" ]; then
            emit VERSION "$(cat "$VERFILE")"
        fi
    else
        emit INSTALLED no
    fi
    emit RUNTIME "$(current_runtime)"

    local running=no
    case "$(current_runtime)" in
        systemd) is_running_systemd && running=yes ;;
        nohup)   is_running_nohup   && running=yes ;;
    esac

    if [ "$running" = "yes" ]; then
        emit RUNNING yes
        local cmdline
        cmdline=$(current_cmdline)
        if echo "$cmdline" | grep -q -- "-vless"; then emit VLESS yes; else emit VLESS no; fi
        if echo "$cmdline" | grep -q -- "-vless-bond"; then emit VLESS_BOND yes; else emit VLESS_BOND no; fi
        if echo "$cmdline" | grep -q -- "-wrap"; then emit WRAP yes; else emit WRAP no; fi
    else
        emit RUNNING no
    fi
    echo "RESULT=ok"
    trap - EXIT
}

_dl() {
    local url=$1 out=$2
    if command -v curl >/dev/null 2>&1; then
        curl -sSL --fail -o "$out" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$out" "$url"
    else
        die "neither curl nor wget present"
    fi
}

_resolve_version() {
    # GitHub `latest/download/<asset>` отвечает 302 с Location, содержащим тег
    # вида .../releases/download/vX.Y.Z/<asset>. Берём из URL.
    local url="$1" loc=""
    if command -v curl >/dev/null 2>&1; then
        loc=$(curl -sI "$url" 2>/dev/null | awk -F': ' 'tolower($1)=="location"{print $2}' | tr -d '\r' | head -n1)
    elif command -v wget >/dev/null 2>&1; then
        loc=$(wget --spider --server-response "$url" 2>&1 | awk '/[Ll]ocation:/{print $2}' | tr -d '\r' | head -n1)
    fi
    echo "$loc" | sed -nE 's#.*/releases/download/([^/]+)/.*#\1#p'
}

# Записать systemd unit и launcher. Идемпотентно — сравниваем содержимое,
# чтобы не дёргать daemon-reload зря.
_install_systemd_unit() {
    local need_reload=0

    local launcher_content
    launcher_content=$(cat <<'LAUNCH_EOF'
#!/bin/bash
# Авто-сгенерирован vk-turn-control.sh — не редактировать вручную.
set -e
PREFIX=/opt/vk-turn
m=$(uname -m)
case "$m" in
    x86_64|amd64) arch=server-linux-amd64 ;;
    aarch64|arm64) arch=server-linux-arm64 ;;
    *) echo "unsupported arch: $m" >&2; exit 1 ;;
esac
[ -f "$PREFIX/run.args" ] || { echo "run.args missing" >&2; exit 1; }
mapfile -t a < "$PREFIX/run.args"
exec "$PREFIX/$arch" "${a[@]}"
LAUNCH_EOF
)

    if [ ! -f "$LAUNCHER" ] || [ "$(cat "$LAUNCHER" 2>/dev/null)" != "$launcher_content" ]; then
        printf '%s\n' "$launcher_content" > "$LAUNCHER"
        chmod 755 "$LAUNCHER"
    fi

    local unit_content
    unit_content=$(cat <<UNIT_EOF
[Unit]
Description=vk-turn-proxy server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=$LAUNCHER
Restart=on-failure
RestartSec=2
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
UNIT_EOF
)

    if [ ! -f "$UNIT_PATH" ] || [ "$(cat "$UNIT_PATH" 2>/dev/null)" != "$unit_content" ]; then
        printf '%s\n' "$unit_content" > "$UNIT_PATH"
        chmod 644 "$UNIT_PATH"
        need_reload=1
    fi

    if [ "$need_reload" = "1" ]; then
        systemctl daemon-reload
    fi
    systemctl enable "$UNIT_NAME" >/dev/null 2>&1 || true
}

# Завершить процесс старого nohup-режима, если он остался от прошлой версии.
_kill_legacy_nohup() {
    if [ -f "$PIDFILE" ]; then
        local pid
        pid=$(cat "$PIDFILE" 2>/dev/null || echo "")
        if [ -n "$pid" ]; then
            kill "$pid" 2>/dev/null || true
            sleep 1
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$PIDFILE"
    fi
    pkill -9 -f "^$PREFIX/server-linux-" 2>/dev/null || true
}

cmd_install() {
    mkdir -p "$PREFIX" || die "cannot create $PREFIX"
    [ -w "$PREFIX" ] || die "$PREFIX not writable"
    local bin name asset_url tmp ver curver
    name=$(detect_arch)
    bin="$PREFIX/$name"
    tmp="$bin.new"
    asset_url="$BASE_URL/$name"

    log "resolving latest version"
    ver=$(_resolve_version "$asset_url")
    if [ -z "$ver" ]; then
        die "cannot resolve latest version (no Location header from $BASE_URL)"
    fi
    curver=""
    [ -f "$VERFILE" ] && curver=$(cat "$VERFILE")

    local cached=0
    if [ -x "$bin" ] && [ "$ver" = "$curver" ]; then
        cached=1
    fi

    if [ "$cached" = "0" ]; then
        log "downloading $name @ $ver"
        if ! _dl "$asset_url" "$tmp"; then
            rm -f "$tmp"
            die "binary download failed"
        fi
        # sanity-check: GitHub отдаёт HTML-404 при отсутствии ассета.
        local size
        size=$(wc -c < "$tmp" 2>/dev/null || echo 0)
        if [ "$size" -lt 100000 ]; then
            rm -f "$tmp"
            die "downloaded file too small ($size bytes)"
        fi
        chmod +x "$tmp"
        if [ -f "$bin" ]; then
            cp -f "$bin" "$bin.bak" 2>/dev/null || true
        fi
        mv -f "$tmp" "$bin"
        echo "$ver" > "$VERFILE"
    fi

    # Решаем режим запуска и подготавливаем инфраструктуру.
    local was_running=0
    case "$(current_runtime)" in
        systemd) is_running_systemd && was_running=1 ;;
        nohup)   is_running_nohup   && was_running=1 ;;
    esac

    if has_systemd; then
        # Любой остаточный nohup-процесс убиваем перед enable юнита, иначе он
        # держит порт и systemctl start не поднимется. Триггеримся на PIDFILE
        # ИЛИ висящий бинарь — RUNTIMEFILE на свежей машине ещё не показателен.
        if [ -f "$PIDFILE" ] || pgrep -f "^$PREFIX/server-linux-" >/dev/null 2>&1; then
            log "killing legacy nohup process before systemd takeover"
            _kill_legacy_nohup
        fi
        _install_systemd_unit
        echo "systemd" > "$RUNTIMEFILE"
        emit RUNTIME systemd
    else
        echo "nohup" > "$RUNTIMEFILE"
        emit RUNTIME nohup
    fi

    if [ "$cached" = "1" ]; then
        emit STAGE cached
    else
        emit STAGE downloaded
    fi
    emit BIN "$name"
    emit VERSION "$ver"
    if [ "$was_running" = "1" ] && [ "$cached" = "0" ]; then
        emit NEEDS_RESTART yes
    fi
    echo "RESULT=ok"
    trap - EXIT
}

# parse --key=value args into globals
ARG_LISTEN=""
ARG_CONNECT=""
ARG_VLESS=""
ARG_VLESS_BOND=""
ARG_WRAP_KEY=""
ARG_TAIL=80

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --listen=*)
                ARG_LISTEN="${1#*=}"
                [[ "$ARG_LISTEN" =~ ^[a-zA-Z0-9.:_-]+$ ]] || die "bad --listen"
                ;;
            --connect=*)
                ARG_CONNECT="${1#*=}"
                [[ "$ARG_CONNECT" =~ ^[a-zA-Z0-9.:_-]+$ ]] || die "bad --connect"
                ;;
            --vless) ARG_VLESS=1 ;;
            --vless-bond) ARG_VLESS_BOND=1 ;;
            --wrap-key=*)
                ARG_WRAP_KEY="${1#*=}"
                [[ "$ARG_WRAP_KEY" =~ ^[0-9a-fA-F]{64}$ ]] || die "bad --wrap-key (need 64 hex)"
                ;;
            --tail=*)
                ARG_TAIL="${1#*=}"
                [[ "$ARG_TAIL" =~ ^[0-9]+$ ]] || die "bad --tail"
                ;;
            *) die "unknown arg: $1" ;;
        esac
        shift
    done
}

# Записать аргументы в run.args (по одному на строку, mode 600).
_write_args_file() {
    local tmp="$ARGSFILE.tmp"
    : > "$tmp"
    chmod 600 "$tmp"
    {
        echo "-listen"
        echo "$ARG_LISTEN"
        echo "-connect"
        echo "$ARG_CONNECT"
        [ -n "$ARG_VLESS" ]      && echo "-vless"
        [ -n "$ARG_VLESS_BOND" ] && echo "-vless-bond"
        if [ -n "$ARG_WRAP_KEY" ]; then
            echo "-wrap"
            echo "-wrap-key"
            echo "$ARG_WRAP_KEY"
        fi
    } >> "$tmp"
    mv -f "$tmp" "$ARGSFILE"
    chmod 600 "$ARGSFILE"
}

cmd_start_systemd() {
    local bin
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed; run install first"

    _write_args_file

    if ! systemctl restart "$UNIT_NAME"; then
        die "systemctl restart failed"
    fi
    sleep 1
    if ! is_running_systemd; then
        if [ -f "$bin.bak" ]; then
            log "process not active; rolling back .bak"
            mv -f "$bin.bak" "$bin"
            systemctl restart "$UNIT_NAME" || true
            sleep 1
            is_running_systemd || die "server failed to start (after rollback); see journalctl -u $UNIT_NAME"
        else
            die "server failed to start; see journalctl -u $UNIT_NAME"
        fi
    fi
    rm -f "$bin.bak"
    local pid
    pid=$(current_pid)
    [ -n "$pid" ] && emit PID "$pid"
    echo "RESULT=ok"
    trap - EXIT
}

cmd_start_nohup() {
    local bin
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed; run install first"

    if is_running_nohup; then
        log "already running, stopping first"
        _stop_nohup
    fi

    cd "$PREFIX"
    local args=(-listen "$ARG_LISTEN" -connect "$ARG_CONNECT")
    [ -n "$ARG_VLESS" ]      && args+=(-vless)
    [ -n "$ARG_VLESS_BOND" ] && args+=(-vless-bond)
    if [ -n "$ARG_WRAP_KEY" ]; then
        umask 077
        printf '%s' "$ARG_WRAP_KEY" > "$WRAPFILE"
        chmod 600 "$WRAPFILE"
        args+=(-wrap -wrap-key "$(cat "$WRAPFILE")")
    fi

    nohup "$bin" "${args[@]}" >"$LOGFILE" 2>&1 &
    local pid=$!
    echo "$pid" > "$PIDFILE"
    sleep 1
    if ! kill -0 "$pid" 2>/dev/null; then
        if [ -f "$bin.bak" ]; then
            log "process died; rolling back .bak"
            mv -f "$bin.bak" "$bin"
        fi
        rm -f "$PIDFILE"
        die "server failed to start; see logs"
    fi
    rm -f "$bin.bak"
    emit PID "$pid"
    echo "RESULT=ok"
    trap - EXIT
}

cmd_start() {
    parse_args "$@"
    [ -n "$ARG_LISTEN" ]  || die "--listen required"
    [ -n "$ARG_CONNECT" ] || die "--connect required"
    case "$(current_runtime)" in
        systemd) cmd_start_systemd ;;
        nohup)   cmd_start_nohup ;;
        *)       die "unknown runtime" ;;
    esac
}

_stop_nohup() {
    if [ -f "$PIDFILE" ]; then
        local pid
        pid=$(cat "$PIDFILE" 2>/dev/null || echo "")
        if [ -n "$pid" ]; then
            kill "$pid" 2>/dev/null || true
            sleep 1
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$PIDFILE"
    fi
    pkill -9 -f "^$PREFIX/server-linux-" 2>/dev/null || true
    rm -f "$WRAPFILE"
}

_stop_systemd() {
    # Не disable — autostart должен сохраняться между ручными stop/start.
    systemctl stop "$UNIT_NAME" 2>/dev/null || true
}

cmd_stop() {
    case "$(current_runtime)" in
        systemd) _stop_systemd ;;
        nohup)   _stop_nohup ;;
    esac
    emit STOPPED yes
    echo "RESULT=ok"
    trap - EXIT
}

cmd_logs() {
    parse_args "$@"
    case "$(current_runtime)" in
        systemd)
            if command -v journalctl >/dev/null 2>&1; then
                journalctl -u "$UNIT_NAME" -n "$ARG_TAIL" --no-pager --output=cat 2>/dev/null \
                    | sed 's/^/LOG: /' || log "(journalctl read failed)"
            else
                log "(journalctl unavailable)"
            fi
            ;;
        nohup)
            if [ -f "$LOGFILE" ]; then
                tail -n "$ARG_TAIL" "$LOGFILE" | sed 's/^/LOG: /'
            else
                log "(log empty)"
            fi
            ;;
    esac
    echo "RESULT=ok"
    trap - EXIT
}

cmd_gen_wrap_key() {
    local bin key
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed"
    key=$("$bin" -gen-wrap-key 2>/dev/null | grep -oE '[0-9a-fA-F]{64}' | head -n1)
    [ -n "$key" ] || die "binary did not return wrap-key"
    emit WRAPKEY "$key"
    echo "RESULT=ok"
    trap - EXIT
}

main() {
    [ $# -ge 1 ] || die "no subcommand"
    local sub=$1
    shift
    case "$sub" in
        probe)        cmd_probe ;;
        install)      cmd_install "$@" ;;
        start)        cmd_start "$@" ;;
        stop)         cmd_stop ;;
        logs)         cmd_logs "$@" ;;
        gen-wrap-key) cmd_gen_wrap_key ;;
        *) die "unknown subcommand: $sub" ;;
    esac
}

main "$@"
