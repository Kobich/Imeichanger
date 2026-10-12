package com.engboost.imeichanger.ui.main

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.engboost.imeichanger.R
import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.Device
import com.engboost.imeichanger.domain.PhoneModel
import com.engboost.imeichanger.ui.theme.ImeiChangerTheme

@Composable
fun DeviceSelectionDialog(
    state: MainDialog.DeviceSelection,
    onQueryChange: (String) -> Unit,
    onBrandSelected: (Brand) -> Unit,
    onModelSelected: (PhoneModel) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        // System back steps from the model list to the brand list before closing.
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DeviceSelectionContent(
            state = state,
            onQueryChange = onQueryChange,
            onBrandSelected = onBrandSelected,
            onModelSelected = onModelSelected,
            onBack = onBack,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionContent(
    state: MainDialog.DeviceSelection,
    onQueryChange: (String) -> Unit,
    onBrandSelected: (Brand) -> Unit,
    onModelSelected: (PhoneModel) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val brand = state.brand
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(brand ?: stringResource(R.string.device_selection_brand_title)) },
                navigationIcon = {
                    if (brand != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
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
                placeholder = {
                    Text(
                        stringResource(
                            if (brand == null) R.string.device_search_brand_hint else R.string.device_search_model_hint,
                        ),
                    )
                },
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
                state.isLoading -> Unit
                brand == null -> BrandList(
                    brands = remember(state.brands, state.query) { state.visibleBrands },
                    onBrandSelected = onBrandSelected,
                )
                else -> ModelList(
                    models = remember(state.models, state.query) { state.visibleModels },
                    onModelSelected = onModelSelected,
                )
            }
        }
    }
}

@Composable
private fun BrandList(
    brands: List<Brand>,
    onBrandSelected: (Brand) -> Unit,
) {
    if (brands.isEmpty()) {
        CenteredHint(stringResource(R.string.device_search_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(brands, key = { it.name.lowercase() }) { brand ->
            ListItem(
                headlineContent = { Text(brand.name) },
                supportingContent = {
                    Text(pluralStringResource(R.plurals.brand_model_count, brand.modelCount, brand.modelCount))
                },
                trailingContent = {
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { onBrandSelected(brand) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ModelList(
    models: List<PhoneModel>,
    onModelSelected: (PhoneModel) -> Unit,
) {
    if (models.isEmpty()) {
        CenteredHint(stringResource(R.string.device_search_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(models, key = { it.name.lowercase() }) { model ->
            ListItem(
                headlineContent = { Text(model.displayName) },
                supportingContent = {
                    Text(
                        text = model.tacs.singleOrNull()?.let { stringResource(R.string.device_tac, it) }
                            ?: pluralStringResource(R.plurals.model_tac_count, model.tacs.size, model.tacs.size),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                modifier = Modifier.clickable { onModelSelected(model) },
            )
            HorizontalDivider()
        }
    }
}

/** Model name without the brand prefix: "10.OR G2" under brand "10.OR" shows as "G2". */
private fun stripBrand(brand: String, name: String): String =
    name
        .takeIf { it.startsWith("$brand ", ignoreCase = true) }
        ?.substring(brand.length + 1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: name

internal val Device.displayName: String
    get() = stripBrand(brand, name)

private val PhoneModel.displayName: String
    get() = stripBrand(brand, name)

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
private fun BrandPagePreview() {
    ImeiChangerTheme {
        DeviceSelectionContent(
            state = MainDialog.DeviceSelection(
                brands = listOf(Brand(name = "10.OR", modelCount = 3), Brand(name = "Alcatel", modelCount = 41)),
                isLoading = false,
            ),
            onQueryChange = {},
            onBrandSelected = {},
            onModelSelected = {},
            onBack = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ModelPagePreview() {
    ImeiChangerTheme {
        DeviceSelectionContent(
            state = MainDialog.DeviceSelection(
                brand = "10.OR",
                models = listOf(
                    PhoneModel(brand = "10.OR", name = "10.OR D", tacs = listOf("91161200")),
                    PhoneModel(brand = "10.OR", name = "10.OR G2", tacs = listOf("35872810", "35872811")),
                ),
                isLoading = false,
            ),
            onQueryChange = {},
            onBrandSelected = {},
            onModelSelected = {},
            onBack = {},
            onDismiss = {},
        )
    }
}
