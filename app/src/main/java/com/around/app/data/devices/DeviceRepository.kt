package com.around.app.data.devices

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface DeviceRepository {
    suspend fun registerDevice(uid: String, token: String): Result<Unit>
}

@Singleton
class FirebaseDeviceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context,
) : DeviceRepository {

    override suspend fun registerDevice(uid: String, token: String): Result<Unit> =
        runCatching {
            firestore.collection("users")
                .document(uid)
                .collection("devices")
                .document(deviceId())
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "model" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        "osVersion" to Build.VERSION.RELEASE,
                        "locale" to Locale.getDefault().language,
                        "lastSeen" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        }

    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return androidId?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
