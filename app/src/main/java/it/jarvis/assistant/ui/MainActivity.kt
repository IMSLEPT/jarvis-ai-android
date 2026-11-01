package it.jarvis.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import it.jarvis.assistant.graph

class MainActivity : ComponentActivity() {
    override fun onResume() { super.onResume(); graph.core.appVisible.value = true }
    override fun onPause() { graph.core.appVisible.value = false; super.onPause() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val g = graph
        setContent {
            val tick by g.settings.tick.collectAsState()
            @Suppress("UNUSED_EXPRESSION") tick
            JarvisTheme(g.settings.themeMode) {
                Box(Modifier.fillMaxSize().background(screenBrush())) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    var screen by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = if (screen == "menu") "home" else "menu" }
    when (screen) {
        "home" -> HomeScreen(onMenu = { screen = "menu" }, go = { screen = it })
        "menu" -> MenuScreen(onBack = { screen = "home" }, go = { screen = it })
        else -> SubScreen(screen, onBack = { screen = "menu" }, go = { screen = it })
    }
}

fun android.content.Context.hasMic() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
