package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

class StubImeiRepository : ImeiRepository {
    override val factoryImeis: StateFlow<Map<SimSlot, String>> = MutableStateFlow(
        mapOf(
            SimSlot.SIM1 to "356938035643809",
            SimSlot.SIM2 to "356938035643817",
        ),
    )

    // SIM2 starts changed so the reset button is visible.
    private val _currentImeis = MutableStateFlow(
        factoryImeis.value + (SimSlot.SIM2 to "490154203237518"),
    )
    override val currentImeis: StateFlow<Map<SimSlot, String>> = _currentImeis.asStateFlow()

    private val _history = MutableStateFlow<List<ImeiChangeRecord>>(emptyList())
    override val history: StateFlow<List<ImeiChangeRecord>> = _history.asStateFlow()

    override suspend fun changeImei(slot: SimSlot, newImei: String) {
        val oldImei = _currentImeis.value.getValue(slot)
        if (oldImei == newImei) return
        _currentImeis.update { it + (slot to newImei) }
        _history.update {
            it + ImeiChangeRecord(
                slot = slot,
                timestamp = Instant.now(),
                oldImei = oldImei,
                newImei = newImei,
            )
        }
    }

    override suspend fun resetImei(slot: SimSlot) {
        changeImei(slot, factoryImeis.value.getValue(slot))
    }
}
