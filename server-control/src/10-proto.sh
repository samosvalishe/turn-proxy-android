# Вывод буферизуется в один JSON-объект, печатается в конце (ok/fail). Любая
# смерть до flush - trap печатает code=internal. stdout всегда ровно один объект.

_DATA=()       # "key":<json-value>
_LOGS=()       # заковыченные JSON-строки
_EMITTED=0     # защита от двойной печати
_STAGE="init"

stage() { _STAGE="$1"; }

# Экранирование тела JSON-строки (без обрамляющих кавычек).
esc() {
    local s=${1-}
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    s=${s//$'\t'/\\t}
    s=${s//$'\r'/\\r}
    s=${s//$'\n'/\\n}
    # Остальные управляющие (ANSI-коды journalctl и пр.) - вон: JSON их сырыми не принимает.
    s=${s//[$'\001'-$'\037'$'\177']/}
    printf '%s' "$s"
}

d_str()  { _DATA+=("\"$1\":\"$(esc "${2-}")\""); }
d_num()  { _DATA+=("\"$1\":${2:-0}"); }              # вызывающий гарантирует число
d_bool() { _DATA+=("\"$1\":$2"); }
d_raw()  { _DATA+=("\"$1\":$2"); }                   # готовый объект/массив/null

log() { _LOGS+=("\"$(esc "$*")\""); }

# Склейка элементов массива (переданы аргументами) через запятую. Пусто при 0 арг.
_join() { local IFS=','; printf '%s' "${*-}"; }

_data_json() { if [ "${#_DATA[@]}" -gt 0 ]; then _join "${_DATA[@]}"; fi; }
_logs_json() { if [ "${#_LOGS[@]}" -gt 0 ]; then _join "${_LOGS[@]}"; fi; }

ok() {
    [ "$_EMITTED" -eq 1 ] && return 0
    _EMITTED=1
    trap - EXIT
    printf '{"proto":%d,"result":"ok","data":{%s},"logs":[%s]}\n' \
        "$PROTO_VERSION" "$(_data_json)" "$(_logs_json)"
}

# fail CODE [MSG]   - MSG по умолчанию = CODE.
fail() {
    local code=$1 msg=${2:-$1}
    [ "$_EMITTED" -eq 1 ] && exit 1
    _EMITTED=1
    trap - EXIT
    printf '{"proto":%d,"result":"err","code":"%s","msg":"%s","stage":"%s","logs":[%s]}\n' \
        "$PROTO_VERSION" "$code" "$(esc "$msg")" "$_STAGE" "$(_logs_json)"
    exit 1
}

_on_exit() {
    local rc=$?
    [ "$_EMITTED" -eq 1 ] && return
    _EMITTED=1
    printf '{"proto":%d,"result":"err","code":"internal","msg":"unexpected exit %d","stage":"%s","logs":[%s]}\n' \
        "$PROTO_VERSION" "$rc" "$_STAGE" "$(_logs_json)"
}
trap _on_exit EXIT
