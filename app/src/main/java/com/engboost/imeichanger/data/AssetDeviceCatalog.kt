package com.engboost.imeichanger.data

import android.content.res.AssetManager
import com.engboost.imeichanger.domain.DeviceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Streams every `*.csv` in `assets/[directory]` on each query instead of loading the whole
 * catalog into memory, so the big file can be split into parts.
 */
class AssetDeviceCatalog(
    private val assets: AssetManager,
    private val directory: String = "tac",
) : DeviceCatalog {
    override suspend fun search(query: String, limit: Int): DeviceSearchResult =
        withContext(Dispatchers.IO) {
            val needle = query.trim()
            val byTac = needle.isNotEmpty() && needle.all(Char::isDigit)
            val found = ArrayList<DeviceModel>(limit)
            val files = assets.list(directory).orEmpty().filter { it.endsWith(".csv") }.sorted()
            for (file in files) {
                assets.open("$directory/$file").bufferedReader().use { reader ->
                    for (line in reader.lineSequence()) {
                        ensureActive()
                        val device = DeviceCsv.parseLine(line) ?: continue
                        if (!device.matches(needle, byTac)) continue
                        if (found.size == limit) return@withContext DeviceSearchResult(found, hasMore = true)
                        found += device
                    }
                }
            }
            DeviceSearchResult(found, hasMore = false)
        }

    private fun DeviceModel.matches(needle: String, byTac: Boolean): Boolean = when {
        needle.isEmpty() -> true
        byTac && tac.startsWith(needle) -> true
        else -> model.contains(needle, ignoreCase = true) || brand.contains(needle, ignoreCase = true)
    }
}
