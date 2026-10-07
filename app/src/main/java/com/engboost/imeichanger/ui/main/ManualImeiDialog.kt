package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.Imei
import com.engboost.imeichanger.ui.theme.ImeichangerTheme

@Composable
fun ManualImeiDialog(
    state: MainDialog.ManualInput,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.manual_input_title)) },
        text = {
            OutlinedTextField(
                value = state.text,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.manual_input_label)) },
                singleLine = true,
                isError = state.error != null,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.error?.let { stringResource(it.messageRes) }.orEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.manual_input_counter, state.text.length, Imei.LENGTH))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            // Must run after the field is attached to focusRequester.
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.text.isNotEmpty()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private val ManualImeiError.messageRes: Int
    get() = when (this) {
        ManualImeiError.INVALID_FORMAT -> R.string.error_invalid_format
        ManualImeiError.SAME_AS_CURRENT -> R.string.error_same_as_current
    }

@Preview
@Composable
private fun ManualImeiDialogPreview() {
    ImeichangerTheme {
        ManualImeiDialog(
            state = MainDialog.ManualInput(text = "35693803564380", error = ManualImeiError.INVALID_FORMAT),
            onValueChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
