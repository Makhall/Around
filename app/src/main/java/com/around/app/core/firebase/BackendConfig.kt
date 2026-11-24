package com.around.app.core.firebase

import com.around.app.BuildConfig

object BackendConfig {
    /** Host for the Firebase emulators. Override with -ParoundEmulatorHost. */
    val EMULATOR_HOST: String = BuildConfig.EMULATOR_HOST
}
