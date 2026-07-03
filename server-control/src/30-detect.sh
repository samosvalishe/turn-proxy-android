# Только чтение и репорт; действий над чужим WG/процессами здесь нет.

_mips_is_le() {
    # 1=LE, 0=BE. od на little-endian '\1\0' читает как 0x0001.
    local hex
    hex=$(printf '\1\0' | od -An -tx2 -N2 2>/dev/null | tr -d ' \n')
    [ "$hex" = "0001" ]
}

# echo asset-имя или пусто (+ код 1) при неизвестной арке. die тут НЕЛЬЗЯ -
# зовётся из $(...), JSON ушёл бы в переменную; решает вызывающий.
detect_arch() {
    local m
    m=$(uname -m 2>/dev/null || echo "")
    case "$m" in
        x86_64|amd64) echo "server-linux-amd64" ;;
        aarch64|arm64) echo "server-linux-arm64" ;;
        armv7l|armv6l|armv5*|arm) echo "server-linux-arm" ;;
        i386|i486|i586|i686) echo "server-linux-386" ;;
        riscv64) echo "server-linux-riscv64" ;;
        mips64|mips64le)
            if _mips_is_le; then echo "server-linux-mips64le"; else echo ""; return 1; fi ;;
        mips|mipsel|mipsle)
            if _mips_is_le; then echo "server-linux-mipsle"; else echo "server-linux-mips"; fi ;;
        *) echo ""; return 1 ;;
    esac
}

# kvm|qemu|openvz|lxc|docker|none|...
detect_virt() {
    local v
    if command -v systemd-detect-virt >/dev/null 2>&1; then
        v=$(systemd-detect-virt 2>/dev/null || true)
        if [ -n "$v" ]; then echo "$v"; return 0; fi
    fi
    if [ -f /.dockerenv ]; then echo docker; return 0; fi
    if grep -qa 'container=lxc' /proc/1/environ 2>/dev/null; then echo lxc; return 0; fi
    echo none
}

# Доступен ли kernel-WG (модуль загружен или грузится). modprobe требует root.
wg_kernel_ok() {
    [ -d /sys/module/wireguard ] && return 0
    modprobe wireguard >/dev/null 2>&1
}

has_systemd() {
    command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]
}

# Детекторы конфликтующих WG-менеджеров (только репорт).
conflict_warp() {
    if command -v warp-cli >/dev/null 2>&1; then return 0; fi
    if ip link show 2>/dev/null | grep -qi 'CloudflareWARP'; then return 0; fi
    if ls "$WG_DIR"/wgcf*.conf >/dev/null 2>&1; then return 0; fi
    return 1
}

conflict_x3ui() {
    if [ -d /etc/x-ui ] || [ -d /usr/local/x-ui ]; then return 0; fi
    if command -v x-ui >/dev/null 2>&1; then return 0; fi
    if has_systemd && systemctl list-unit-files 2>/dev/null | grep -qi '^x-ui'; then return 0; fi
    return 1
}

conflict_wgeasy() {
    if command -v docker >/dev/null 2>&1; then
        if docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep -qi 'wg-easy'; then return 0; fi
    fi
    if [ -n "${WG_HOST:-}" ]; then return 0; fi
    return 1
}

conflict_tailscale() {
    if command -v tailscale >/dev/null 2>&1; then return 0; fi
    if ip link show 2>/dev/null | grep -qi 'tailscale'; then return 0; fi
    return 1
}

# JSON-массив (без скобок) чужих wg-интерфейсов, кроме нашего ft-wg0.
other_wg_ifaces_csv() {
    command -v wg >/dev/null 2>&1 || return 0
    local i out="" first=1
    for i in $(wg show interfaces 2>/dev/null || true); do
        [ "$i" = "$WG_IFACE" ] && continue
        if [ "$first" -eq 1 ]; then first=0; else out="$out,"; fi
        out="$out\"$(esc "$i")\""
    done
    printf '%s' "$out"
}

# port_owner tcp|udp PORT -> "free" | "<comm>" | "unknown". Только определение,
# НИКАКИХ kill. Используется cmd_start, чтобы не убивать чужой процесс на порту.
port_owner() {
    local proto=$1 port=$2 letter line name
    case "$proto" in tcp) letter=t ;; udp) letter=u ;; *) echo unknown; return 0 ;; esac
    if command -v ss >/dev/null 2>&1; then
        line=$(ss -H -ln"$letter"p 2>/dev/null | awk -v p=":${port}\$" '$4 ~ p {print; exit}' || true)
        if [ -z "$line" ]; then echo free; return 0; fi
        name=$(printf '%s' "$line" | sed -nE 's/.*users:\(\("([^"]+)".*/\1/p')
        echo "${name:-unknown}"
        return 0
    fi
    echo unknown
}

# port_pid tcp|udp PORT -> pid слушающего процесса (или пусто).
port_pid() {
    local proto=$1 port=$2 letter line
    case "$proto" in tcp) letter=t ;; udp) letter=u ;; *) return 0 ;; esac
    command -v ss >/dev/null 2>&1 || return 0
    line=$(ss -H -ln"$letter"p 2>/dev/null | awk -v p=":${port}\$" '$4 ~ p {print; exit}' || true)
    printf '%s' "$line" | sed -nE 's/.*pid=([0-9]+).*/\1/p'
}

# 0, если exe процесса лежит в $PREFIX (наш бинарь; " (deleted)"-суффикс не мешает).
pid_is_ours() {  # PID
    local exe
    exe=$(readlink "/proc/$1/exe" 2>/dev/null || true)
    case "$exe" in "$PREFIX"/*) return 0 ;; esac
    return 1
}
