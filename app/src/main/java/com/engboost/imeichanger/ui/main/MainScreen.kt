package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.Imei
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
    onManualImeiChange: (String) -> Unit,
    onApplyManualImei: () -> Unit,
    onAutoChangeImei: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
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
                    onResetImei = onResetImei,
                )
            }
            item {
                ManualChangeCard(
                    input = state.manualInput,
                    onValueChange = onManualImeiChange,
                    onApply = onApplyManualImei,
                )
            }
            item {
                AutoChangeCard(onAutoChange = onAutoChangeImei)
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
    onResetImei: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.current_imei_title), modifier = modifier) {
        Text(
            text = imei ?: stringResource(R.string.imei_unknown),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
        )
        OutlinedButton(
            onClick = onResetImei,
            enabled = imei != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reset_imei))
        }
    }
}

@Composable
private fun ManualChangeCard(
    input: ManualImeiInput,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canApply = input.text.length == Imei.LENGTH
    SectionCard(title = stringResource(R.string.manual_change_title), modifier = modifier) {
        OutlinedTextField(
            value = input.text,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.manual_change_label)) },
            singleLine = true,
            isError = input.error != null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = input.error?.let { stringResource(it.messageRes) }.orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.manual_change_counter, input.text.length, Imei.LENGTH))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canApply) onApply() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onApply,
            enabled = canApply,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.manual_change_apply))
        }
    }
}

@Composable
private fun AutoChangeCard(
    onAutoChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.auto_change_title), modifier = modifier) {
        Text(
            text = stringResource(R.string.auto_change_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onAutoChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.auto_change_apply))
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

private val ManualImeiError.messageRes: Int
    get() = when (this) {
        ManualImeiError.WRONG_LENGTH -> R.string.error_wrong_length
        ManualImeiError.INVALID_CHECKSUM -> R.string.error_invalid_checksum
        ManualImeiError.SAME_AS_CURRENT -> R.string.error_same_as_current
    }

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    ImeichangerTheme {
        MainScreen(
            state = MainUiState(
                selectedSlot = SimSlot.SIM1,
                currentImei = "356938035643809",
                manualInput = ManualImeiInput(
                    text = "35693803564381",
                    error = ManualImeiError.WRONG_LENGTH,
                ),
                history = listOf(
                    ImeiChangeRecord(
                        slot = SimSlot.SIM1,
                        timestamp = Instant.parse("2026-10-05T12:30:00Z"),
                        oldImei = "490154203237518",
                        newImei = "356938035643809",
                    ),
                ),
            ),
            onSimSelected = {},
            onResetImei = {},
            onManualImeiChange = {},
            onApplyManualImei = {},
            onAutoChangeImei = {},
        )
    }
}
