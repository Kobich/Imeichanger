package com.engboost.imeichanger.data

import android.content.res.AssetManager
import com.engboost.imeichanger.domain.DeviceImei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Каталог из CSV в assets (колонки: название, компания, IMEI).
 *
 * Файл читается потоково на каждый запрос и не держится в памяти целиком: на сотнях тысяч
 * строк это дешевле по памяти. Если скорости не хватит — импортировать в Room с FTS.
 */
class AssetDeviceCatalog(
    private val assets: AssetManager,
    private val fileName: String = "devices.csv",
) : DeviceCatalog {

    override suspend fun search(query: String, limit: Int): DeviceSearchResult =
        withContext(Dispatchers.IO) {
            val needle = query.trim()
            val found = ArrayList<DeviceImei>(limit)
            var hasMore = false
            assets.open(fileName).bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    ensureActive()
                    val device = DeviceCsv.parseLine(line) ?: continue
                    if (!device.matches(needle)) continue
                    if (found.size == limit) {
                        hasMore = true
                        break
                    }
                    found += device
                }
            }
            DeviceSearchResult(found, hasMore)
        }

    private fun DeviceImei.matches(needle: String): Boolean =
        needle.isEmpty() ||
            name.contains(needle, ignoreCase = true) ||
            company.contains(needle, ignoreCase = true)
}
