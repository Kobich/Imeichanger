package com.engboost.imeichanger

// =====================================================================
// TEMPORARY TEST BUILD (step 1: phoneEx descriptor probe)
// ---------------------------------------------------------------------
// MainActivity below launches the phoneEx TEST screen, not the real UI.
// The real UI entry (ImeichangerApp -> MainScreen) is preserved,
// commented out, at the bottom of this file. To restore the real app:
//   1. delete / comment the test MainActivity + PhoneExScreen,
//   2. uncomment the "ORIGINAL UI ENTRY" block at the bottom.
// The real UI files (ui/main, data, domain) stay untouched in the repo.
// =====================================================================

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engboost.imeichanger.phoneex.CallbackLayout
import com.engboost.imeichanger.phoneex.OemHookCallback
import com.engboost.imeichanger.phoneex.PhoneExClient
import com.engboost.imeichanger.phoneex.PhoneExIntrospection
import com.engboost.imeichanger.ui.theme.ImeichangerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImeichangerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhoneExScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PhoneExScreen(modifier: Modifier = Modifier) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var logText by remember { mutableStateOf("") }
    // Keep strong refs so async callbacks are not garbage-collected before the modem replies.
    val liveCallbacks = remember { mutableListOf<OemHookCallback>() }
    var layout by remember { mutableStateOf(CallbackLayout.FALLBACK) }

    var imei by remember { mutableStateOf("353332990071343") }
    var slot by remember { mutableStateOf("0") }
    var atCmd by remember { mutableStateOf("AT+EGMR=0,7") }

    fun log(line: String) {
        mainHandler.post { logText = (logText + line + "\n").takeLast(8000) }
    }

    fun newCallback(): OemHookCallback {
        val cb = OemHookCallback(
            layout = layout,
            onAtCmdResp = { s, t, c -> log("CB onAtCmdResp slot=$s token=$t cmd=$c") },
            onAtUrcInd = { s, u -> log("CB onAtUrcInd slot=$s urc=$u") },
            onError = { e -> log("CB onError: $e") },
            log = ::log,
        )
        liveCallbacks.add(cb)
        return cb
    }

    // Single serial worker so commands run in a deterministic order (no racing threads).
    val worker = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    fun runCmd(cmd: String, slotInt: Int) {
        val cb = newCallback()
        try {
            PhoneExClient.callPhoneEx(
                slot = slotInt,
                token = 0L,
                atCmd = cmd,
                callback = cb,
                log = ::log,
            )
        } catch (e: Exception) {
            log("ERROR send '$cmd': ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun enqueue(cmd: String) {
        val slotInt = slot.toIntOrNull() ?: 0
        worker.execute { runCmd(cmd, slotInt) }
    }

    // Proper write sequence: radio OFF -> write IMEI -> reboot modem -> read back.
    fun enqueueWriteSequence() {
        val slotInt = slot.toIntOrNull() ?: 0
        val target = imei.trim()
        worker.execute {
            log("=== write sequence start (imei=$target slot=$slotInt) ===")
            runCmd("AT+CFUN=0", slotInt)
            Thread.sleep(1000)
            runCmd("AT+EGMR=1,7,\"$target\"", slotInt)
            Thread.sleep(1000)
            runCmd("AT+CFUN=1,1", slotInt)
            Thread.sleep(3000)
            runCmd("AT+EGMR=2,7", slotInt) // read back to verify
            log("=== write sequence end ===")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("phoneEx / IMEI — шаг 1 (проба)")

        OutlinedTextField(
            value = slot, onValueChange = { slot = it },
            label = { Text("Слот (0 = IMEI1, 1 = IMEI2)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = imei, onValueChange = { imei = it },
            label = { Text("IMEI для записи") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = atCmd, onValueChange = { atCmd = it },
            label = { Text("Произвольная AT-команда") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                Thread {
                    val l = PhoneExIntrospection.probe(::log)
                    mainHandler.post { layout = l }
                }.start()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("1. Проба дескриптора") }

        Button(
            onClick = { enqueue(atCmd) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("2. Отправить AT-команду (чтение/диагностика)") }

        Button(
            onClick = { enqueue("AT+EGMR=0,7") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("3. Прочитать IMEI (EGMR read)") }

        Button(
            onClick = { enqueueWriteSequence() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("4. Записать IMEI (CFUN=0 → запись → ресет → чтение)") }

        Button(
            onClick = { logText = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Очистить лог") }

        Text(
            text = if (logText.isEmpty()) "(лог пуст — нажми «Проба дескриптора»)" else logText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

// =====================================================================
// ORIGINAL UI ENTRY (from the compose UI branch) — commented out during
// the phoneEx test phase. Restore this (and the imports it needs:
//   androidx.compose.runtime.getValue
//   androidx.lifecycle.compose.collectAsStateWithLifecycle
//   androidx.lifecycle.viewmodel.compose.viewModel
//   com.engboost.imeichanger.ui.main.MainScreen
//   com.engboost.imeichanger.ui.main.MainViewModel )
// and point MainActivity.setContent { ImeichangerTheme { ImeichangerApp(...) } }
// =====================================================================
//
// /** UI root: the only place that touches the ViewModel; children get state and callbacks. */
// @Composable
// fun ImeichangerApp(
//     modifier: Modifier = Modifier,
//     viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
// ) {
//     val state by viewModel.uiState.collectAsStateWithLifecycle()
//     MainScreen(
//         state = state,
//         onSimSelected = viewModel::onSimSelected,
//         onResetImei = viewModel::onResetImei,
//         onManualInputClick = viewModel::onManualInputClick,
//         onManualImeiChange = viewModel::onManualImeiChange,
//         onManualImeiConfirm = viewModel::onManualImeiConfirm,
//         onAutoChangeClick = viewModel::onAutoChangeClick,
//         onDeviceQueryChange = viewModel::onDeviceQueryChange,
//         onDeviceSelected = viewModel::onDeviceSelected,
//         onDialogDismiss = viewModel::onDialogDismiss,
//         onMessageShown = viewModel::onMessageShown,
//         modifier = modifier,
//     )
// }
