package it.jarvis.assistant.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Normalizer

data class ContactMatch(val name: String, val phone: String)
/** Solo numeri reali della rubrica concessa dall'utente; mai inferire il numero di un nome. */
object ContactResolver {
    fun normalize(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").trim().replace(Regex("\\s+"), " ")
    fun phone(raw: String): String? {
        if (raw.any { it.isLetter() }) return null
        return PhoneNumberUtils.formatNumberToE164(raw.trim(), "IT")?.removePrefix("+")?.takeIf { it.length in 8..15 }
    }
    fun find(ctx: Context, name: String): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val query = normalize(name)
        if (query.isBlank()) return emptyList()
        val candidates = mutableListOf<ContactMatch>()
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val label = c.getString(0).orEmpty()
                val normalized = normalize(label)
                if (normalized == query || normalized.split(' ').containsAll(query.split(' '))) {
                    phone(c.getString(1).orEmpty())?.let { candidates += ContactMatch(label, it) }
                }
            }
        }
        val exact = candidates.filter { normalize(it.name) == query }
        return (exact.ifEmpty { candidates }).distinctBy { it.phone }
    }
}
