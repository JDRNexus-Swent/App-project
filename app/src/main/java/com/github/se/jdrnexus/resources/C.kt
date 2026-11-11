package com.github.se.jdrnexus.resources

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

// Like R, but C
/**
 * Contains test tags used to identify UI elements in Jetpack Compose.
 *
 * These constants provide stable, reusable identifiers for elements such as text fields, buttons,
 * and containers. They allow UI tests to locate and interact with specific components using
 * `Modifier.testTag()` and `onNodeWithTag()`, without depending on the element's displayed text or
 * position in the UI.
 *
 * Keeping tags centralized avoids hard-coded strings throughout the application and helps ensure
 * that the UI implementation and its tests use the same identifiers.
 */
object C {
  object Tag {
    const val main_screen_container = "main_screen_container"

    const val shared_top_bar = "shared_top_bar"
    const val shared_back_button = "shared_back_button"
    const val shared_bottom_bar = "shared_bottom_bar"

    const val sign_in_email = "auth_sign_in_email"
    const val sign_in_password = "auth_sign_in_password"
    const val sign_in_password_visibility = "auth_sign_in_password_visibility"
    const val sign_in_submit = "auth_sign_in_submit"
    const val sign_in_google = "auth_sign_in_google"
    const val sign_in_forgot_password = "auth_sign_in_forgot_password"
    const val sign_in_sign_up = "auth_sign_in_sign_up"
    const val sign_up_username = "auth_sign_up_username"
    const val sign_up_email = "auth_sign_up_email"
    const val sign_up_password = "auth_sign_up_password"
    const val sign_up_password_visibility = "auth_sign_up_password_visibility"
    const val sign_up_submit = "auth_sign_up_submit"
    const val sign_up_sign_in = "auth_sign_up_sign_in"

    fun bottomTab(name: String) = "bottom_tab_$name"

    const val map_screen = "map_screen"
    const val map_google_map = "map_google_map"
    const val map_game_pin = "map_game_pin"
    const val map_city_search_field = "map_city_search_field"
    const val map_city_search_button = "map_city_search_button"
    const val map_filters_toggle = "map_filters_toggle"
    const val map_game_system_filter = "map_game_system_filter"
    const val map_available_seats_filter = "map_available_seats_filter"
    const val map_apply_filters = "map_apply_filters"
    const val map_location_button = "map_location_button"
    const val map_status_message = "map_status_message"
    const val map_retry = "map_retry"
    const val map_selected_game_title = "map_selected_game_title"
    const val map_selected_game_distance = "map_selected_game_distance"
    const val map_view_game_details = "map_view_game_details"

    const val dice_offline_screen = "dice_offline_screen"
    const val dice_offline_close = "dice_offline_close"
    const val dice_offline_selected_type = "dice_offline_selected_type"
    const val dice_offline_range = "dice_offline_range"
    const val dice_offline_die = "dice_offline_die"
    const val dice_offline_result = "dice_offline_result"
    const val dice_offline_result_detail = "dice_offline_result_detail"
    const val dice_offline_loading = "dice_offline_loading"
    const val dice_offline_modifier_value = "dice_offline_modifier_value"
    const val dice_offline_modifier_decrease = "dice_offline_modifier_decrease"
    const val dice_offline_modifier_increase = "dice_offline_modifier_increase"
    const val dice_offline_roll = "dice_offline_roll"
    const val dice_offline_error = "dice_offline_error"
    const val dice_offline_error_dismiss = "dice_offline_error_dismiss"
    const val dice_offline_shake_toggle = "dice_offline_shake_toggle"

    const val enter_workspace_button = "enter_workspace_button"
    const val workspace_actions_fab = "workspace_actions_fab"
    const val workspace_add_folder = "workspace_add_folder"
    const val workspace_add_file = "workspace_add_file"
    const val workspace_add_files_dialog = "add_files_dialog"
    const val workspace_add_files_list = "add_files_list"
    const val workspace_add_files_done = "add_files_done"
    const val workspace_create_folder_dialog = "create_folder_dialog"
    const val workspace_create_folder_name_input = "create_folder_name_input"
    const val workspace_create_folder_confirm = "create_folder_confirm"
    const val workspace_create_folder_progress = "create_folder_progress"
    const val workspace_create_folder_cancel = "create_folder_cancel"
    const val workspace_edit_item_dialog = "edit_item_dialog"
    const val workspace_edit_item_name_input = "edit_item_name_input"
    const val workspace_edit_item_content_input = "edit_item_content_input"
    const val workspace_edit_item_save = "edit_item_save"
    const val workspace_edit_item_progress = "edit_item_progress"
    const val workspace_edit_item_cancel = "edit_item_cancel"
    const val workspace_remove_item_dialog = "remove_item_dialog"
    const val workspace_remove_item_confirm = "remove_item_confirm"
    const val workspace_remove_item_progress = "remove_item_progress"
    const val workspace_remove_item_cancel = "remove_item_cancel"
    const val workspace_operation_progress = "workspace_operation_progress"
    const val workspace_loading = "workspace_loading"

    fun workspaceItem(fileId: String) = "workspace_item_$fileId"

    fun workspaceEdit(fileId: String) = "workspace_edit_$fileId"

    fun workspaceRemove(fileId: String) = "workspace_remove_$fileId"

    fun workspaceVisibility(fileId: String) = "workspace_visibility_$fileId"

    /** Tag of the selection button for [diceName] (e.g. "D20"). */
    fun diceOfflineSelect(diceName: String) = "dice_offline_select_$diceName"

    const val game_gm_list = "game_gm_list"
    const val game_gm_loading = "game_gm_loading"
    const val game_gm_error = "game_gm_error"
    const val game_gm_empty_combat = "game_gm_empty_combat"
    const val game_gm_empty_npcs = "game_gm_empty_npcs"
    const val game_gm_empty_lore = "game_gm_empty_lore"
    const val game_gm_add_enemy = "game_gm_add_enemy"
    const val game_gm_add_enemy_dialog = "game_gm_add_enemy_dialog"
    const val game_gm_enemy_name = "game_gm_enemy_name"
    const val game_gm_enemy_hp = "game_gm_enemy_hp"
    const val game_gm_enemy_max_hp = "game_gm_enemy_max_hp"
    const val game_gm_enemy_ac = "game_gm_enemy_ac"
    const val game_gm_enemy_condition = "game_gm_enemy_condition"
    const val game_gm_start_combat = "game_gm_start_combat"
    const val game_gm_next_round = "game_gm_next_round"
    const val game_gm_end_combat = "game_gm_end_combat"
    const val game_gm_dice = "game_gm_dice"
    const val game_gm_hidden_sections = "game_gm_hidden_sections"

    fun gameGmEnemy(id: String) = "game_gm_enemy_$id"

    fun gameGmEnemyIncrease(id: String) = "game_gm_enemy_increase_$id"

    fun gameGmEnemyDecrease(id: String) = "game_gm_enemy_decrease_$id"

    fun gameGmEnemyRemove(id: String) = "game_gm_enemy_remove_$id"

    fun gameGmActivity(id: String) = "game_gm_activity_$id"

    fun gameGmNpc(id: String) = "game_gm_npc_$id"

    fun gameGmLore(id: String) = "game_gm_lore_$id"

    fun gameGmLoreExpand(id: String) = "game_gm_lore_expand_$id"

    /** Tag of the button hiding the GM dashboard section [key] (e.g. "combat"). */
    fun gameGmHide(key: String) = "game_gm_hide_$key"

    /** Tag of the button restoring the hidden GM dashboard section [key]. */
    fun gameGmRestore(key: String) = "game_gm_restore_$key"
  }
}
