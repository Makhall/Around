package com.around.app.feature.map

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `missing timestamps are empty`() {
        assertEquals(RelativeTimeUnit.NONE, relativeTime(0L, now).unit)
        assertEquals(RelativeTimeUnit.NONE, relativeTime(-5L, now).unit)
    }

    @Test
    fun `recent updates are just now`() {
        assertEquals(RelativeTimeUnit.JUST_NOW, relativeTime(now - 5_000L, now).unit)
    }

    @Test
    fun `units step from seconds to days`() {
        assertEquals(
            RelativeTime(RelativeTimeUnit.SECONDS, 30L),
            relativeTime(now - 30_000L, now),
        )
        assertEquals(
            RelativeTime(RelativeTimeUnit.MINUTES, 5L),
            relativeTime(now - 5 * 60_000L, now),
        )
        assertEquals(
            RelativeTime(RelativeTimeUnit.HOURS, 3L),
            relativeTime(now - 3 * 60 * 60_000L, now),
        )
        assertEquals(
            RelativeTime(RelativeTimeUnit.DAYS, 2L),
            relativeTime(now - 2 * 24 * 60 * 60_000L, now),
        )
    }

    @Test
    fun `future timestamps are clamped`() {
        assertEquals(RelativeTimeUnit.JUST_NOW, relativeTime(now + 30_000L, now).unit)
    }
}
