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
import com.engboost.imeichanger.ui.theme.ImeichangerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImeichangerTheme {
                ImeichangerApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Корень UI: единственное место, где живёт ViewModel. Ниже передаются только state и колбэки. */
@Composable
fun ImeichangerApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        state = state,
        onSimSelected = viewModel::onSimSelected,
        onResetImei = viewModel::onResetImei,
        onManualInputClick = viewModel::onManualInputClick,
        onManualImeiChange = viewModel::onManualImeiChange,
        onManualImeiConfirm = viewModel::onManualImeiConfirm,
        onAutoChangeClick = viewModel::onAutoChangeClick,
        onDeviceQueryChange = viewModel::onDeviceQueryChange,
        onDeviceSelected = viewModel::onDeviceSelected,
        onDialogDismiss = viewModel::onDialogDismiss,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}
