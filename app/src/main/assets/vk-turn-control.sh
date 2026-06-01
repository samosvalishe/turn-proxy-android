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
OBFFILE="$PREFIX/obf.key"
LOGFILE="$PREFIX/server.log"
VERFILE="$PREFIX/version"
RUNTIMEFILE="$PREFIX/runtime"
ARGSFILE="$PREFIX/run.args"
ENVFILE="$PREFIX/run.env"
LAUNCHER="$PREFIX/launch.sh"
UNIT_PATH="/etc/systemd/system/vk-turn-proxy.service"
UNIT_NAME="vk-turn-proxy.service"
BASE_URL="https://github.com/samosvalishe/free-turn-proxy/releases/latest/download"

log()  { echo "LOG: $*"; }
emit() { echo "$1=$2"; }
die()  { echo "ERR=$*"; echo "RESULT=err"; trap - EXIT; exit 1; }

trap 'rc=$?; if [ $rc -ne 0 ]; then echo "ERR=script exit $rc"; echo "RESULT=err"; fi' EXIT

_mips_is_le() {
    # 1=LE, 0=BE. od на little-endian машине '\1\0' читает как 0x0001.
    local hex
    hex=$(printf '\1\0' | od -An -tx2 -N2 2>/dev/null | tr -d ' \n')
    [ "$hex" = "0001" ]
}

detect_arch() {
    local m
    m=$(uname -m)
    case "$m" in
        x86_64|amd64) echo "server-linux-amd64" ;;
        aarch64|arm64) echo "server-linux-arm64" ;;
        armv7l|armv6l|armv5*|arm) echo "server-linux-arm" ;;
        i386|i486|i586|i686) echo "server-linux-386" ;;
        riscv64) echo "server-linux-riscv64" ;;
        mips64|mips64le)
            if _mips_is_le; then echo "server-linux-mips64le"
            else die "unsupported arch: mips64 BE (no asset)"
            fi
            ;;
        mips|mipsel|mipsle)
            if _mips_is_le; then echo "server-linux-mipsle"
            else echo "server-linux-mips"
            fi
            ;;
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
        # -mode tcp → MODE=tcp, иначе udp-релей (дефолт ядра).
        if echo "$cmdline" | grep -q -- "-mode tcp"; then emit MODE tcp; else emit MODE udp; fi
        # OBF=<профиль из cmdline> (rtpopus|none).
        local obf
        obf=$(echo "$cmdline" | sed -nE 's/.*-obf-profile[= ]+([a-z]+).*/\1/p')
        emit OBF "${obf:-none}"
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
mips_is_le() {
    local hex
    hex=$(printf '\1\0' | od -An -tx2 -N2 2>/dev/null | tr -d ' \n')
    [ "$hex" = "0001" ]
}
m=$(uname -m)
case "$m" in
    x86_64|amd64) arch=server-linux-amd64 ;;
    aarch64|arm64) arch=server-linux-arm64 ;;
    armv7l|armv6l|armv5*|arm) arch=server-linux-arm ;;
    i386|i486|i586|i686) arch=server-linux-386 ;;
    riscv64) arch=server-linux-riscv64 ;;
    mips64|mips64le)
        if mips_is_le; then arch=server-linux-mips64le
        else echo "unsupported arch: mips64 BE" >&2; exit 1
        fi
        ;;
    mips|mipsel|mipsle)
        if mips_is_le; then arch=server-linux-mipsle
        else arch=server-linux-mips
        fi
        ;;
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
EnvironmentFile=-$ENVFILE
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
ARG_MODE=""
ARG_OBF_PROFILE="none"
ARG_OBF_KEY=""
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
            --mode=*)
                ARG_MODE="${1#*=}"
                [[ "$ARG_MODE" =~ ^(udp|tcp)$ ]] || die "bad --mode (need udp|tcp)"
                ;;
            --obf-profile=*)
                ARG_OBF_PROFILE="${1#*=}"
                [[ "$ARG_OBF_PROFILE" =~ ^(none|rtpopus)$ ]] || die "bad --obf-profile (need none|rtpopus)"
                ;;
            --obf-key=*)
                ARG_OBF_KEY="${1#*=}"
                [[ "$ARG_OBF_KEY" =~ ^[0-9a-fA-F]{64}$ ]] || die "bad --obf-key (need 64 hex)"
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
# Bond на сервере не задаётся — ядро детектит его по magic-префиксу стрима.
_write_args_file() {
    local tmp="$ARGSFILE.tmp"
    : > "$tmp"
    chmod 600 "$tmp"
    {
        echo "-listen"
        echo "$ARG_LISTEN"
        echo "-connect"
        echo "$ARG_CONNECT"
        if [ "$ARG_MODE" = "tcp" ]; then
            echo "-mode"
            echo "tcp"
        fi
        if [ "$ARG_OBF_PROFILE" != "none" ] && [ -n "$ARG_OBF_KEY" ]; then
            echo "-obf-profile"
            echo "$ARG_OBF_PROFILE"
            echo "-obf-key"
            echo "$ARG_OBF_KEY"
        fi
    } >> "$tmp"
    mv -f "$tmp" "$ARGSFILE"
    chmod 600 "$ARGSFILE"
}

# Записать env-переменные в run.env (KEY=VALUE, mode 600). Используется
# и systemd (EnvironmentFile), и nohup (source перед exec). Сейчас env пуст
# (KCP FEC выпилен из ядра), файл оставлен для совместимости unit-а.
_write_env_file() {
    local tmp="$ENVFILE.tmp"
    : > "$tmp"
    chmod 600 "$tmp"
    mv -f "$tmp" "$ENVFILE"
    chmod 600 "$ENVFILE"
}

cmd_start_systemd() {
    local bin
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed; run install first"

    _write_args_file
    _write_env_file

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
    _write_env_file
    if [ -f "$ENVFILE" ]; then
        set -a
        # shellcheck disable=SC1090
        . "$ENVFILE"
        set +a
    fi
    local args=(-listen "$ARG_LISTEN" -connect "$ARG_CONNECT")
    [ "$ARG_MODE" = "tcp" ] && args+=(-mode tcp)
    if [ "$ARG_OBF_PROFILE" != "none" ] && [ -n "$ARG_OBF_KEY" ]; then
        umask 077
        printf '%s' "$ARG_OBF_KEY" > "$OBFFILE"
        chmod 600 "$OBFFILE"
        args+=(-obf-profile "$ARG_OBF_PROFILE" -obf-key "$(cat "$OBFFILE")")
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
    rm -f "$OBFFILE"
    rm -f "$ENVFILE"
}

_stop_systemd() {
    # Не disable — autostart должен сохраняться между ручными stop/start.
    systemctl stop "$UNIT_NAME" 2>/dev/null || true
    rm -f "$ENVFILE"
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

cmd_gen_obf_key() {
    local bin key
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed"
    key=$("$bin" -gen-obf-key 2>/dev/null | grep -oE '[0-9a-fA-F]{64}' | head -n1)
    [ -n "$key" ] || die "binary did not return obf-key"
    emit OBFKEY "$key"
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
        gen-obf-key)  cmd_gen_obf_key ;;
        *) die "unknown subcommand: $sub" ;;
    esac
}

main "$@"
