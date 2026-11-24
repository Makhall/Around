package com.around.app.data.devices

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class DeviceRegistrar @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentDevice() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                deviceRepository.registerDevice(uid, token)
            }.onFailure { error ->
                Log.w(TAG, "Could not register FCM device", error)
            }
        }
    }

    fun registerToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            deviceRepository.registerDevice(uid, token)
                .onFailure { error ->
                    Log.w(TAG, "Could not store refreshed FCM token", error)
                }
        }
    }

    private companion object {
        const val TAG = "DeviceRegistrar"
    }
}
