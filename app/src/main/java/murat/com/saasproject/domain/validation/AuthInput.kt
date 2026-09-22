package murat.com.saasproject.domain.validation

/** iOS `AuthEmailInput.swift` — e-postayı normalize eder (trim + boşluk temizliği + küçük harf). */
object AuthEmailInput {
    fun normalize(raw: String): String = raw.trim().replace(" ", "").lowercase()

    /**
     * iOS `AdminLoginViewModel.validatePasswordResetEmail` ile aynı gevşek kontrol:
     * yalnızca `@` ve `.` varlığı aranır (tam regex doğrulaması yapılmaz).
     */
    fun isPlausible(email: String): Boolean = email.contains("@") && email.contains(".")
}

/**
 * iOS `UserDisplayName.swift` — kullanıcı adının gösterilecek hâlini çözer.
 * OAuth kaydında sağlayıcıdan gelen ad yoksa e-posta ön ekine düşer.
 */
object UserDisplayName {
    const val PLACEHOLDER = "Kullanıcı"

    private fun emailPrefix(email: String?): String? =
        email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }

    fun normalizedProfileName(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** Depolanan ad anlamlı değilse e-posta ön eki, o da yoksa placeholder. */
    fun display(storedName: String?, email: String?): String {
        val trimmed = storedName?.trim()
        if (!trimmed.isNullOrEmpty() && trimmed != PLACEHOLDER) return trimmed
        return emailPrefix(email) ?: PLACEHOLDER
    }

    /** iOS `resolveForOAuthRegistration` — sağlayıcı adı > Firebase displayName > e-posta ön eki. */
    fun resolveForOAuthRegistration(
        providerName: String?,
        firebaseDisplayName: String?,
        email: String?
    ): String = normalizedProfileName(providerName)
        ?: normalizedProfileName(firebaseDisplayName)
        ?: emailPrefix(email)
        ?: PLACEHOLDER

    /**
     * Kayıtlı adın "gerçek" bir ad olmadığını tespit eder; OAuth girişinde sağlayıcıdan
     * gelen gerçek adla değiştirilmesi gerekip gerekmediğine karar vermek için kullanılır.
     */
    fun isPlaceholder(name: String?, email: String?): Boolean {
        val trimmed = name?.trim()
        if (trimmed.isNullOrEmpty()) return true
        if (trimmed == PLACEHOLDER) return true
        return trimmed == emailPrefix(email)
    }
}

/** iOS `InviteCodeGenerator.swift` — okunabilirlik için I/O/0/1 hariç alfabe. */
object InviteCodeGenerator {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val DEFAULT_LENGTH = 14

    fun generate(length: Int = DEFAULT_LENGTH): String =
        (1..length).map { ALPHABET.random() }.joinToString("")

    /** Kullanıcının girdiği kodu sunucunun beklediği biçime getirir (trim + uppercase). */
    fun normalize(raw: String): String = raw.trim().uppercase()
}

/**
 * iOS `WhatsAppLink.swift` — abonelik ödemesi için WhatsApp sohbeti açar.
 * Telefon normalizasyonu iOS ile aynı: sadece rakamlar, `0` ile başlıyorsa `90` eklenir.
 */
object WhatsAppLink {
    const val DEFAULT_MESSAGE = "merhaba"

    fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("90") -> digits
            digits.startsWith("0") -> "90" + digits.drop(1)
            else -> digits
        }
    }

    fun chatUrl(phone: String, message: String = DEFAULT_MESSAGE): String =
        "https://wa.me/${normalizePhone(phone)}?text=${android.net.Uri.encode(message)}"
}
