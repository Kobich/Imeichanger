package com.engboost.imeichanger.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engboost.imeichanger.data.AssetDeviceCatalog
import com.engboost.imeichanger.data.DeviceCatalog
import com.engboost.imeichanger.data.ImeiRepository
import com.engboost.imeichanger.data.ImeiStorage
import com.engboost.imeichanger.data.PhoneExImeiRepository
import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.Device
import com.engboost.imeichanger.domain.Imei
import com.engboost.imeichanger.domain.PhoneModel
import com.engboost.imeichanger.domain.SimSlot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class MainViewModel(
    private val repository: ImeiRepository,
    private val deviceCatalog: DeviceCatalog,
) : ViewModel() {
    private data class LocalState(
        val selectedSlot: SimSlot = SimSlot.SIM1,
        val dialog: MainDialog? = null,
        val message: MainMessage? = null,
    )

    private val localState = MutableStateFlow(LocalState())
    private var catalogJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        localState,
        repository.currentImeis,
        repository.originalImeis,
        repository.history,
        repository.isBusy,
    ) { local, imeis, originalImeis, history, isBusy ->
        val slot = local.selectedSlot
        MainUiState(
            selectedSlot = slot,
            currentImei = imeis[slot],
            originalImei = originalImeis[slot],
            history = history
                .filter { it.slot == slot }
                .sortedByDescending { it.timestamp },
            dialog = local.dialog,
            message = local.message,
            isBusy = isBusy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun onSimSelected(slot: SimSlot) {
        localState.update { it.copy(selectedSlot = slot) }
    }

    fun onResetImeiClick() {
        val slot = localState.value.selectedSlot
        viewModelScope.launch {
            repository.resetImei(slot)
        }
    }

    fun onMessageShown(id: Long) {
        localState.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    fun onDialogDismiss() {
        catalogJob?.cancel()
        localState.update { it.copy(dialog = null) }
    }

    fun onManualInputClick() {
        localState.update { it.copy(dialog = MainDialog.ManualInput()) }
    }

    fun onManualImeiChange(text: String) {
        updateDialog<MainDialog.ManualInput> {
            MainDialog.ManualInput(text = text.filter(Char::isDigit).take(Imei.LENGTH))
        }
    }

    fun onManualImeiSubmit() {
        val dialog = localState.value.dialog as? MainDialog.ManualInput ?: return
        val imei = dialog.text
        val error = when {
            Imei.validate(imei) != null -> ManualImeiError.INVALID_FORMAT
            imei == uiState.value.currentImei -> ManualImeiError.SAME_AS_CURRENT
            else -> null
        }
        if (error != null) {
            updateDialog<MainDialog.ManualInput> { it.copy(error = error) }
        } else {
            showConfirmDialog(imei)
        }
    }

    fun onAutoChangeClick() {
        localState.update { it.copy(dialog = MainDialog.DeviceSelection()) }
        loadCatalog { DeviceSelectionPage(brands = deviceCatalog.brands()) }
    }

    fun onDeviceQueryChange(query: String) {
        updateDialog<MainDialog.DeviceSelection> { it.copy(query = query) }
    }

    fun onBrandSelected(brand: Brand) {
        updateDialog<MainDialog.DeviceSelection> {
            it.copy(brand = brand.name, query = "", models = emptyList())
        }
        loadCatalog { DeviceSelectionPage(models = deviceCatalog.models(brand.name)) }
    }

    /** Back from the model list goes to the brand list; from the brand list it closes. */
    fun onDeviceSelectionBack() {
        val dialog = localState.value.dialog as? MainDialog.DeviceSelection ?: return
        if (dialog.brand == null) {
            onDialogDismiss()
        } else {
            catalogJob?.cancel()
            updateDialog<MainDialog.DeviceSelection> {
                it.copy(brand = null, query = "", models = emptyList(), isLoading = false, loadFailed = false)
            }
        }
    }

    fun onModelSelected(model: PhoneModel) {
        catalogJob?.cancel()
        // A model may have several TACs: any of them makes a plausible IMEI.
        val device = Device(brand = model.brand, name = model.name, tac = model.tacs.random())
        showConfirmDialog(Imei.generate(device.tac), device)
    }

    private class DeviceSelectionPage(
        val brands: List<Brand>? = null,
        val models: List<PhoneModel>? = null,
    )

    private fun loadCatalog(load: suspend () -> DeviceSelectionPage) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            updateDialog<MainDialog.DeviceSelection> { it.copy(isLoading = true, loadFailed = false) }
            val page = try {
                load()
            } catch (e: IOException) {
                Log.e(TAG, "Device catalog read failed", e)
                null
            }
            updateDialog<MainDialog.DeviceSelection> {
                it.copy(
                    brands = page?.brands ?: it.brands,
                    models = page?.models ?: it.models,
                    isLoading = false,
                    loadFailed = page == null,
                )
            }
        }
    }

    private fun showConfirmDialog(imei: String, device: Device? = null) {
        localState.update {
            it.copy(
                dialog = MainDialog.ConfirmChange(
                    slot = it.selectedSlot,
                    oldImei = uiState.value.currentImei,
                    newImei = imei,
                    device = device,
                    isFirstChange = uiState.value.history.isEmpty(),
                ),
            )
        }
    }

    fun onImeiChangeConfirm() {
        val dialog = localState.value.dialog as? MainDialog.ConfirmChange ?: return
        localState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            repository.changeImei(dialog.slot, dialog.newImei)
            localState.update {
                it.copy(message = MainMessage.ImeiChanged(imei = dialog.newImei, device = dialog.device))
            }
        }
    }

    private inline fun <reified T : MainDialog> updateDialog(crossinline transform: (T) -> MainDialog) {
        localState.update { state ->
            val dialog = state.dialog as? T ?: return@update state
            state.copy(dialog = transform(dialog))
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    // Swap to StubImeiRepository() for UI preview without a device.
                    repository = PhoneExImeiRepository(
                        storage = ImeiStorage(File(application.filesDir, "imei_state.json")),
                    ),
                    deviceCatalog = AssetDeviceCatalog(application.assets),
                )
            }
        }
    }
}
