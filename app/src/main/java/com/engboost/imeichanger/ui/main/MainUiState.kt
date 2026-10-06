package com.engboost.imeichanger.ui.main

import com.engboost.imeichanger.domain.DeviceModel
import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot

data class MainUiState(
    val selectedSlot: SimSlot = SimSlot.SIM1,
    val currentImei: String? = null,
    val factoryImei: String? = null,
    /** История выбранного слота, новые записи сверху. */
    val history: List<ImeiChangeRecord> = emptyList(),
    val dialog: MainDialog? = null,
    val message: MainMessage? = null,
) {
    /** Кнопка сброса показывается, только если IMEI отличается от «родного». */
    val canResetImei: Boolean
        get() = currentImei != null && factoryImei != null && currentImei != factoryImei
}

sealed interface MainDialog {
    data class ManualInput(
        val text: String = "",
        val error: ManualImeiError? = null,
    ) : MainDialog

    data class DeviceSelection(
        val query: String = "",
        val devices: List<DeviceModel> = emptyList(),
        val hasMore: Boolean = false,
        val isLoading: Boolean = true,
        val loadFailed: Boolean = false,
    ) : MainDialog
}

enum class ManualImeiError {
    INVALID_FORMAT,
    SAME_AS_CURRENT,
}

/** Одноразовое сообщение (snackbar). [id] различает повторные одинаковые сообщения. */
sealed interface MainMessage {
    val id: Long

    /**
     * Заглушка: IMEI прошёл проверку (ручной ввод) или сгенерирован по TAC [device]
     * (автоматическая смена), дальше будет экран подтверждения.
     */
    data class ImeiAccepted(
        val imei: String,
        val device: DeviceModel? = null,
        override val id: Long = System.nanoTime(),
    ) : MainMessage
}
