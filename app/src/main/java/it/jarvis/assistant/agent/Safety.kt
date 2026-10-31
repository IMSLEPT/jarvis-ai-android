package it.jarvis.assistant.agent

/** Politica di sicurezza: nessuna operazione sensibile senza conferma, blocchi assoluti per pagamenti e prenotazioni. */
object Safety {
    private val sensitive = Regex(
        "(?i)\\b(invia|inviare|send|paga|pagare|pay|acquista|acquisto|compra|buy|purchase|ordina|order|prenota|prenotazione|book|reserve|conferma|confirm|elimina|cancella|delete|remove|rimuovi|disinstalla|uninstall|bonifico|transfer|trasferisci|accetto|accept|consenti|allow|chiama|call|condividi|share|pubblica|post|logout|esci)\\b"
    )
    // Su queste app/azioni non si va oltre la ricerca.
    private val hardBlockPackagesPrefix = listOf("com.booking")
    private val hardBlockLabels = Regex("(?i)\\b(prenota|prenotazione|reserve|book now|paga|pay now|pay|completa|complete booking|conferma prenotazione|inserisci carta|numero carta|card number)\\b")
    private val payPackages = listOf("com.google.android.apps.walletnfcrel", "com.paypal", "com.android.vending")

    fun isSensitiveLabel(label: String?): Boolean = label != null && sensitive.containsMatchIn(label)

    /** @return motivo del blocco assoluto o null */
    fun hardBlock(pkg: String, label: String?): String? {
        if (label == null) return null
        if (hardBlockPackagesPrefix.any { pkg.startsWith(it) } && hardBlockLabels.containsMatchIn(label))
            return "Non posso prenotare né pagare: mi fermo alla ricerca."
        if (payPackages.any { pkg.startsWith(it) } && sensitive.containsMatchIn(label))
            return "Non eseguo pagamenti o acquisti."
        return null
    }

    fun parseYesNo(answer: String?): Boolean? {
        val a = answer?.lowercase()?.replace(Regex("[^a-zàèéìòù ]"), " ")?.trim() ?: return null
        val yes = listOf("sì", "si", "certo", "ok", "va bene", "conferma", "confermo", "procedi", "invia", "esatto", "yes", "vai")
        val no = listOf("no", "annulla", "stop", "fermati", "non", "aspetta", "ferma", "basta", "negativo")
        if (no.any { a == it || a.startsWith("$it ") || a.contains(" $it ") }) return false
        if (yes.any { a == it || a.startsWith("$it ") || a.contains(" $it ") }) return true
        return null
    }
}
