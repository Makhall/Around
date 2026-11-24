package com.around.app.data.sharing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sharingDataStore by preferencesDataStore(name = "sharing")

data class SharingSnapshot(
    val enabled: Boolean,
    val pausedUntil: Long,
)

@Singleton
class SharingPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val sharingEnabled: Flow<Boolean> = context.sharingDataStore.data
        .map { it[SHARING_ENABLED] ?: false }

    val pausedUntil: Flow<Long> = context.sharingDataStore.data
        .map { it[PAUSED_UNTIL] ?: 0L }

    suspend fun snapshot(): SharingSnapshot = context.sharingDataStore.data
        .first()
        .let {
            SharingSnapshot(
                enabled = it[SHARING_ENABLED] ?: false,
                pausedUntil = it[PAUSED_UNTIL] ?: 0L,
            )
        }

    suspend fun setSharingEnabled(enabled: Boolean) {
        context.sharingDataStore.edit { it[SHARING_ENABLED] = enabled }
    }

    suspend fun setPausedUntil(until: Long) {
        context.sharingDataStore.edit { it[PAUSED_UNTIL] = until }
    }

    private companion object {
        val SHARING_ENABLED = booleanPreferencesKey("sharing_enabled")
        val PAUSED_UNTIL = longPreferencesKey("paused_until")
    }
}
