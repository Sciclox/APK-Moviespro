package com.animetv.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.animetv.app.navigation.AnimeTvApp

class MainActivity : ComponentActivity() {
    private var currentActivityIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentActivityIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("AnimeTVCrash", "EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val file = java.io.File(filesDir, "crash_log.txt")
                file.writeText("Crash: ${throwable.stackTraceToString()}")
            } catch (_: Exception) {}

            val isNonFatal = throwable is android.content.ActivityNotFoundException ||
                    throwable is NullPointerException ||
                    throwable is IllegalStateException ||
                    throwable.message?.contains("ActivityNotFoundException") == true ||
                    throwable.message?.contains("WebView") == true ||
                    throwable.message?.contains("media3") == true

            if (!isNonFatal) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        installSplashScreen()
        super.onCreate(savedInstanceState)
        currentActivityIntent = intent
        setContent {
            AnimeTvApp(activityIntent = currentActivityIntent)
        }
    }
}
