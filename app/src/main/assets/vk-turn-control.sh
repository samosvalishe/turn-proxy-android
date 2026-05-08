#!/bin/bash
# vk-turn-proxy server control script.
# Streamed via SSH stdin from Android (bash -s -- <subcmd> <args>).
# Output protocol: KEY=VALUE lines, "LOG: ..." for free text, final RESULT=ok|err.

set -eu

PREFIX="/opt/vk-turn"
PIDFILE="$PREFIX/proxy.pid"
WRAPFILE="$PREFIX/wrap.key"
LOGFILE="$PREFIX/server.log"
VERFILE="$PREFIX/version"
BASE_URL="https://github.com/Moroka8/vk-turn-proxy/releases/latest/download"

log()  { echo "LOG: $*"; }
emit() { echo "$1=$2"; }
die()  { echo "ERR=$*"; echo "RESULT=err"; exit 1; }

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

is_running() {
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
    if is_running; then
        emit RUNNING yes
        local cmdline
        cmdline=$(pgrep -af "^$PREFIX/server-linux-" | head -n1 || true)
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

    if [ -x "$bin" ] && [ "$ver" = "$curver" ]; then
        emit STAGE cached
        emit BIN "$name"
        emit VERSION "$ver"
        echo "RESULT=ok"
        trap - EXIT
        return 0
    fi

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

    emit STAGE downloaded
    emit BIN "$name"
    emit VERSION "$ver"
    if is_running; then
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

cmd_start() {
    parse_args "$@"
    [ -n "$ARG_LISTEN" ]  || die "--listen required"
    [ -n "$ARG_CONNECT" ] || die "--connect required"
    local bin
    bin=$(binpath)
    [ -x "$bin" ] || die "server binary not installed; run install first"

    if is_running; then
        log "already running, stopping first"
        cmd_stop_inner
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
    # Старт удался — резервная копия больше не нужна, чтобы не копились тушки
    # после нескольких циклов update.
    rm -f "$bin.bak"
    emit PID "$pid"
    echo "RESULT=ok"
    trap - EXIT
}

cmd_stop_inner() {
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

cmd_stop() {
    cmd_stop_inner
    emit STOPPED yes
    echo "RESULT=ok"
    trap - EXIT
}

cmd_logs() {
    parse_args "$@"
    if [ -f "$LOGFILE" ]; then
        tail -n "$ARG_TAIL" "$LOGFILE" | sed 's/^/LOG: /'
    else
        log "(log empty)"
    fi
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
