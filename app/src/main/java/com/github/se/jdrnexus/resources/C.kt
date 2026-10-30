package com.github.se.jdrnexus.resources

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

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
    const val greeting = "main_screen_greeting"
    const val greeting_robo = "second_screen_greeting"

    const val main_screen_container = "main_screen_container"
    const val second_screen_container = "second_screen_container"

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

    /** Tag of the selection button for [diceName] (e.g. "D20"). */
    fun diceOfflineSelect(diceName: String) = "dice_offline_select_$diceName"
  }
}
