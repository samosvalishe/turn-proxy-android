# clients.json правит только бинарь ядра (атомарно, hot-reload); здесь - тонкие
# обёртки над его сабкомандой `clients`.

_bin_path() {
    local arch
    arch=$(detect_arch) || arch=""
    # if, не &&-хвост: пустая арка вернула бы 1 - set -e убьёт $(_bin_path) у вызывающего.
    if [ -n "$arch" ]; then echo "$PREFIX/$arch"; fi
}

clients_add() {  # ID COMMENT
    local bin
    bin=$(_bin_path)
    [ -n "$bin" ] && [ -x "$bin" ] || fail not_installed "server binary not installed; run install first"
    CLIENTS_FILE="$CLIENTSFILE" "$bin" clients add "$1" "$2" >/dev/null \
        || fail clients_cmd_failed "clients add failed"
}

# Best-effort: отзыв не должен валить операцию (идемпотентный откат).
clients_remove_soft() {  # ID
    local bin
    bin=$(_bin_path)
    [ -n "$bin" ] && [ -x "$bin" ] || return 0
    CLIENTS_FILE="$CLIENTSFILE" "$bin" clients remove "$1" >/dev/null 2>&1 || true
}

# JSON-массив гостей из allowlist, НЕ привязанных к WG-пирам (их показывает блок
# peers). Фильтруем владельца (owner.cid) и cid'ы, у которых есть .cid-маппинг.
# Формат строки ядра: " - <id> (Comment: <text>)". Имя наружу - base64.
# Код 1 при провале `clients list` - fail тут нельзя: зовётся из $(),
# err-envelope ушёл бы в подстановку. fail'ит вызывающий (cmd_share_list).
clients_json() {
    local bin
    bin=$(_bin_path)
    if [ -z "$bin" ] || [ ! -x "$bin" ]; then printf '[]'; return 0; fi

    local owner="" mapped="" f out="" first=1 line id comment el
    [ -f "$OWNERCIDFILE" ] && owner=$(tr -d ' \r\n' < "$OWNERCIDFILE")
    if [ -d "$SHARE_DIR" ]; then
        for f in "$SHARE_DIR"/*.cid; do
            [ -f "$f" ] || continue
            mapped="$mapped $(tr -d ' \r\n' < "$f")"
        done
    fi

    local raw
    raw=$(CLIENTS_FILE="$CLIENTSFILE" "$bin" clients list 2>/dev/null) || return 1

    while IFS= read -r line; do
        case "$line" in " - "*) ;; *) continue ;; esac
        id=${line#" - "}; id=${id%% *}
        [ -n "$id" ] || continue
        [ "$id" = "$owner" ] && continue
        case " $mapped " in *" $id "*) continue ;; esac
        comment=$(printf '%s' "$line" | sed -nE 's/^ - [^ ]+ \(Comment: (.*)\)$/\1/p')
        el="{\"id\":\"$(esc "$id")\""
        if [ -n "$comment" ]; then
            el="$el,\"name_b64\":\"$(printf '%s' "$comment" | base64 | tr -d '\n')\""
        fi
        el="$el}"
        if [ "$first" = 1 ]; then first=0; else out="$out,"; fi
        out="$out$el"
    done <<< "$raw"
    printf '[%s]' "$out"
}
