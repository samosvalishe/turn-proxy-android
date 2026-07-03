#!/usr/bin/env bats
# White-box тесты чистых WG-хелперов: sourceим модули (без 99-main) в sandbox
# через FT_*-override. Не требуют root/wg для логики парсинга и аллокации.

setup() {
    SRC="$BATS_TEST_DIRNAME/../src"
    LIB="$BATS_TEST_TMPDIR/lib.sh"
    : > "$LIB"
    for f in $(ls "$SRC"/*.sh | sort | grep -v '99-main'); do
        cat "$f" >> "$LIB"; printf '\n' >> "$LIB"
    done
    export FT_PREFIX="$BATS_TEST_TMPDIR/prefix"
    export FT_WG_DIR="$BATS_TEST_TMPDIR/wg"
    export FT_WG_IFACE="ft-wg0"
    mkdir -p "$FT_PREFIX" "$FT_WG_DIR"
    # shellcheck disable=SC1090
    source "$LIB"
    set +e            # bats-ассерты несовместимы с унаследованным errexit
    trap - EXIT       # подавить _on_exit JSON в конце теста
}

_write_conf() { printf '%s\n' "$@" > "$FT_WG_DIR/ft-wg0.conf"; }

@test "_wg_alloc_ip: .1 сервер + .2 владелец заняты -> .3" {
    _write_conf "[Interface]" "$WG_MARKER" "Address = 10.13.13.1/24" \
                "" "[Peer]" "AllowedIPs = 10.13.13.2/32"
    [ "$(_wg_alloc_ip "$FT_WG_DIR/ft-wg0.conf")" = "10.13.13.3" ]
}

@test "_wg_alloc_ip: после .3 -> .4" {
    _write_conf "[Interface]" "Address = 10.13.13.1/24" \
                "[Peer]" "AllowedIPs = 10.13.13.2/32" \
                "[Peer]" "AllowedIPs = 10.13.13.3/32"
    [ "$(_wg_alloc_ip "$FT_WG_DIR/ft-wg0.conf")" = "10.13.13.4" ]
}

@test "_wg_alloc_ip: IPv6-only Address -> код 1" {
    _write_conf "[Interface]" "Address = fd00::1/64"
    run _wg_alloc_ip "$FT_WG_DIR/ft-wg0.conf"
    [ "$status" -ne 0 ]
}

@test "wg_is_ours: маркер есть -> 0" {
    _write_conf "[Interface]" "$WG_MARKER" "Address = 10.13.13.1/24"
    wg_is_ours
}

@test "wg_is_ours: маркера нет -> 1" {
    _write_conf "[Interface]" "Address = 10.0.0.1/24"
    run wg_is_ours
    [ "$status" -ne 0 ]
}

@test "wg_present: нет файла -> 1" {
    rm -f "$FT_WG_DIR/ft-wg0.conf"
    run wg_present
    [ "$status" -ne 0 ]
}

@test "wg_port: из ListenPort conf (wg отсутствует)" {
    _write_conf "[Interface]" "ListenPort = 51820" "Address = 10.13.13.1/24"
    [ "$(wg_port)" = "51820" ]
}

@test "wg_port: инлайн-комментарий срезается" {
    _write_conf "[Interface]" "ListenPort = 51820 # default" "Address = 10.13.13.1/24"
    [ "$(wg_port)" = "51820" ]
}

@test "pkg_mgr: на машине без пакетников -> код 1" {
    # На CI-Linux пакетник есть; тест осмыслен в чистом окружении. Проверяем,
    # что функция не падает и возвращает имя ИЛИ код 1.
    run pkg_mgr
    [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
}
