package com.around.app.core.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceIdsTest {

    @Test
    fun `round trips circle and place ids`() {
        val id = GeofenceIds.requestId("circle-1", "place-2")
        assertEquals("circle-1" to "place-2", GeofenceIds.parse(id))
    }

    @Test
    fun `rejects malformed ids`() {
        assertNull(GeofenceIds.parse("circle-only"))
        assertNull(GeofenceIds.parse("|place"))
        assertNull(GeofenceIds.parse("circle|"))
    }
}
