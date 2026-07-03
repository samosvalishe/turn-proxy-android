main() {
    [ $# -ge 1 ] || fail bad_arg "no subcommand"
    local sub=$1
    shift
    case "$sub" in
        probe)         cmd_probe ;;
        install)       parse_args "$@"; cmd_install ;;
        wg-setup)      parse_args "$@"; cmd_wg_setup ;;
        start)         parse_args "$@"; cmd_start ;;
        stop)          cmd_stop ;;
        logs)          parse_args "$@"; cmd_logs ;;
        share-info)    cmd_share_info ;;
        share-list)    cmd_share_list ;;
        peer-add)      parse_args "$@"; cmd_peer_add ;;
        peer-conf)     parse_args "$@"; cmd_peer_conf ;;
        peer-remove)   parse_args "$@"; cmd_peer_remove ;;
        client-add)    parse_args "$@"; cmd_client_add ;;
        client-remove) parse_args "$@"; cmd_client_remove ;;
        uninstall)     parse_args "$@"; cmd_uninstall ;;
        *)             fail bad_arg "unknown subcommand: $sub" ;;
    esac
}

main "$@"
