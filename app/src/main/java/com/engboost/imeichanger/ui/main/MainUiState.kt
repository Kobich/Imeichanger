package com.engboost.imeichanger.ui.main

import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot

data class MainUiState(
    val selectedSlot: SimSlot = SimSlot.SIM1,
    val currentImei: String? = null,
    val manualInput: ManualImeiInput = ManualImeiInput(),
    /** История выбранного слота, новые записи сверху. */
    val history: List<ImeiChangeRecord> = emptyList(),
)

data class ManualImeiInput(
    val text: String = "",
    val error: ManualImeiError? = null,
)

enum class ManualImeiError {
    WRONG_LENGTH,
    INVALID_CHECKSUM,
    SAME_AS_CURRENT,
}
