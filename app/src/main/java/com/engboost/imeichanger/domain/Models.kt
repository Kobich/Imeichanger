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

/** One catalog row, and the device chosen for an IMEI change. */
data class Device(
    val brand: String,
    val name: String,
    val tac: String,
)

data class Brand(
    val name: String,
    val modelCount: Int,
)

/** A model of [brand] with every TAC the catalog lists for it (rows with the same name merged). */
data class PhoneModel(
    val brand: String,
    val name: String,
    val tacs: List<String>,
)
