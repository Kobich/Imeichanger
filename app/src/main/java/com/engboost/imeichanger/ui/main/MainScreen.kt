package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.DeviceModel
import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import com.engboost.imeichanger.ui.theme.ImeichangerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onSimSelected: (SimSlot) -> Unit,
    onResetImei: () -> Unit,
    onManualInputClick: () -> Unit,
    onManualImeiChange: (String) -> Unit,
    onManualImeiConfirm: () -> Unit,
    onAutoChangeClick: () -> Unit,
    onDeviceQueryChange: (String) -> Unit,
    onDeviceSelected: (DeviceModel) -> Unit,
    onChangeConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text(context))
        onMessageShown(message.id)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                // No top bar: keep a comfortable gap below the status bar inset.
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SimSelector(
                    selectedSlot = state.selectedSlot,
                    onSimSelected = onSimSelected,
                )
            }
            item {
                CurrentImeiCard(
                    imei = state.currentImei,
                    canReset = state.canResetImei,
                    onResetImei = onResetImei,
                )
            }
            item {
                ChangeImeiCard(
                    onManualInputClick = onManualInputClick,
                    onAutoChangeClick = onAutoChangeClick,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = state.history,
                    key = { "${it.slot}-${it.timestamp.toEpochMilli()}-${it.newImei}" },
                ) { record ->
                    HistoryItem(record)
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        is MainDialog.ManualInput -> ManualImeiDialog(
            state = dialog,
            onValueChange = onManualImeiChange,
            onConfirm = onManualImeiConfirm,
            onDismiss = onDialogDismiss,
        )
        is MainDialog.DeviceSelection -> DeviceSelectionDialog(
            state = dialog,
            onQueryChange = onDeviceQueryChange,
            onDeviceSelected = onDeviceSelected,
            onDismiss = onDialogDismiss,
        )
        is MainDialog.Confirm -> ConfirmImeiDialog(
            state = dialog,
            onConfirm = onChangeConfirm,
            onDismiss = onDialogDismiss,
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimSelector(
    selectedSlot: SimSlot,
    onSimSelected: (SimSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = SimSlot.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        slots.forEachIndexed { index, slot ->
            SegmentedButton(
                selected = slot == selectedSlot,
                onClick = { onSimSelected(slot) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = slots.size),
            ) {
                Text(stringResource(slot.labelRes))
            }
        }
    }
}

@Composable
private fun CurrentImeiCard(
    imei: String?,
    canReset: Boolean,
    onResetImei: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.current_imei_title), modifier = modifier) {
        Text(
            text = imei ?: stringResource(R.string.imei_unknown),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
        )
        if (canReset) {
            OutlinedButton(
                onClick = onResetImei,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reset_imei))
            }
        }
    }
}

@Composable
private fun ChangeImeiCard(
    onManualInputClick: () -> Unit,
    onAutoChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.change_imei_title), modifier = modifier) {
        Button(
            onClick = onManualInputClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.manual_input))
        }
        FilledTonalButton(
            onClick = onAutoChangeClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.auto_change))
        }
    }
}

@Composable
private fun HistoryItem(
    record: ImeiChangeRecord,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss") }
    val dateTime = remember(record.timestamp) {
        formatter.format(record.timestamp.atZone(ZoneId.systemDefault()))
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = dateTime,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.history_old, record.oldImei),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.history_new, record.newImei),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private val SimSlot.labelRes: Int
    get() = when (this) {
        SimSlot.SIM1 -> R.string.sim_1
        SimSlot.SIM2 -> R.string.sim_2
    }

private fun MainMessage.text(context: android.content.Context): String = when (this) {
    is MainMessage.ImeiAccepted -> if (device != null) {
        context.getString(R.string.message_device_accepted, device.displayName, device.brand, imei)
    } else {
        context.getString(R.string.message_imei_accepted, imei)
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    ImeichangerTheme {
        MainScreen(
            state = MainUiState(
                selectedSlot = SimSlot.SIM1,
                currentImei = "490154203237518",
                factoryImei = "356938035643809",
                history = listOf(
                    ImeiChangeRecord(
                        slot = SimSlot.SIM1,
                        timestamp = Instant.parse("2026-10-05T12:30:00Z"),
                        oldImei = "356938035643809",
                        newImei = "490154203237518",
                    ),
                ),
            ),
            onSimSelected = {},
            onResetImei = {},
            onManualInputClick = {},
            onManualImeiChange = {},
            onManualImeiConfirm = {},
            onAutoChangeClick = {},
            onDeviceQueryChange = {},
            onDeviceSelected = {},
            onChangeConfirm = {},
            onDialogDismiss = {},
            onMessageShown = {},
        )
    }
}
