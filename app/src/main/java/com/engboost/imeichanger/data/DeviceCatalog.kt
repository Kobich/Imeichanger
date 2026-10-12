package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.PhoneModel

interface DeviceCatalog {
    /** All brands, sorted by name. */
    suspend fun brands(): List<Brand>

    /** Models of [brand], sorted by name. */
    suspend fun models(brand: String): List<PhoneModel>
}
