package com.selfflow.app.data.alarm

import com.selfflow.app.R

enum class RingtoneOption(
    val key: String,
    val titleRes: Int,
    val rawRes: Int?
) {
    SYSTEM_DEFAULT(
        key = "system_default",
        titleRes = R.string.ringtone_system_default,
        rawRes = null
    ),
    MORNING_LIGHT(
        key = "morning_light",
        titleRes = R.string.ringtone_morning_light,
        rawRes = R.raw.morning_light
    ),
    DIGITAL_BEEP(
        key = "digital_beep",
        titleRes = R.string.ringtone_digital_beep,
        rawRes = R.raw.digital_beep
    ),
    CLASSIC_BELL(
        key = "classic_bell",
        titleRes = R.string.ringtone_classic_bell,
        rawRes = R.raw.classic_bell
    );

    companion object {
        fun fromKey(key: String?): RingtoneOption {
            return entries.find { it.key == key } ?: SYSTEM_DEFAULT
        }
    }
}
