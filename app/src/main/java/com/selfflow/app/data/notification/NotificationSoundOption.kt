package com.selfflow.app.data.notification

import com.selfflow.app.R

enum class NotificationSoundOption(
    val key: String,
    val titleRes: Int,
    val rawRes: Int?
) {
    SYSTEM_DEFAULT(
        key = "system_default",
        titleRes = R.string.notification_sound_system_default,
        rawRes = null
    ),
    SOFT_CHIME(
        key = "soft_chime",
        titleRes = R.string.notification_sound_soft_chime,
        rawRes = R.raw.notification_soft
    ),
    DIGITAL_BEEP(
        key = "digital_beep",
        titleRes = R.string.notification_sound_digital_beep,
        rawRes = R.raw.digital_beep
    ),
    CLASSIC_BELL(
        key = "classic_bell",
        titleRes = R.string.notification_sound_classic_bell,
        rawRes = R.raw.classic_bell
    );

    companion object {
        fun fromKey(key: String?): NotificationSoundOption {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}
