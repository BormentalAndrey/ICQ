#include "stdafx.h"
#include "config.h"

#ifdef SUPPORT_EXTERNAL_CONFIG
#include "../core/tools/binary_stream.h"
#include "../core/tools/strings.h"
#include "../core/tools/system.h"
#include "../core/utils.h"
#include <boost/filesystem.hpp>
#endif // SUPPORT_EXTERNAL_CONFIG

#include "../environment.h"
#include "../json_unserialize_helpers.h"
#include "config_data.h"
#include "../spin_lock.h"

namespace config {

static auto is_less_by_first = [](const auto &x1, const auto &x2) {
  static_assert(std::is_same_v<decltype(x1), decltype(x2)>);
  auto key1 = x1.first;
  auto key2 = x2.first;
  return static_cast<std::underlying_type_t<decltype(key1)>>(key1) <
         static_cast<std::underlying_type_t<decltype(key2)>>(key2);
};

static auto get_string(const rapidjson::Value &json_value, std::string_view name) {
  return common::json::get_value<std::string>(json_value, name).value_or(std::string());
}

static auto get_bool(const rapidjson::Value &json_value, std::string_view name) {
  return common::json::get_value<bool>(json_value, name).value_or(false);
}

static auto get_int64(const rapidjson::Value &json_value, std::string_view name) {
  return common::json::get_value<int64_t>(json_value, name).value_or(0);
}

static std::optional<urls_array> parse_urls(const rapidjson::Value &_node) {
  if (const auto it = _node.FindMember("urls"); it != _node.MemberEnd()) {
    urls_array res = {{
         { urls::base, get_string(it->value, "base") },
         { urls::base_binary, get_string(it->value, "base_binary") },
         { urls::files_parser_url, get_string(it->value, "files_parser_url") },
         { urls::profile, get_string(it->value, "profile") },
         { urls::profile_agent, get_string(it->value, "profile_agent") },
         { urls::auth_mail_ru, get_string(it->value, "auth_mail_ru") },
         { urls::oauth_url, get_string(it->value, "oauth_url") },
         { urls::token_url, get_string(it->value, "token_url") },
         { urls::redirect_uri, get_string(it->value, "redirect_uri") },
         { urls::r_mail_ru, get_string(it->value, "r_mail_ru") },
         { urls::win_mail_ru, get_string(it->value, "win_mail_ru") },
         { urls::read_msg, get_string(it->value, "read_msg") },
         { urls::stickerpack_share, get_string(it->value, "stickerpack_share") },
         { urls::cicq_com, get_string(it->value, "c.icq.com") },
         { urls::update_win_alpha, get_string(it->value, "update_win_alpha") },
         { urls::update_win_beta, get_string(it->value, "update_win_beta") },
         { urls::update_win_release, get_string(it->value, "update_win_release") },
         { urls::update_mac_alpha, get_string(it->value, "update_mac_alpha") },
         { urls::update_mac_beta, get_string(it->value, "update_mac_beta") },
         { urls::update_mac_release, get_string(it->value, "update_mac_release") },
         { urls::update_linux_alpha_32, get_string(it->value, "update_linux_alpha_32") },
         { urls::update_linux_alpha_64, get_string(it->value, "update_linux_alpha_64") },
         { urls::update_linux_beta_32, get_string(it->value, "update_linux_beta_32") },
         { urls::update_linux_beta_64, get_string(it->value, "update_linux_beta_64") },
         { urls::update_linux_release_32, get_string(it->value, "update_linux_release_32") },
         { urls::update_linux_release_64, get_string(it->value, "update_linux_release_64") },
         { urls::omicron, get_string(it->value, "omicron") },
         { urls::stat_base, get_string(it->value, "stat_base") },
         { urls::feedback, get_string(it->value, "feedback") },
         { urls::legal_terms, get_string(it->value, "legal_terms") },
         { urls::legal_privacy_policy, get_string(it->value, "legal_privacy_policy") },
         { urls::installer_help, get_string(it->value, "installer_help") },
         { urls::data_visibility, get_string(it->value, "data_visibility") },
         { urls::password_recovery, get_string(it->value, "password_recovery") },
         { urls::zstd_request_dict, get_string(it->value, "zstd_request_dict") },
         { urls::zstd_response_dict, get_string(it->value, "zstd_response_dict") },
         { urls::attach_phone, get_string(it->value, "attach_phone_url") },
         { urls::update_app_url, get_string(it->value, "update_app_url") },
         { urls::vcs_room, get_string(it->value, "vcs_room") },
         { urls::dns_cache, get_string(it->value, "dns_cache") },
         { urls::external_emoji, get_string(it->value, "external_emoji") },
         { urls::privacy_policy_url, get_string(it->value, "privacy_policy_url") },
         { urls::terms_of_use_url, get_string(it->value, "terms_of_use_url") },
         { urls::delete_account_url, get_string(it->value, "delete_account_url") },
         { urls::delete_account_url_email, get_string(it->value, "delete_account_url_email") }
    }};

    if (std::is_sorted(std::cbegin(res), std::cend(res), is_less_by_first))
      return std::make_optional(std::move(res));
    assert(false);
  }
  return {};
}

static std::optional<translations_array> parse_translations(const rapidjson::Value &_node) {
  if (const auto it = _node.FindMember("translations"); it != _node.MemberEnd()) {
    translations_array res = {{
         { translations::installer_title_win, get_string(it->value, "installer_title_win") },
         { translations::installer_failed_win, get_string(it->value, "installer_failed_win") },
         { translations::installer_welcome_win, get_string(it->value, "installer_welcome_win") },
         { translations::app_title, get_string(it->value, "app_title") },
         { translations::app_install_mobile, get_string(it->value, "app_install_mobile") },
         { translations::gdpr_welcome, get_string(it->value, "gdpr_welcome") }
    }};

    if (std::is_sorted(std::cbegin(res), std::cend(res), is_less_by_first))
      return std::make_optional(std::move(res));
    assert(false);
  }
  return {};
}

static std::optional<features_array> parse_features(const rapidjson::Value &_node) {
  if (const auto it = _node.FindMember("features"); it != _node.MemberEnd()) {
    features_array res = {{
         { features::feedback_selected, get_bool(it->value, "feedback_selected") },
         { features::new_avatar_rapi, get_bool(it->value, "new_avatar_rapi") },
         { features::ptt_recognition, get_bool(it->value, "ptt_recognition") },
         { features::snippet_in_chat, get_bool(it->value, "snippet_in_chat") },
         { features::add_contact, get_bool(it->value, "add_contact") },
         { features::remove_contact, get_bool(it->value, "remove_contact") },
         { features::force_avatar_update, get_bool(it->value, "force_avatar_update") },
         { features::call_quality_stat, get_bool(it->value, "call_quality_stat") },
         { features::show_data_visibility_link, get_bool(it->value, "show_data_visibility_link") },
         { features::show_security_call_link, get_bool(it->value, "show_security_call_link") },
         { features::contact_us_via_mail_default, get_bool(it->value, "contact_us_via_mail_default") },
         { features::searchable_recents_placeholder, get_bool(it->value, "searchable_recents_placeholder") },
         { features::auto_accepted_gdpr, get_bool(it->value, "auto_accepted_gdpr") },
         { features::phone_allowed, get_bool(it->value, "phone_allowed") },
         { features::show_attach_phone_number_popup, get_bool(it->value, "show_attach_phone_number_popup") },
         { features::attach_phone_number_popup_modal, get_bool(it->value, "attach_phone_number_popup_modal") },
         { features::login_by_phone_allowed, get_bool(it->value, "login_by_phone_allowed") },
         { features::login_by_mail_default, get_bool(it->value, "login_by_mail_default") },
         { features::login_by_uin_allowed, get_bool(it->value, "login_by_uin_allowed") },
         { features::login_by_oauth2_allowed, get_bool(it->value, "login_by_oauth2_allowed") },
         { features::forgot_password, get_bool(it->value, "forgot_password") },
         { features::explained_forgot_password, get_bool(it->value, "explained_forgot_password") },
         { features::unknown_contacts, get_bool(it->value, "unknown_contacts") },
         { features::stranger_contacts, get_bool(it->value, "stranger_contacts") },
         { features::otp_login, get_bool(it->value, "otp_login") },
         { features::hiding_message_text_enabled, get_bool(it->value, "hiding_message_text_enabled") },
         { features::hiding_message_info_enabled, get_bool(it->value, "hiding_message_info_enabled") },
         { features::changeable_name, get_bool(it->value, "changeable_name") },
         { features::allow_contacts_rename, get_bool(it->value, "allow_contacts_rename") },
         { features::avatar_change_allowed, get_bool(it->value, "avatar_change_allowed") },
         { features::beta_update, get_bool(it->value, "beta_update") },
         { features::ssl_verify, get_bool(it->value, "ssl_verify") },
         { features::profile_agent_as_domain_url, get_bool(it->value, "profile_agent_as_domain_url") },
         { features::need_gdpr_window, get_bool(it->value, "need_gdpr_window") },
         { features::need_introduce_window, get_bool(it->value, "need_introduce_window") },
         { features::has_nicknames, get_bool(it->value, "has_nicknames") },
         { features::zstd_request_enabled, get_bool(it->value, "zstd_request_enabled") },
         { features::zstd_response_enabled, get_bool(it->value, "zstd_response_enabled") },
         { features::external_phone_attachment, get_bool(it->value, "external_phone_attachment") },
         { features::external_url_config, get_bool(it->value, "external_url_config") },
         { features::omicron_enabled, get_bool(it->value, "omicron_enabled") },
         { features::sticker_suggests, get_bool(it->value, "sticker_suggests") },
         { features::sticker_suggests_server, get_bool(it->value, "sticker_suggests_server") },
         { features::smartreplies, get_bool(it->value, "smartreplies") },
         { features::spell_check, get_bool(it->value, "spell_check") },
         { features::favorites_message_onpremise, get_bool(it->value, "favorites_message_onpremise") },
         { features::info_change_allowed, get_bool(it->value, "info_change_allowed") },
         { features::call_link_v2_enabled, get_bool(it->value, "call_link_v2_enabled") },
         { features::vcs_call_by_link_enabled, get_bool(it->value, "vcs_call_by_link_enabled") },
         { features::vcs_webinar_enabled, get_bool(it->value, "vcs_webinar_enabled") },
         { features::statuses_enabled, get_bool(it->value, "statuses_enabled") },
         { features::global_contact_search_allowed, get_bool(it->value, "global_contact_search_allowed") },
         { features::show_reactions, get_bool(it->value, "show_reactions") },
         { features::open_icqcom_link, get_bool(it->value, "open_icqcom_link") },
         { features::statistics, get_bool(it->value, "statistics") },
         { features::statistics_mytracker, get_bool(it->value, "statistics_mytracker") },
         { features::force_update_check_allowed, get_bool(it->value, "force_update_check_allowed") },
         { features::call_room_info_enabled, get_bool(it->value, "call_room_info_enabled") },
         { features::store_version, get_bool(it->value, "store_version") },
         { features::otp_login_open_mail_link, get_bool(it->value, "otp_login_open_mail_link") },
         { features::dns_workaround, get_bool(it->value, "dns_workaround_enabled") },
         { features::external_emoji, get_bool(it->value, "external_emoji") },
         { features::show_your_invites_to_group_enabled, get_bool(it->value, "show_your_invites_to_group_enabled") },
         { features::group_invite_blacklist_enabled, get_bool(it->value, "group_invite_blacklist_enabled") },
         { features::contact_us_via_backend, get_bool(it->value, "contact_us_via_backend") },
         { features::update_from_backend, get_bool(it->value, "update_from_backend") },
         { features::invite_by_sms, get_bool(it->value, "invite_by_sms") },
         { features::show_sms_notify_setting, get_bool(it->value, "show_sms_notify_setting") },
         { features::silent_message_delete, get_bool(it->value, "silent_message_delete") },
         { features::remove_deleted_from_notifications, get_bool(it->value, "remove_deleted_from_notifications") },
         { features::long_path_tooltip_enabled, get_bool(it->value, "long_path_tooltip_enabled") },
         { features::installer_crash_send, get_bool(it->value, "installer_crash_send") },
         { features::custom_statuses_enabled, get_bool(it->value, "custom_statuses_enabled") },
         { features::formatting_in_bubbles, get_bool(it->value, "formatting_in_bubbles") },
         { features::formatting_in_input, get_bool(it->value, "formatting_in_input") },
         { features::apps_bar_enabled, get_bool(it->value, "apps_bar_enabled") },
         { features::status_in_apps_bar, get_bool(it->value, "status_in_apps_bar") },
         { features::scheduled_messages_enabled, get_bool(it->value, "scheduled_messages_enabled") },
         { features::threads_enabled, get_bool(it->value, "threads_enabled") },
         { features::reminders_enabled, get_bool(it->value, "reminders_enabled") },
         { features::support_shared_federation_stickerpacks, get_bool(it->value, "support_shared_federation_stickerpacks") },
         { features::url_ftp_protocols_allowed, get_bool(it->value, "url_ftp_protocols_allowed") },
         { features::draft_enabled, get_bool(it->value, "draft_enabled") },
         { features::message_corner_menu, get_bool(it->value, "message_corner_menu") },
         { features::task_creation_in_chat_enabled, get_bool(it->value, "task_creation_in_chat_enabled") },
         { features::smartreply_suggests_feature_enabled, get_bool(it->value, "smartreply_suggests_feature_enabled") },
         { features::smartreply_suggests_text, get_bool(it->value, "smartreply_suggests_text") },
         { features::smartreply_suggests_stickers, get_bool(it->value, "smartreply_suggests_stickers") },
         { features::smartreply_suggests_for_quotes, get_bool(it->value, "smartreply_suggests_for_quotes") },
         { features::compact_mode_by_default, get_bool(it->value, "compact_mode_by_default") },
         { features::expanded_gallery, get_bool(it->value, "expanded_gallery") },
         { features::restricted_files_enabled, get_bool(it->value, "restricted_files_enabled") },
         { features::antivirus_check_enabled, get_bool(it->value, "antivirus_check_enabled") },
         { features::antivirus_check_progress_visible, get_bool(it->value, "antivirus_check_progress_visible") },
         { features::external_user_agreement, get_bool(it->value, "external_user_agreement") },
         { features::user_agreement_enabled, get_bool(it->value, "user_agreement_enabled") },
         { features::delete_account_enabled, get_bool(it->value, "delete_account_enabled") },
         { features::delete_account_via_admin, get_bool(it->value, "delete_account_via_admin") },
         { features::has_registry_about, get_bool(it->value, "has_registry_about") },
         { features::organization_structure_enabled, get_bool(it->value, "organization_structure_enabled") },
         { features::tasks_enabled, get_bool(it->value, "tasks_enabled") },
         { features::calendar_enabled, get_bool(it->value, "calendar_enabled") },
         { features::tarm_mail, get_bool(it->value, "tarm_mail") },
         { features::tarm_cloud, get_bool(it->value, "tarm_cloud") },
         { features::tarm_calls, get_bool(it->value, "tarm_calls") },
         { features::calendar_self_auth, get_bool(it->value, "calendar_self_auth") },
         { features::mail_enabled, get_bool(it->value, "mail_enabled") },
         { features::mail_self_auth, get_bool(it->value, "mail_self_auth") },
         { features::cloud_self_auth, get_bool(it->value, "cloud_self_auth") },
         { features::digital_assistant_search_positioning, get_bool(it->value, "digital_assistant_search_positioning") },
         { features::leading_last_name, get_bool(it->value, "leading_last_name") },
         { features::report_messages_enabled, get_bool(it->value, "report_messages_enabled") }
    }};

    if (std::is_sorted(std::cbegin(res), std::cend(res), is_less_by_first))
      return std::make_optional(std::move(res));
    assert(false);
  }
  return {};
}

static std::optional<values_array> parse_values(const rapidjson::Value &_node) {
  if (const auto it = _node.FindMember("values"); it != _node.MemberEnd()) {
    values_array res = {{
         { values::product_path, get_string(it->value, "product_path") },
         { values::product_path_mac, get_string(it->value, "product_path_mac") },
         { values::app_name_win, get_string(it->value, "app_name_win") },
         { values::app_name_mac, get_string(it->value, "app_name_mac") },
         { values::app_name_linux, get_string(it->value, "app_name_linux") },
         { values::user_agent_app_name, get_string(it->value, "user_agent_app_name") },
         { values::client_b64, get_string(it->value, "client_b64") },
         { values::client_id, get_string(it->value, "client_id") },
         { values::client_rapi, get_string(it->value, "client_rapi") },
         { values::oauth_type, get_string(it->value, "oauth_type") },
         { values::oauth_scope, get_string(it->value, "oauth_scope") },
         { values::product_name, get_string(it->value, "product_name") },
         { values::product_name_short, get_string(it->value, "product_name_short") },
         { values::product_name_full, get_string(it->value, "product_name_full") },
         { values::main_instance_mutex_linux, get_string(it->value, "main_instance_mutex_linux") },
         { values::main_instance_mutex_win, get_string(it->value, "main_instance_mutex_win") },
         { values::crossprocess_pipe, get_string(it->value, "crossprocess_pipe") },
         { values::register_url_scheme_csv, get_string(it->value, "register_url_scheme_csv") },
         { values::installer_shortcut_win, get_string(it->value, "installer_shortcut_win") },
         { values::installer_menu_folder_win, get_string(it->value, "installer_menu_folder_win") }
    }};

    if (std::is_sorted(std::cbegin(res), std::cend(res), is_less_by_first))
      return std::make_optional(std::move(res));
    assert(false);
  }
  return {};
}

} // namespace config
