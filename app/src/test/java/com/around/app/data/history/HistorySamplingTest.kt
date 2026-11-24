package com.around.app.data.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySamplingTest {

    private val now = 1_700_000_000_000L
    private val latitude = -6.1754
    private val longitude = 106.8272

    @Test
    fun `the first point is always sampled`() {
        assertTrue(
            shouldSampleHistory(
                lastTimestamp = 0L,
                lastLatitude = 0.0,
                lastLongitude = 0.0,
                now = now,
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    @Test
    fun `a point is sampled after the interval`() {
        assertTrue(
            shouldSampleHistory(
                lastTimestamp = now - HISTORY_SAMPLE_INTERVAL_MS,
                lastLatitude = latitude,
                lastLongitude = longitude,
                now = now,
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    @Test
    fun `stationary points inside the interval are skipped`() {
        assertFalse(
            shouldSampleHistory(
                lastTimestamp = now - 10_000L,
                lastLatitude = latitude,
                lastLongitude = longitude,
                now = now,
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    @Test
    fun `moving enough inside the interval is sampled`() {
        assertTrue(
            shouldSampleHistory(
                lastTimestamp = now - 10_000L,
                lastLatitude = latitude,
                lastLongitude = longitude,
                now = now,
                latitude = latitude + 0.001,
                longitude = longitude,
            ),
        )
    }

    @Test
    fun `small movement inside the interval is skipped`() {
        assertFalse(
            shouldSampleHistory(
                lastTimestamp = now - 10_000L,
                lastLatitude = latitude,
                lastLongitude = longitude,
                now = now,
                latitude = latitude + 0.00001,
                longitude = longitude,
            ),
        )
    }
}
