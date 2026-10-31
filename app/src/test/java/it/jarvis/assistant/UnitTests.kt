package it.jarvis.assistant

import it.jarvis.assistant.agent.Safety
import it.jarvis.assistant.security.ApiKeyBackup
import it.jarvis.assistant.security.BackupException
import it.jarvis.assistant.security.BackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafetyTest {
    @Test fun sensitiveLabels() {
        assertTrue(Safety.isSensitiveLabel("Invia"))
        assertTrue(Safety.isSensitiveLabel("Paga ora"))
        assertTrue(Safety.isSensitiveLabel("Elimina"))
        assertFalse(Safety.isSensitiveLabel("Cerca"))
        assertFalse(Safety.isSensitiveLabel(null))
    }
    @Test fun bookingIsHardBlocked() {
        assertNotNull(Safety.hardBlock("com.booking", "Prenota ora"))
        assertNotNull(Safety.hardBlock("com.booking", "Paga"))
        assertNull(Safety.hardBlock("com.booking", "Cerca"))
        assertNull(Safety.hardBlock("com.whatsapp", "Invia"))
    }
    @Test fun yesNo() {
        assertEquals(true, Safety.parseYesNo("Sì, certo"))
        assertEquals(true, Safety.parseYesNo("ok"))
        assertEquals(false, Safety.parseYesNo("no annulla"))
        assertEquals(false, Safety.parseYesNo("Stop"))
        assertNull(Safety.parseYesNo("forse domani"))
        assertNull(Safety.parseYesNo(null))
    }
}

class BackupCryptoTest {
    private val payload = BackupPayload(mapOf("groq" to "gsk_test_123"), mapOf("groq" to "qwen/qwen3.8-27b"), mapOf("groq" to "https://api.groq.com/openai/v1"))

    @Test fun roundTrip() {
        val enc = ApiKeyBackup.encrypt(payload, "passphrase-lunga".toCharArray())
        assertFalse(enc.contains("gsk_test_123"))
        val dec = ApiKeyBackup.decrypt(enc, "passphrase-lunga".toCharArray())
        assertEquals("gsk_test_123", dec.keys["groq"])
        assertEquals("qwen/qwen3.8-27b", dec.models["groq"])
    }

    @Test fun wrongPassphraseFails() {
        val enc = ApiKeyBackup.encrypt(payload, "passphrase-lunga".toCharArray())
        try { ApiKeyBackup.decrypt(enc, "sbagliata-sbagliata".toCharArray()); fail("doveva fallire") } catch (e: BackupException) { /* ok */ }
    }

    @Test fun shortPassphraseRejected() {
        try { ApiKeyBackup.encrypt(payload, "corta".toCharArray()); fail("doveva fallire") } catch (e: IllegalArgumentException) { /* ok */ }
    }

    @Test fun notABackup() {
        try { ApiKeyBackup.decrypt("{\"x\":1}", "qualunque-pass".toCharArray()); fail() } catch (e: BackupException) { /* ok */ }
    }
}
