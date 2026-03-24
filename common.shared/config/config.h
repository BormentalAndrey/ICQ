#pragma once
#include <variant>
#include <array>
#include <string_view>
#include <optional>
#include <memory>
#include <vector>
#include <string>

namespace common::tools { class spin_lock; }

namespace config {
    enum class features {
        feedback_selected, new_avatar_rapi, ptt_recognition, snippet_in_chat,
        add_contact, remove_contact, force_avatar_update, call_quality_stat,
        show_data_visibility_link, show_security_call_link, contact_us_via_mail_default,
        searchable_recents_placeholder, auto_accepted_gdpr, phone_allowed,
        show_attach_phone_number_popup, attach_phone_number_popup_modal,
        login_by_phone_allowed, login_by_mail_default, login_by_uin_allowed,
        login_by_oauth2_allowed, forgot_password, explained_forgot_password,
        unknown_contacts, stranger_contacts, otp_login, hiding_message_text_enabled,
        hiding_message_info_enabled, changeable_name, allow_contacts_rename,
        avatar_change_allowed, beta_update, ssl_verify, profile_agent_as_domain_url,
        need_gdpr_window, need_introduce_window, has_nicknames, zstd_request_enabled,
        zstd_response_enabled, external_phone_attachment, external_url_config,
        omicron_enabled, sticker_suggests, sticker_suggests_server, smartreplies,
        spell_check, favorites_message_onpremise, info_change_allowed,
        call_link_v2_enabled, vcs_call_by_link_enabled, vcs_webinar_enabled,
        statuses_enabled, global_contact_search_allowed, show_reactions,
        open_icqcom_link, statistics, statistics_mytracker, force_update_check_allowed,
        call_room_info_enabled, store_version, otp_login_open_mail_link,
        dns_workaround, external_emoji, show_your_invites_to_group_enabled,
        group_invite_blacklist_enabled, contact_us_via_backend, update_from_backend,
        invite_by_sms, show_sms_notify_setting, silent_message_delete,
        remove_deleted_from_notifications, long_path_tooltip_enabled,
        installer_crash_send, custom_statuses_enabled, formatting_in_bubbles,
        formatting_in_input, apps_bar_enabled, status_in_apps_bar,
        scheduled_messages_enabled, threads_enabled, reminders_enabled,
        support_shared_federation_stickerpacks, url_ftp_protocols_allowed,
        draft_enabled, message_corner_menu, task_creation_in_chat_enabled,
        smartreply_suggests_feature_enabled, smartreply_suggests_text,
        smartreply_suggests_stickers, smartreply_suggests_for_quotes,
        compact_mode_by_default, expanded_gallery, restricted_files_enabled,
        antivirus_check_enabled, antivirus_check_progress_visible,
        external_user_agreement, user_agreement_enabled, delete_account_enabled,
        delete_account_via_admin, has_registry_about, organization_structure_enabled,
        tasks_enabled, calendar_enabled, tarm_mail, tarm_cloud, tarm_calls,
        calendar_self_auth, mail_enabled, mail_self_auth, cloud_self_auth,
        digital_assistant_search_positioning, leading_last_name, report_messages_enabled,
        max_size
    };

    enum class urls {
        base, base_binary, files_parser_url, profile, profile_agent, auth_mail_ru,
        oauth_url, token_url, redirect_uri, r_mail_ru, win_mail_ru, read_msg,
        stickerpack_share, cicq_com, update_win_alpha, update_win_beta,
        update_win_release, update_mac_alpha, update_mac_beta, update_mac_release,
        update_linux_alpha_32, update_linux_alpha_64, update_linux_beta_32,
        update_linux_beta_64, update_linux_release_32, update_linux_release_64,
        omicron, stat_base, feedback, legal_terms, legal_privacy_policy,
        installer_help, data_visibility, password_recovery, zstd_request_dict,
        zstd_response_dict, attach_phone, update_app_url, vcs_room, dns_cache,
        external_emoji, privacy_policy_url, terms_of_use_url, delete_account_url,
        delete_account_url_email, max_size
    };

    enum class values {
        product_path, product_path_mac, app_name_win, app_name_mac, app_name_linux,
        user_agent_app_name, client_b64, client_id, client_rapi, oauth_type,
        oauth_scope, product_name, product_name_short, product_name_full,
        main_instance_mutex_linux, main_instance_mutex_win, crossprocess_pipe,
        register_url_scheme_csv, installer_shortcut_win, installer_menu_folder_win,
        dev_id_mac, dev_id_win, dev_id_linux, // Исправление: добавлены константы, требуемые в common_defs.cpp
        max_size
    };

    enum class translations {
        installer_title_win, installer_failed_win, installer_welcome_win,
        app_title, app_install_mobile, gdpr_welcome, max_size
    };

    using value_type = std::variant<int64_t, double, std::string>;
    using urls_array = std::array<std::pair<urls, std::string>, static_cast<size_t>(urls::max_size)>;
    using features_array = std::array<std::pair<features, bool>, static_cast<size_t>(features::max_size)>;
    using values_array = std::array<std::pair<values, value_type>, static_cast<size_t>(values::max_size)>;
    using translations_array = std::array<std::pair<translations, std::string>, static_cast<size_t>(translations::max_size)>;

    // Интерфейс для вызова конфигурации, используемый в common_defs.cpp
    struct config_interface {
        virtual std::string_view string(values key) const = 0;
    };
    config_interface& get();
}
