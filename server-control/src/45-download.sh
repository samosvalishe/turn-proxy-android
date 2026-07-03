_dl() {  # URL OUT
    local url=$1 out=$2
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --connect-timeout 15 --max-time 300 -o "$out" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --timeout=300 -O "$out" "$url"
    else
        fail download_failed "neither curl nor wget present"
    fi
}

# GitHub latest/download/<asset> отвечает 302 с Location, где тег vX.Y.Z. Берём из URL.
_resolve_version() {  # URL -> tag (или пусто)
    local url=$1 loc=""
    if command -v curl >/dev/null 2>&1; then
        loc=$(curl -sI "$url" 2>/dev/null | awk -F': ' 'tolower($1)=="location"{print $2}' | tr -d '\r' | head -n1)
    elif command -v wget >/dev/null 2>&1; then
        loc=$(wget --spider --server-response "$url" 2>&1 | awk '/[Ll]ocation:/{print $2}' | tr -d '\r' | head -n1)
    fi
    printf '%s' "$loc" | sed -nE 's#.*/releases/download/([^/]+)/.*#\1#p'
}

# Проверка скачанного: размер + ELF-magic (+ опц. sha256). fail с кодом при провале.
_verify_download() {  # FILE [EXPECTED_SHA]
    local f=$1 want=${2:-} size magic got
    size=$(wc -c < "$f" 2>/dev/null || echo 0)
    if [ "$size" -lt 100000 ]; then
        rm -f "$f"; fail too_small "downloaded file too small ($size bytes)"
    fi
    # od может отсутствовать (busybox) - тогда ELF-чек пропускаем, не валим.
    magic=$(od -An -tx1 -N4 "$f" 2>/dev/null | tr -d ' \n')
    if [ -n "$magic" ] && [ "$magic" != "7f454c46" ]; then
        rm -f "$f"; fail not_elf "downloaded file is not an ELF binary"
    fi
    if [ -n "$want" ] && command -v sha256sum >/dev/null 2>&1; then
        got=$(sha256sum "$f" | awk '{print $1}')
        if [ -n "$got" ] && [ "$got" != "$want" ]; then
            rm -f "$f"; fail sha_mismatch "sha256 mismatch (got $got)"
        fi
    fi
}
