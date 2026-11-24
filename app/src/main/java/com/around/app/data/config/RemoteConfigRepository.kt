package com.around.app.data.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteSettings(
    val mapStyleUrl: String,
    val sharingEnabled: Boolean,
)

interface AppConfigRepository {
    suspend fun load(): RemoteSettings
}

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : AppConfigRepository {

    override suspend fun load(): RemoteSettings {
        return runCatching {
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "map_style_url" to DEFAULT_MAP_STYLE_URL,
                    "sharing_enabled" to true,
                ),
            ).await()
            remoteConfig.fetchAndActivate().await()
            RemoteSettings(
                mapStyleUrl = remoteConfig.getString("map_style_url")
                    .ifBlank { DEFAULT_MAP_STYLE_URL },
                sharingEnabled = remoteConfig.getBoolean("sharing_enabled"),
            )
        }.getOrElse {
            RemoteSettings(
                mapStyleUrl = DEFAULT_MAP_STYLE_URL,
                sharingEnabled = true,
            )
        }
    }

    companion object {
        const val DEFAULT_MAP_STYLE_URL =
            "https://tiles.openfreemap.org/styles/liberty"
    }
}
