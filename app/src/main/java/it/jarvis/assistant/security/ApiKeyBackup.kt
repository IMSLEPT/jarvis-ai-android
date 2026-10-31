package it.jarvis.assistant.security

import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Contenuto in chiaro del backup: chiavi API + configurazione provider (nessun dato vocale o memoria). */
data class BackupPayload(
    val keys: Map<String, String>,
    val models: Map<String, String>,
    val baseUrls: Map<String, String>,
)

class BackupException(msg: String) : Exception(msg)

/**
 * Formato: JSON { app, version, kdf, iterations, salt, iv, data } con data = AES-256-GCM(JSON payload),
 * chiave derivata dalla passphrase con PBKDF2-HMAC-SHA256 (310.000 iterazioni). Senza passphrase il file è inutile.
 */
object ApiKeyBackup {
    private const val ITER = 310_000
    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    private fun derive(pass: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(pass, salt, iter, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(payload: BackupPayload, passphrase: CharArray): String {
        require(passphrase.size >= 8) { "La passphrase deve avere almeno 8 caratteri" }
        val plain = JSONObject().apply {
            put("keys", JSONObject(payload.keys))
            put("models", JSONObject(payload.models))
            put("baseUrls", JSONObject(payload.baseUrls))
        }.toString().toByteArray(Charsets.UTF_8)
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt, ITER), GCMParameterSpec(128, iv))
        c.updateAAD("jarvis-ai-backup-v1".toByteArray())
        val ct = c.doFinal(plain)
        return JSONObject().apply {
            put("app", "jarvis-ai"); put("version", 1)
            put("kdf", "PBKDF2WithHmacSHA256"); put("iterations", ITER)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("data", b64(ct))
        }.toString(2)
    }

    fun decrypt(fileText: String, passphrase: CharArray): BackupPayload {
        val j = try { JSONObject(fileText) } catch (e: Exception) { throw BackupException("File non valido") }
        if (j.optString("app") != "jarvis-ai") throw BackupException("Non è un backup di Jarvis AI")
        try {
            val salt = unb64(j.getString("salt"))
            val iv = unb64(j.getString("iv"))
            val data = unb64(j.getString("data"))
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, derive(passphrase, salt, j.getInt("iterations")), GCMParameterSpec(128, iv))
            c.updateAAD("jarvis-ai-backup-v1".toByteArray())
            val plain = JSONObject(String(c.doFinal(data), Charsets.UTF_8))
            fun map(n: String): Map<String, String> {
                val o = plain.optJSONObject(n) ?: return emptyMap()
                return o.keys().asSequence().associateWith { o.getString(it) }
            }
            return BackupPayload(map("keys"), map("models"), map("baseUrls"))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw BackupException("Passphrase errata o file danneggiato")
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Backup illeggibile: ${e.message}")
        }
    }
}
