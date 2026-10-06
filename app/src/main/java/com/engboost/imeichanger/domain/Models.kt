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

/** Модель устройства из TAC-каталога. IMEI генерируется из [tac] при выборе. */
data class DeviceModel(
    val brand: String,
    val model: String,
    /** Type Allocation Code — первые 8 цифр IMEI, задают модель устройства. */
    val tac: String,
)
