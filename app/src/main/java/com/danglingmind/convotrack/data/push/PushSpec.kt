package com.danglingmind.convotrack.data.push

import com.danglingmind.convotrack.R

/** What a data-only push turns into: text resources and channel. Pure, so it is unit-tested. */
data class PushSpec(
    val titleRes: Int,
    val bodyRes: Int,
    val channelId: String,
    val safety: Boolean,
    val collapses: Boolean,
    private val namesRider: Boolean,
) {
    /** Format args for [bodyRes]: the rider (caller / emergency) first when the text names one. */
    fun args(rideName: String, riderName: String): Array<Any> =
        if (namesRider) arrayOf(riderName, rideName) else arrayOf(rideName)

    companion object {
        const val CHANNEL_UPDATES = "ride_updates"
        const val CHANNEL_SAFETY = "safety"

        fun from(type: String, reason: String?): PushSpec? = when (type) {
            "RIDE_STARTED" -> PushSpec(R.string.push_ride_started_title, R.string.push_ride_started_body, CHANNEL_UPDATES, false, true, false)
            "RIDE_ENDED" -> PushSpec(R.string.push_ride_ended_title, R.string.push_ride_ended_body, CHANNEL_UPDATES, false, true, false)
            "REGROUP_CALLED" -> PushSpec(
                R.string.push_regroup_title,
                when (reason) {
                    "FOOD" -> R.string.push_regroup_food_body
                    "SCENIC" -> R.string.push_regroup_scenic_body
                    "FUEL" -> R.string.push_regroup_fuel_body
                    else -> R.string.push_regroup_generic_body
                },
                CHANNEL_UPDATES, false, false, true,
            )
            "EMERGENCY" -> PushSpec(R.string.push_emergency_title, R.string.push_emergency_body, CHANNEL_SAFETY, true, false, true)
            else -> null
        }
    }
}
