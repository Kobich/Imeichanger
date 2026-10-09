package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.Device

interface DeviceCatalog {
    suspend fun search(query: String, limit: Int): DeviceSearchResult
}

data class DeviceSearchResult(
    val devices: List<Device>,
    val hasMore: Boolean,
)
