package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.DeviceModel
import com.engboost.imeichanger.ui.theme.ImeichangerTheme

@Composable
fun DeviceSelectionDialog(
    state: MainDialog.DeviceSelection,
    onQueryChange: (String) -> Unit,
    onDeviceSelected: (DeviceModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DeviceSelectionContent(
            state = state,
            onQueryChange = onQueryChange,
            onDeviceSelected = onDeviceSelected,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionContent(
    state: MainDialog.DeviceSelection,
    onQueryChange: (String) -> Unit,
    onDeviceSelected: (DeviceModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_selection_title)) },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.device_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            when {
                state.loadFailed -> CenteredHint(stringResource(R.string.device_catalog_error))
                !state.isLoading && state.devices.isEmpty() ->
                    CenteredHint(stringResource(R.string.device_search_empty))
                else -> DeviceList(
                    devices = state.devices,
                    hasMore = state.hasMore,
                    onDeviceSelected = onDeviceSelected,
                )
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceModel>,
    hasMore: Boolean,
    onDeviceSelected: (DeviceModel) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // No key: the catalog may contain duplicate rows.
        items(devices) { device ->
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.device_name_with_company, device.displayName, device.brand))
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.device_tac, device.tac),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                modifier = Modifier.clickable { onDeviceSelected(device) },
            )
            HorizontalDivider()
        }
        if (hasMore) {
            item {
                Text(
                    text = stringResource(R.string.device_search_has_more, devices.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

internal val DeviceModel.displayName: String
    get() = model
        .takeIf { it.startsWith("$brand ", ignoreCase = true) }
        ?.substring(brand.length + 1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: model

@Composable
private fun CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceSelectionPreview() {
    ImeichangerTheme {
        DeviceSelectionContent(
            state = MainDialog.DeviceSelection(
                query = "10.or",
                devices = listOf(
                    DeviceModel(brand = "10.OR", model = "10.OR D", tac = "91161200"),
                    DeviceModel(brand = "10.OR", model = "10.OR D2", tac = "91163440"),
                ),
                hasMore = true,
                isLoading = false,
            ),
            onQueryChange = {},
            onDeviceSelected = {},
            onDismiss = {},
        )
    }
}
