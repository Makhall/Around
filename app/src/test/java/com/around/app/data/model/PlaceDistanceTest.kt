package com.around.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceDistanceTest {

    private fun place(
        id: String,
        latitude: Double,
        longitude: Double,
        radius: Int,
    ) = Place(
        id = id,
        circleId = "circle-1",
        name = id,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radius,
        createdBy = "uid-alice",
        createdAt = 1L,
    )

    @Test
    fun `finds a place when the location is inside its radius`() {
        val home = place("home", -6.1754, 106.8272, 100)
        assertEquals(home, listOf(home).placeAt(-6.1755, 106.8273))
    }

    @Test
    fun `does not match outside the radius`() {
        val home = place("home", -6.1754, 106.8272, 100)
        assertNull(listOf(home).placeAt(-6.18, 106.83))
    }

    @Test
    fun `picks the first matching place`() {
        val home = place("home", -6.1754, 106.8272, 200)
        val work = place("work", -6.1755, 106.8273, 100)
        assertEquals(home, listOf(home, work).placeAt(-6.1755, 106.8273))
    }
}
