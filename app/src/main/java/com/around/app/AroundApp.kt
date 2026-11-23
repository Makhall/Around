package com.around.app

import android.app.Application
import com.around.app.core.firebase.BackendConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AroundApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            Firebase.auth.useEmulator(BackendConfig.EMULATOR_HOST, 9099)
            Firebase.firestore.useEmulator(BackendConfig.EMULATOR_HOST, 8080)
            Firebase.database.useEmulator(BackendConfig.EMULATOR_HOST, 9000)
            Firebase.functions(BackendConfig.FUNCTIONS_REGION)
                .useEmulator(BackendConfig.EMULATOR_HOST, 5001)
        }
    }
}
