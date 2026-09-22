package murat.com.saasproject.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import murat.com.saasproject.domain.config.BusinessPaymentConfig
import murat.com.saasproject.domain.config.KvkkPolicy
import java.util.Calendar
import java.util.Date

/**
 * iOS `BusinessModel.swift` karşılığı — Firestore `businesses/{uid}`.
 *
 * Alan adları iOS ile birebir aynı olmak zorunda; iOS ve Android aynı dokümanı okur/yazar.
 */
data class Business(
    val id: String,
    val name: String,
    val logoURL: String?,
    val phone: String,
    val email: String,
    val businessType: String,
    val createdAt: Date,
    val rewards: List<String> = emptyList(),
    val kvkkAcceptedAt: Date? = null,
    val kvkkPolicyVersion: String? = null,
    val isActive: Boolean = false,
    val subscriptionExpiresAt: Date? = null,
    val pointEarningRules: String? = null
) {

    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name)
        put("logoURL", logoURL.orEmpty())
        put("phone", phone)
        put("email", email)
        put("businessType", businessType)
        put("createdAt", Timestamp(createdAt))
        put("rewards", rewards)
        put("isActive", isActive)
        kvkkAcceptedAt?.let { put("kvkkAcceptedAt", Timestamp(it)) }
        kvkkPolicyVersion?.let { put("kvkkPolicyVersion", it) }
        subscriptionExpiresAt?.let { put("subscriptionExpiresAt", Timestamp(it)) }
        // iOS bu alanı her zaman yazar (null ise boş string).
        put("pointEarningRules", pointEarningRules.orEmpty())
    }

    /** iOS `displayPointEarningRules` — boş/whitespace ise null. */
    val displayPointEarningRules: String?
        get() = normalizePointEarningRules(pointEarningRules)

    /**
     * iOS `isSubscriptionValid`: gün başlangıcına normalize edilip DAHİL karşılaştırılır.
     * Aboneliği bugün bitiyorsa hâlâ geçerli sayılır.
     */
    val isSubscriptionValid: Boolean
        get() {
            val expiry = subscriptionExpiresAt ?: return false
            return !startOfDay(expiry).before(startOfDay(Date()))
        }

    /** iOS `remainingDays` — bugünden bitiş gününe kalan tam gün sayısı. */
    val remainingDays: Int?
        get() {
            val expiry = subscriptionExpiresAt ?: return null
            val diffMillis = startOfDay(expiry).time - startOfDay(Date()).time
            return Math.round(diffMillis / MILLIS_PER_DAY.toDouble()).toInt()
        }

    fun withSubscriptionExpiresAt(date: Date?): Business = copy(subscriptionExpiresAt = date)

    fun withPointEarningRules(rules: String?): Business =
        copy(pointEarningRules = normalizePointEarningRules(rules))

    /**
     * iOS `subscriptionExpiryByAddingDays`.
     * Temel tarih = max(bugün, mevcut bitiş); üzerine [days] eklenir.
     * Uzatma gelecekteki bitişten, kısaltma da aynı tabandan hesaplanır.
     */
    fun subscriptionExpiryByAddingDays(days: Int): Date {
        val today = startOfDay(Date())
        val base = subscriptionExpiresAt?.let { expiry ->
            val expiryDay = startOfDay(expiry)
            if (expiryDay.after(today)) expiryDay else today
        } ?: today
        return addDays(base, days)
    }

    /** iOS `cancelledSubscriptionExpiry` — dünün gün başı. */
    fun cancelledSubscriptionExpiry(): Date = addDays(startOfDay(Date()), -1)

    /**
     * iOS `ensuringDefaultSubscriptionIfNeeded()` — abonelik tarihi olmayan eski kayıtlara
     * `createdAt + 30 gün` deneme süresi backfill eder ve hesabı aktifleştirir.
     */
    fun ensuringDefaultSubscriptionIfNeeded(): Business {
        if (subscriptionExpiresAt != null) return this
        return copy(
            subscriptionExpiresAt = defaultTrialEndDate(createdAt),
            isActive = true
        )
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        fun normalizePointEarningRules(raw: String?): String? =
            raw?.trim()?.takeIf { it.isNotEmpty() }

        fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        /** iOS `defaultTrialEndDate` — başlangıç + `BusinessPaymentConfig.trialDays`. */
        fun defaultTrialEndDate(from: Date = Date()): Date = Calendar.getInstance().apply {
            time = from
            add(Calendar.DAY_OF_YEAR, BusinessPaymentConfig.TRIAL_DAYS)
        }.time

        fun addDays(to: Date, days: Int): Date = Calendar.getInstance().apply {
            time = to
            add(Calendar.DAY_OF_YEAR, days)
        }.time

        /**
         * iOS `Business.forRegistration` — yeni işletme kaydı.
         * İsim çağıran tarafta uppercase'lenir (iOS `AdminSignUpViewModel` davranışı).
         */
        fun forRegistration(
            id: String,
            name: String,
            phone: String,
            email: String,
            businessType: String,
            kvkkAcceptedAt: Date = Date(),
            kvkkPolicyVersion: String = KvkkPolicy.VERSION
        ): Business {
            val createdAt = Date()
            return Business(
                id = id,
                name = name,
                logoURL = "",
                phone = phone,
                email = email,
                businessType = businessType,
                createdAt = createdAt,
                rewards = emptyList(),
                kvkkAcceptedAt = kvkkAcceptedAt,
                kvkkPolicyVersion = kvkkPolicyVersion,
                isActive = true,
                subscriptionExpiresAt = defaultTrialEndDate(createdAt),
                pointEarningRules = null
            )
        }

        /**
         * iOS `init?(dictionary:)` ile aynı zorunlu alanlar: id, name, phone, email, businessType.
         * Eksikse null döner (bozuk doküman atlanır).
         */
        fun fromMap(data: Map<String, Any?>, documentId: String? = null): Business? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: documentId ?: return null
            val name = data["name"] as? String ?: return null
            val phone = data["phone"] as? String ?: return null
            val email = data["email"] as? String ?: return null
            val businessType = data["businessType"] as? String ?: return null

            return Business(
                id = id,
                name = name,
                logoURL = data["logoURL"] as? String,
                phone = phone,
                email = email,
                businessType = businessType,
                createdAt = data.dateOrNow("createdAt"),
                rewards = (data["rewards"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                kvkkAcceptedAt = data.dateOrNull("kvkkAcceptedAt"),
                kvkkPolicyVersion = data["kvkkPolicyVersion"] as? String,
                isActive = data["isActive"] as? Boolean ?: false,
                subscriptionExpiresAt = data.dateOrNull("subscriptionExpiresAt"),
                pointEarningRules = normalizePointEarningRules(data["pointEarningRules"] as? String)
            )
        }

        fun fromSnapshot(snapshot: DocumentSnapshot): Business? {
            val data = snapshot.data ?: return null
            return fromMap(data, snapshot.id)
        }
    }
}

internal fun Map<String, Any?>.dateOrNull(key: String): Date? = when (val value = this[key]) {
    is Timestamp -> value.toDate()
    is Date -> value
    else -> null
}

internal fun Map<String, Any?>.dateOrNow(key: String): Date = dateOrNull(key) ?: Date()
