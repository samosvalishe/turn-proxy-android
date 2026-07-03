# Кросс-дистрибутивная обёртка (apt/dnf/yum/apk/pacman/zypper). Best-effort: 0/1.

pkg_mgr() {
    local m
    for m in apt-get dnf yum apk pacman zypper; do
        if command -v "$m" >/dev/null 2>&1; then echo "$m"; return 0; fi
    done
    return 1
}

# apt держит dpkg-lock при unattended-upgrades - ждём, не падаем молча.
_apt_wait_lock() {
    command -v fuser >/dev/null 2>&1 || return 0
    local i=0
    while fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1 \
       || fuser /var/lib/dpkg/lock >/dev/null 2>&1; do
        [ "$i" -ge 60 ] && return 0
        log "waiting for dpkg lock ($((i*2))s)"
        sleep 2
        i=$((i + 1))
    done
}

pkg_install() {  # PKG [PKG...]
    local mgr
    mgr=$(pkg_mgr) || return 1
    case "$mgr" in
        apt-get)
            _apt_wait_lock
            DEBIAN_FRONTEND=noninteractive apt-get update -qq >/dev/null 2>&1 || true
            DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@" >/dev/null 2>&1 ;;
        dnf)    dnf install -y -q "$@" >/dev/null 2>&1 ;;
        yum)    yum install -y -q "$@" >/dev/null 2>&1 ;;
        apk)    apk add --no-cache "$@" >/dev/null 2>&1 ;;
        pacman) pacman -Sy --noconfirm "$@" >/dev/null 2>&1 ;;
        zypper) zypper --non-interactive install "$@" >/dev/null 2>&1 ;;
        *)      return 1 ;;
    esac
}

pkg_remove() {  # PKG [PKG...]
    local mgr
    mgr=$(pkg_mgr) || return 1
    case "$mgr" in
        apt-get)
            _apt_wait_lock
            DEBIAN_FRONTEND=noninteractive apt-get remove -y -qq "$@" >/dev/null 2>&1 ;;
        dnf)    dnf remove -y -q "$@" >/dev/null 2>&1 ;;
        yum)    yum remove -y -q "$@" >/dev/null 2>&1 ;;
        apk)    apk del "$@" >/dev/null 2>&1 ;;
        pacman) pacman -Rns --noconfirm "$@" >/dev/null 2>&1 ;;
        zypper) zypper --non-interactive remove "$@" >/dev/null 2>&1 ;;
        *)      return 1 ;;
    esac
}
