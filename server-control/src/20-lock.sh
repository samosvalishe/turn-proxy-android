# Best-effort flock: параллельные SSH-сессии не рвут bin/conf/args. FD 8 -
# control-lock, FD 9 - peers-lock. Нет flock - идём дальше (одиночный юзер).

with_lock() {
    command -v flock >/dev/null 2>&1 || return 0
    [ -d "$PREFIX" ] || mkdir -p "$PREFIX" 2>/dev/null || return 0
    [ -w "$PREFIX" ] || return 0
    exec 8>"$LOCKFILE" 2>/dev/null || return 0
    flock -w 300 8 2>/dev/null || true
}

with_peers_lock() {
    command -v flock >/dev/null 2>&1 || return 0
    [ -d "$PREFIX" ] || mkdir -p "$PREFIX" 2>/dev/null || return 0
    exec 9>"$PEERS_LOCK" 2>/dev/null || return 0
    flock -w 60 9 2>/dev/null || true
}
