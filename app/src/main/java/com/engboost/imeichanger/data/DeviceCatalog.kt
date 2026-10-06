package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceImei

interface DeviceCatalog {
    /**
     * Ищет устройства, у которых название или компания содержат [query] (без учёта регистра).
     * Пустой запрос возвращает первые записи каталога.
     */
    suspend fun search(query: String, limit: Int): DeviceSearchResult
}

data class DeviceSearchResult(
    val devices: List<DeviceImei>,
    /** Совпадений больше, чем [DeviceCatalog.search] limit — стоит уточнить запрос. */
    val hasMore: Boolean,
)
