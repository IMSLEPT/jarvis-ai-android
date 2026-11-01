package it.jarvis.assistant

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import it.jarvis.assistant.core.Msg
import it.jarvis.assistant.ui.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeRegressionTest {
    @get:Rule val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun reset() { ui.runOnIdle { ui.activity.graph.core.chat.value = emptyList(); ui.activity.graph.core.error.value = null } }

    @Test fun composerRemainsVisibleWhileTyping() {
        screenshot("home")
        ui.onNode(hasSetTextAction()).performClick().performTextInput("Messaggio scritto di prova")
        ui.runOnIdle {
            val activity = ui.activity
            val focus = activity.currentFocus ?: error("Campo di input senza focus")
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(focus, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        ui.waitUntil(10_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        ui.onNodeWithText("Messaggio scritto di prova").assertIsDisplayed()
        ui.onNodeWithContentDescription("Invia comando").assertIsDisplayed()
        screenshot("chat-keyboard")
    }
    @Test fun longConversationScrollsToLastMessage() {
        ui.runOnIdle { ui.activity.graph.core.chat.value = List(25) { Msg(if (it % 2 == 0) "user" else "jarvis", "Messaggio numero $it") } }
        ui.waitForIdle()
        ui.onNodeWithText("Messaggio numero 24").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).assertIsDisplayed()
        screenshot("conversation")
    }
    @Test fun microphoneWithoutModelsShowsErrorInsteadOfCrashing() {
        ui.activity.graph.models.remove(it.jarvis.assistant.models.ModelCatalog.vad)
        ui.onNodeWithContentDescription("Parla con Jarvis").performClick()
        ui.waitUntil(10_000) { ui.activity.graph.core.error.value != null }
        ui.onNode(hasSetTextAction()).performClick().performTextInput("La chat funziona ancora")
        ui.onNodeWithText("La chat funziona ancora").assertIsDisplayed()
    }
    @Test fun settingsAndBackNavigationRemainAvailable() {
        ui.onNodeWithContentDescription("Impostazioni e strumenti").performClick()
        ui.onNodeWithText("Permessi", substring = false).performClick()
        ui.onNodeWithText("Configura il tuo assistente").assertIsDisplayed()
        screenshot("permissions")
        ui.onNodeWithContentDescription("Indietro").performClick()
        ui.onNodeWithText("Menu", substring = false).assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Download resta disponibile anche quando Gradle disinstalla l'app al termine dei test.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/Download/jarvis-screenshots")).use { it.readBytes() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /sdcard/Download/jarvis-screenshots/$name.png")).use { it.readBytes() }
    }
}
