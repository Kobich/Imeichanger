package com.engboost.imeichanger.ui.main

import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.Device
import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.PhoneModel
import com.engboost.imeichanger.domain.SimSlot

data class MainUiState(
    val selectedSlot: SimSlot = SimSlot.SIM1,
    val currentImei: String? = null,
    val originalImei: String? = null,
    val history: List<ImeiChangeRecord> = emptyList(),
    val dialog: MainDialog? = null,
    val confirmation: ChangeConfirmation? = null,
    val message: MainMessage? = null,
    val isBusy: Boolean = false,
) {
    val canResetImei: Boolean
        get() = currentImei != null && originalImei != null && currentImei != originalImei
}

sealed interface MainDialog {
    data class ManualInput(
        val text: String = "",
        val error: ManualImeiError? = null,
    ) : MainDialog

    /**
     * Two pages: the brand list ([brand] == null), then the models of the chosen brand.
     * [query] filters whichever list is shown.
     */
    data class DeviceSelection(
        val brand: String? = null,
        val query: String = "",
        val brands: List<Brand> = emptyList(),
        val models: List<PhoneModel> = emptyList(),
        val isLoading: Boolean = true,
        val loadFailed: Boolean = false,
    ) : MainDialog {
        val visibleBrands: List<Brand>
            get() = brands.filter { it.name.contains(query.trim(), ignoreCase = true) }

        val visibleModels: List<PhoneModel>
            get() = models.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
}

/**
 * "Change IMEI?" shown on top of [MainUiState.dialog]: cancelling returns to that
 * dialog (manual input or device selection), confirming closes both.
 */
data class ChangeConfirmation(
    val slot: SimSlot,
    val oldImei: String?,
    val newImei: String,
    val device: Device? = null,
    // No change recorded for this slot yet: the current IMEI is the one remembered
    // as original, so the user should check it against the box or *#06#.
    val isFirstChange: Boolean = false,
)

enum class ManualImeiError {
    INVALID_FORMAT,
    SAME_AS_CURRENT,
}

/** One-shot snackbar message; [id] distinguishes repeated identical messages. */
sealed interface MainMessage {
    val id: Long

    data class ImeiChanged(
        val imei: String,
        val device: Device? = null,
        override val id: Long = System.nanoTime(),
    ) : MainMessage
}
