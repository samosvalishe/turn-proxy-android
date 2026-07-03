# k=v state ($PREFIX/state) для того, что не вывести из conf/окружения. Ключи:
# runtime (systemd|nohup), wg_pkg_owned (1 - wireguard-tools ставили мы).

state_get() {  # KEY -> значение (или пусто)
    [ -f "$STATE_FILE" ] || return 0
    sed -n "s/^$1=//p" "$STATE_FILE" 2>/dev/null | head -n1
}

state_set() {  # KEY VALUE - атомарная запись
    local k=$1 v=$2 tmp
    [ -d "$PREFIX" ] || mkdir -p "$PREFIX"
    tmp=$(mktemp "$STATE_FILE.XXXXXX") || return 1
    if [ -f "$STATE_FILE" ]; then
        grep -v "^$k=" "$STATE_FILE" > "$tmp" 2>/dev/null || true
    fi
    printf '%s=%s\n' "$k" "$v" >> "$tmp"
    chmod 600 "$tmp"
    mv -f "$tmp" "$STATE_FILE"
}
