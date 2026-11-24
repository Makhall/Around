package com.around.app.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.around.app.R
import kotlinx.coroutines.delay

enum class RelativeTimeUnit {
    NONE,
    JUST_NOW,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
}

data class RelativeTime(
    val unit: RelativeTimeUnit,
    val value: Long = 0L,
)

fun relativeTime(timestamp: Long, now: Long): RelativeTime {
    if (timestamp <= 0L) {
        return RelativeTime(RelativeTimeUnit.NONE)
    }
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    return when {
        elapsed < 10_000L -> RelativeTime(RelativeTimeUnit.JUST_NOW)
        elapsed < 60_000L -> RelativeTime(RelativeTimeUnit.SECONDS, elapsed / 1_000L)
        elapsed < 60 * 60_000L -> RelativeTime(RelativeTimeUnit.MINUTES, elapsed / 60_000L)
        elapsed < 24 * 60 * 60_000L -> RelativeTime(RelativeTimeUnit.HOURS, elapsed / (60 * 60_000L))
        else -> RelativeTime(RelativeTimeUnit.DAYS, elapsed / (24 * 60 * 60_000L))
    }
}

@Composable
fun rememberRelativeTime(timestamp: Long, refreshMs: Long = 15_000L): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(timestamp) {
        now = System.currentTimeMillis()
        while (true) {
            delay(refreshMs)
            now = System.currentTimeMillis()
        }
    }

    return relativeTimeText(relativeTime(timestamp, now))
}

@Composable
fun relativeTimeText(relative: RelativeTime): String = when (relative.unit) {
    RelativeTimeUnit.NONE -> stringResource(R.string.map_no_location)
    RelativeTimeUnit.JUST_NOW -> stringResource(R.string.map_just_now)
    RelativeTimeUnit.SECONDS -> stringResource(R.string.map_seconds_ago, relative.value)
    RelativeTimeUnit.MINUTES -> stringResource(R.string.map_minutes_ago, relative.value)
    RelativeTimeUnit.HOURS -> stringResource(R.string.map_hours_ago, relative.value)
    RelativeTimeUnit.DAYS -> stringResource(R.string.map_days_ago, relative.value)
}
