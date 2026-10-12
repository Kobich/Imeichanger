package com.engboost.imeichanger.data

import android.content.res.AssetManager
import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.PhoneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads every `*.csv` in `assets/[directory]` once, on first use, and keeps the
 * grouped [CatalogIndex] in memory. The catalog may be split into several files.
 */
class AssetDeviceCatalog(
    private val assets: AssetManager,
    private val directory: String = "tac",
) : DeviceCatalog {
    private val loadMutex = Mutex()
    private var index: CatalogIndex? = null

    override suspend fun brands(): List<Brand> = index().brands

    override suspend fun models(brand: String): List<PhoneModel> = index().models(brand)

    private suspend fun index(): CatalogIndex = loadMutex.withLock {
        index ?: withContext(Dispatchers.IO) { load() }.also { index = it }
    }

    private fun load(): CatalogIndex {
        val files = assets.list(directory).orEmpty().filter { it.endsWith(".csv") }.sorted()
        val devices = files.asSequence().flatMap { file ->
            assets.open("$directory/$file").bufferedReader().use { reader ->
                reader.readLines().mapNotNull(DeviceCsv::parseLine)
            }
        }
        return CatalogIndex(devices)
    }
}
