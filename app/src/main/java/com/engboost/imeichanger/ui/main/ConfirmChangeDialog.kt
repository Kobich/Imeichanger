package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.SimSlot
import com.engboost.imeichanger.ui.theme.ImeiChangerTheme

@Composable
fun ConfirmChangeDialog(
    state: MainDialog.ConfirmChange,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.confirm_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.confirm_change_slot, stringResource(state.slot.labelRes)))
                if (state.oldImei != null) {
                    Text(
                        text = stringResource(R.string.confirm_change_old, state.oldImei),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.confirm_change_new, state.newImei),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.device != null) {
                    Text(
                        text = stringResource(
                            R.string.device_name_with_brand,
                            state.device.displayName,
                            state.device.brand,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm_change_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private val SimSlot.labelRes: Int
    get() = when (this) {
        SimSlot.SIM1 -> R.string.sim_1
        SimSlot.SIM2 -> R.string.sim_2
    }

@Preview
@Composable
private fun ConfirmChangeDialogPreview() {
    ImeiChangerTheme {
        ConfirmChangeDialog(
            state = MainDialog.ConfirmChange(
                slot = SimSlot.SIM1,
                oldImei = "356938035643809",
                newImei = "490154203237518",
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
