package com.around.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.around.app.core.designsystem.theme.AroundTheme
import com.around.app.core.navigation.AroundNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingCircleId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingCircleId = consumeCircleId(intent)
        }
        setContent {
            AroundTheme {
                AroundNavHost(
                    notificationCircleId = pendingCircleId,
                    onNotificationCircleHandled = { pendingCircleId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCircleId = consumeCircleId(intent)
    }

    private fun consumeCircleId(intent: Intent?): String? {
        val circleId = intent?.getStringExtra(EXTRA_CIRCLE_ID)
        intent?.removeExtra(EXTRA_CIRCLE_ID)
        return circleId
    }

    private companion object {
        const val EXTRA_CIRCLE_ID = "circleId"
    }
}
