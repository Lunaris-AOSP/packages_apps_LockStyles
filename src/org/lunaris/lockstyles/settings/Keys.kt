/*
 * SPDX-FileCopyrightText: 2016-2026 crDroid Android Project
 * SPDX-FileCopyrightText: 2023-2024 The risingOS Android Project
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import org.lunaris.lockstyles.settings.LockSetting.Bool
import org.lunaris.lockstyles.settings.LockSetting.Num
import org.lunaris.lockstyles.settings.LockSetting.Text
import org.lunaris.lockstyles.settings.Table.SECURE
import org.lunaris.lockstyles.settings.Table.SYSTEM

/**
 * Every lock screen setting Singularity had, with its table and default as its preference XML
 * declared them (lunaris_settings_lock_screen, clock_styles, lockscreen_clock_preview_settings,
 * media_art_settings, pulse_settings, nowplaying_settings, rising_settings_lockscreen_widgets,
 * wallpaper_depth and lunaris_settings_udfps_custom).
 */
object Keys {
    // Interface
    val MUSIC_PILL = Bool(SECURE, "ax_dynamic_bar_keyguard_music_pill", false)
    val BATTERY_INFO = Bool(SYSTEM, "lockscreen_battery_info", true)
    val CHARGING_TIME = Bool(SYSTEM, "lockscreen_charging_time", true)
    val EMERGENCY_BUTTON = Bool(SECURE, "lockscreen_show_emergency_button", true)
    val AFFORDANCE_SINGLE_TAP = Bool(SECURE, "keyguard_affordance_single_tap", false)
    val USER_SWITCHER = Bool(SYSTEM, "kg_user_switcher_enabled", false)
    val SCRIM_TRANSPARENT = Bool(SECURE, "keyguard_scrim_transparent", true)

    // Weather
    val SMARTSPACE = Bool(SECURE, "lockscreen_smartspace_enabled", true)
    val WEATHER = Bool(SYSTEM, "lockscreen_weather_enabled", false)
    val WEATHER_LOCATION = Bool(SYSTEM, "lockscreen_weather_location", false)
    val WEATHER_TEXT = Bool(SYSTEM, "lockscreen_weather_text", true)
    val WEATHER_WIND = Bool(SYSTEM, "lockscreen_weather_wind_info", false)
    val WEATHER_HUMIDITY = Bool(SYSTEM, "lockscreen_weather_humidity_info", false)

    // Gestures
    val DOUBLE_TAP_SLEEP = Bool(SYSTEM, "double_tap_sleep_lockscreen", true)
    val FP_SUCCESS_VIBRATE = Bool(SYSTEM, "fp_success_vibrate", true)
    val FP_ERROR_VIBRATE = Bool(SYSTEM, "fp_error_vibrate", true)
    val FP_RIPPLE = Bool(SYSTEM, "enable_ripple_effect", true)

    // Misc
    val POWER_MENU = Bool(SYSTEM, "lockscreen_enable_power_menu", true)

    // Clock
    // The clock's style, colours, size and animations are the gallery's, in CustomClocks.
    val NOTIFICATIONS_ABOVE_CLOCK = Bool(SECURE, "lock_screen_notifications_above_clock", false)
    val AOD_IMAGE = Bool(SYSTEM, "custom_aod_image_enabled", false)
    val AOD_IMAGE_URI = Text(SYSTEM, "custom_aod_image_uri", null)

    // Widgets
    val INFO_WIDGETS = Bool(SYSTEM, "lockscreen_info_widgets_enabled", false)
    val WIDGETS_COLOR_MODE = Text(SYSTEM, "lockscreen_widgets_color_mode", "default")
    val WIDGETS_CUSTOM_COLOR = Num(SYSTEM, "lockscreen_widgets_custom_color", -1)
    val WIDGETS = Bool(SYSTEM, "lockscreen_widgets_enabled", false)
    val WIDGETS_STYLE = Text(SYSTEM, "lockscreen_widgets_style", "0")
    val WIDGETS_TRANSPARENCY = Num(SYSTEM, "lockscreen_widgets_transparency", 100)
    val WIDGETS_MAIN = Text(SYSTEM, "lockscreen_widgets", null)
    val WIDGETS_EXTRAS = Text(SYSTEM, "lockscreen_widgets_extras", null)

    // Depth wallpaper
    val DEPTH = Bool(SYSTEM, "depth_wallpaper_enabled", false)
    val DEPTH_SPATIAL = Bool(SYSTEM, "depth_wallpaper_spatial_effect", false)
    val DEPTH_SPATIAL_SENSITIVITY =
        Num(SYSTEM, "depth_wallpaper_spatial_sensitivity", 100)
    val DEPTH_AUTO_SUBJECT = Bool(SYSTEM, "depth_wallpaper_auto_subject", false)
    val DEPTH_SUBJECT = Text(SYSTEM, "depth_wallpaper_subject_image_uri", null)
    val DEPTH_OPACITY = Num(SYSTEM, "depth_wallpaper_opacity", 100)
    val DEPTH_OFFSET_X = Num(SYSTEM, "depth_wallpaper_offset_x", 0)
    val DEPTH_OFFSET_Y = Num(SYSTEM, "depth_wallpaper_offset_y", 0)
    val DEPTH_BOTTOM_FADE = Num(SYSTEM, "depth_wallpaper_bottom_fade", 100)
    val DEPTH_FADE_CURVE = Num(SYSTEM, "depth_wallpaper_fade_curve", 50)
    val DEPTH_BOTTOM_INSET = Num(SYSTEM, "depth_wallpaper_bottom_inset", 180)

    // Media art
    val MEDIA_ART = Bool(SYSTEM, "ls_media_art_enabled", false)
    val MEDIA_ART_AOD = Bool(SYSTEM, "ls_media_art_aod_enabled", false)
    val MEDIA_ART_AOD_DIM = Num(SYSTEM, "ls_media_art_aod_dim_level", 35)
    val MEDIA_ART_FILTER = Text(SYSTEM, "ls_media_art_filter", "1")
    val MEDIA_ART_PIXEL_SIZE = Num(SYSTEM, "ls_media_art_pixel_size", 20)
    val MEDIA_ART_FADE = Num(SYSTEM, "ls_media_art_fade_level", 40)
    val MEDIA_ART_BLUR = Num(SYSTEM, "ls_media_art_blur_level", 90)

    // Pulse
    val PULSE = Bool(SECURE, "lockscreen_pulse_enabled", false)
    val PULSE_AMBIENT = Bool(SECURE, "pulse_show_on_ambient", true)
    val PULSE_BASS_HAPTICS = Text(SECURE, "pulse_bass_haptics", "0")
    val PULSE_RENDERER = Text(SECURE, "pulse_renderer", "solid")
    val PULSE_CAPTURE_MODE = Text(SECURE, "pulse_capture_mode", "0")
    val PULSE_ROUNDED = Bool(SECURE, "pulse_rounded_bars", false)
    val PULSE_COLOR = Text(SECURE, "pulse_color", "lavalamp")
    val PULSE_CUSTOM_COLOR = Num(SECURE, "pulse_custom_color", 0xFFFFFFFF.toInt())
    val PULSE_BAR_COUNT = Num(SECURE, "pulse_bar_count", 32)
    val PULSE_HEIGHT = Num(SECURE, "pulse_height_multiplier", 100)

    // Now playing
    val NOW_PLAYING = Bool(SYSTEM, "nowplaying_enabled", false)
    val NP_ICON_STYLE = Text(SYSTEM, "nowplaying_icon_style", "0")
    val NP_ICON_SIZE = Num(SYSTEM, "nowplaying_icon_size", 24)
    val NP_LYRICS = Bool(SYSTEM, "nowplaying_lyrics_mode", false)
    val NP_COMPACT = Bool(SYSTEM, "nowplaying_use_compact_style", false)
    val NP_TAP_EXPAND = Bool(SYSTEM, "nowplaying_tap_to_expand", true)
    val NP_COLOR_MODE = Text(SYSTEM, "nowplaying_color_mode", "0")
    val NP_VERTICAL = Num(SYSTEM, "nowplaying_vertical_position", 88)
    val NP_TRACK_SIZE = Num(SYSTEM, "nowplaying_track_text_size", 14)
    val NP_ARTIST_SIZE = Num(SYSTEM, "nowplaying_artist_text_size", 12)
    val NP_ON_LOCKSCREEN = Bool(SYSTEM, "nowplaying_show_on_lockscreen", true)
    val NP_ON_AOD = Bool(SYSTEM, "nowplaying_show_on_aod", true)

    // Under-display fingerprint
    val UDFPS_ICON_TYPE = Text(SYSTEM, "udfps_icon_type", "0")
    val UDFPS_ICON_AOD = Bool(SYSTEM, "udfps_icon_aod", false)
    val UDFPS_ICON = Num(SYSTEM, "udfps_icon", 0)
    val UDFPS_CUSTOM_ICON = Text(SYSTEM, "udfps_custom_fp_icon_path", null)
    val UDFPS_ANIM_STYLE = Num(SYSTEM, "udfps_anim_style", 0)
    val UDFPS_ANIM_SIZE = Num(SYSTEM, "udfps_anim_size", 50)
    val UDFPS_ANIM_OFFSET = Num(SYSTEM, "udfps_anim_offset", 0)
}
