package com.engboost.imeichanger.domain

import java.time.Instant

enum class SimSlot {
    SIM1,
    SIM2,
}

data class ImeiChangeRecord(
    val slot: SimSlot,
    val timestamp: Instant,
    val oldImei: String,
    val newImei: String,
)
