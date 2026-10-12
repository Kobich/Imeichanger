package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.Device
import com.engboost.imeichanger.domain.PhoneModel

/**
 * Groups catalog rows into brand -> models. Brand and model names are matched
 * case-insensitively; the first spelling seen is the one shown. Rows repeating
 * a model with another TAC are merged into one [PhoneModel].
 */
class CatalogIndex(devices: Sequence<Device>) {
    private val modelsByBrand: Map<String, List<PhoneModel>>
    val brands: List<Brand>

    init {
        // brand key -> model key -> (display brand, display name, TACs)
        val grouped = LinkedHashMap<String, LinkedHashMap<String, PhoneModel>>()
        for (device in devices) {
            val brand = device.brand.ifBlank { UNKNOWN_BRAND }
            val models = grouped.getOrPut(brand.lowercase()) { LinkedHashMap() }
            val key = device.name.lowercase()
            val model = models[key]
            models[key] = when {
                model == null -> PhoneModel(brand = brand, name = device.name, tacs = listOf(device.tac))
                device.tac in model.tacs -> model
                else -> model.copy(tacs = model.tacs + device.tac)
            }
        }
        modelsByBrand = grouped.mapValues { (_, models) ->
            models.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        brands = modelsByBrand.values
            .map { models -> Brand(name = models.first().brand, modelCount = models.size) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun models(brand: String): List<PhoneModel> = modelsByBrand[brand.lowercase()].orEmpty()

    companion object {
        const val UNKNOWN_BRAND = "Unknown"
    }
}
