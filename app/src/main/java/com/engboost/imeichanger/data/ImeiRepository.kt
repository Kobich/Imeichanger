package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import kotlinx.coroutines.flow.Flow

interface ImeiRepository {
    val currentImeis: Flow<Map<SimSlot, String>>

    val originalImeis: Flow<Map<SimSlot, String>>
    val history: Flow<List<ImeiChangeRecord>>

    suspend fun changeImei(slot: SimSlot, newImei: String)

    suspend fun resetImei(slot: SimSlot)
}
