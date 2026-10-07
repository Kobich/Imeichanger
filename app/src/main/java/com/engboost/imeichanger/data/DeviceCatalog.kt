package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceModel

interface DeviceCatalog {
    suspend fun search(query: String, limit: Int): DeviceSearchResult
}

data class DeviceSearchResult(
    val devices: List<DeviceModel>,
    val hasMore: Boolean,
)
