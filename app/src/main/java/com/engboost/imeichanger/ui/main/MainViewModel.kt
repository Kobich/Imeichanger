package com.engboost.imeichanger.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engboost.imeichanger.data.ImeiRepository
import com.engboost.imeichanger.data.StubImeiRepository
import com.engboost.imeichanger.domain.Imei
import com.engboost.imeichanger.domain.ImeiError
import com.engboost.imeichanger.domain.SimSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ImeiRepository,
) : ViewModel() {

    private val selectedSlot = MutableStateFlow(SimSlot.SIM1)
    private val manualInput = MutableStateFlow(ManualImeiInput())

    val uiState: StateFlow<MainUiState> = combine(
        selectedSlot,
        manualInput,
        repository.currentImeis,
        repository.history,
    ) { slot, input, imeis, history ->
        MainUiState(
            selectedSlot = slot,
            currentImei = imeis[slot],
            manualInput = input,
            history = history
                .filter { it.slot == slot }
                .sortedByDescending { it.timestamp },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun onSimSelected(slot: SimSlot) {
        if (selectedSlot.value == slot) return
        selectedSlot.value = slot
        manualInput.value = ManualImeiInput()
    }

    fun onManualImeiChange(text: String) {
        manualInput.value = ManualImeiInput(
            text = text.filter(Char::isDigit).take(Imei.LENGTH),
        )
    }

    fun onApplyManualImei() {
        val slot = selectedSlot.value
        val imei = manualInput.value.text
        val error = when {
            imei == uiState.value.currentImei -> ManualImeiError.SAME_AS_CURRENT
            else -> when (Imei.validate(imei)) {
                ImeiError.WRONG_LENGTH, ImeiError.NOT_DIGITS -> ManualImeiError.WRONG_LENGTH
                ImeiError.INVALID_CHECKSUM -> ManualImeiError.INVALID_CHECKSUM
                null -> null
            }
        }
        if (error != null) {
            manualInput.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            repository.changeImei(slot, imei)
            manualInput.value = ManualImeiInput()
        }
    }

    fun onAutoChangeImei() {
        val slot = selectedSlot.value
        val tac = uiState.value.currentImei?.let(Imei::tacOf)
        viewModelScope.launch {
            repository.changeImei(slot, Imei.generate(tac))
        }
    }

    fun onResetImei() {
        val slot = selectedSlot.value
        viewModelScope.launch {
            repository.resetImei(slot)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(StubImeiRepository()) }
        }
    }
}
