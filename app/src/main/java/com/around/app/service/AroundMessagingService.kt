package com.around.app.service

import com.around.app.data.devices.DeviceRegistrar
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AroundMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceRegistrar: DeviceRegistrar

    override fun onNewToken(token: String) {
        deviceRegistrar.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["kind"] == "place_alert") {
            PlaceAlertNotifier.show(this, data)
        }
    }
}
