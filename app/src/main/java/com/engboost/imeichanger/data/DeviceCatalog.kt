package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceModel

interface DeviceCatalog {
    /**
     * Ищет модели, у которых бренд или название содержат [query] (без учёта регистра),
     * а для запроса из цифр — TAC начинается с [query]. Пустой запрос возвращает первые записи.
     */
    suspend fun search(query: String, limit: Int): DeviceSearchResult
}

data class DeviceSearchResult(
    val devices: List<DeviceModel>,
    /** Совпадений больше, чем [DeviceCatalog.search] limit — стоит уточнить запрос. */
    val hasMore: Boolean,
)
