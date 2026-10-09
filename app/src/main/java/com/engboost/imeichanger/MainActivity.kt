package com.engboost.imeichanger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engboost.imeichanger.ui.main.MainScreen
import com.engboost.imeichanger.ui.main.MainViewModel
import com.engboost.imeichanger.ui.theme.ImeiChangerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImeiChangerTheme {
                ImeiChangerApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** UI root: the only place that touches the ViewModel; children get state and callbacks. */
@Composable
fun ImeiChangerApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        state = state,
        onSimSelected = viewModel::onSimSelected,
        onResetImeiClick = viewModel::onResetImeiClick,
        onManualInputClick = viewModel::onManualInputClick,
        onManualImeiChange = viewModel::onManualImeiChange,
        onManualImeiSubmit = viewModel::onManualImeiSubmit,
        onAutoChangeClick = viewModel::onAutoChangeClick,
        onDeviceQueryChange = viewModel::onDeviceQueryChange,
        onDeviceSelected = viewModel::onDeviceSelected,
        onImeiChangeConfirm = viewModel::onImeiChangeConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}
