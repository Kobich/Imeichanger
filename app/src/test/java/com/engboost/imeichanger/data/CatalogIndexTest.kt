package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.Brand
import com.engboost.imeichanger.domain.Device
import com.engboost.imeichanger.domain.PhoneModel
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogIndexTest {

    private val index = CatalogIndex(
        sequenceOf(
            Device(brand = "Zte", name = "Zte Blade", tac = "11111111"),
            Device(brand = "10.OR", name = "10.OR G2", tac = "35872810"),
            Device(brand = "10.or", name = "10.or g2", tac = "35872811"),
            Device(brand = "10.OR", name = "10.OR G2", tac = "35872810"),
            Device(brand = "10.OR", name = "10.OR D", tac = "91161200"),
            Device(brand = "", name = "Nameless", tac = "22222222"),
        ),
    )

    @Test
    fun brandsAreGroupedCaseInsensitivelyAndSorted() {
        assertEquals(
            listOf(
                Brand(name = "10.OR", modelCount = 2),
                Brand(name = CatalogIndex.UNKNOWN_BRAND, modelCount = 1),
                Brand(name = "Zte", modelCount = 1),
            ),
            index.brands,
        )
    }

    @Test
    fun sameModelRowsAreMergedWithDistinctTacs() {
        assertEquals(
            listOf(
                PhoneModel(brand = "10.OR", name = "10.OR D", tacs = listOf("91161200")),
                PhoneModel(brand = "10.OR", name = "10.OR G2", tacs = listOf("35872810", "35872811")),
            ),
            index.models("10.or"),
        )
    }

    @Test
    fun unknownBrandReturnsNoModels() {
        assertEquals(emptyList<PhoneModel>(), index.models("Nokia"))
    }
}
