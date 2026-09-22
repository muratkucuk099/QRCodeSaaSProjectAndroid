package murat.com.saasproject.domain.config

/**
 * iOS `MainAdminConfig.swift` karşılığı.
 * Ana admin UID'si Firestore security rules içinde de hardcoded olduğu için değiştirilemez.
 */
object MainAdminConfig {
    const val UID = "puDrOcA8HLae65OChJu1N8j11lc2"
    const val SUBSCRIPTION_EXTEND_DAYS = 30
    const val SUBSCRIPTION_SHORTEN_DAYS = 30
}

/** iOS `BusinessPaymentConfig.swift` karşılığı. */
object BusinessPaymentConfig {
    const val FALLBACK_PHONE = "0552 554 50 09"
    const val FALLBACK_PRICE = "—"
    const val TRIAL_DAYS = 30
}

/** iOS `KVKKPolicy.swift` karşılığı. Versiyon Firestore'a yazıldığı için iOS ile aynı kalmalı. */
object KvkkPolicy {
    const val APP_NAME = "kumbaram"
    const val VERSION = "1.0"

    /** iOS `KVKKPolicy.dataControllerTitle` — yayın öncesi tüzel kişi adıyla güncellenmeli. */
    const val DATA_CONTROLLER_TITLE = "kumbaram"

    /** KVKK başvuru ve talep e-postası. */
    const val CONTACT_EMAIL = "destek@kumbaram"

    /** iOS `AppSupportInfo.urlString`. */
    const val SUPPORT_URL = "https://muratkucuk099.github.io"
}

/** iOS `AuthEmailConfig.swift` karşılığı. */
object AuthEmailConfig {
    const val PREFERRED_LANGUAGE_CODE = "tr"
    const val PASSWORD_RESET_CONTINUE_URL = "https://saasproject-23a1f.web.app/sifre-sifirla"
}

/** iOS `FirebaseService.performCloudRequest` ile aynı bölge ve proje. */
object CloudFunctionsConfig {
    const val REGION = "us-central1"
    const val PROJECT_ID = "saasproject-23a1f"
    const val BASE_URL = "https://$REGION-$PROJECT_ID.cloudfunctions.net/"

    const val SYNC_FCM_TOKEN = "syncFCMToken"
    const val SEND_BUSINESS_PUSH = "sendBusinessPush"
    const val CREATE_INVITE_CODE = "createInviteCode"
    const val VALIDATE_INVITE_CODE = "validateInviteCode"
    const val SEND_PASSWORD_RESET_EMAIL = "sendPasswordResetEmail"
}

/** iOS `FirebaseService.createActiveQRCode` / `AdminMainViewController` sabitleri. */
object QrConfig {
    /** Firestore `active_qr_codes.expiresAt` = üretim + 10 dakika. */
    const val VALIDITY_MINUTES = 10L

    /** İşletme ekranında QR'ın görünür kaldığı geri sayım (saniye). */
    const val DISPLAY_COUNTDOWN_SECONDS = 10
}

/** Firestore koleksiyon ve alan adları. iOS ile birebir aynı olmak zorunda. */
object FirestorePaths {
    const val USERS = "users"
    const val BUSINESSES = "businesses"
    const val REWARDS = "rewards"
    const val ACTIVE_QR_CODES = "active_qr_codes"
    const val APP_CONFIG = "app_config"
    const val APP_CONFIG_PUBLIC_DOC = "public"

    /**
     * Sunucu tarafı koleksiyon. Firestore rules istemci okuma/yazmasını kapatır.
     * Oluşturma ve doğrulama yalnızca Cloud Function üzerinden yapılır; bu sabiti
     * repository'lerde yazma için kullanma.
     */
    const val INVITE_CODES = "invite_codes"

    // Alt koleksiyonlar
    const val SUB_USERS = "users"
    const val SUB_POINT_LOGS = "point_logs"
    const val SUB_REWARD_LOGS = "reward_logs"
    const val SUB_NOTIFICATIONS = "notifications"
    const val SUB_FCM_TOKENS = "fcmTokens"
}

/** Firebase Storage yolları. iOS ile aynı. */
object StoragePaths {
    const val BUSINESS_LOGOS = "businessLogos"
    const val REWARDS = "rewards"
    /** Eski/kullanılmayan yükleme yolu — yalnızca hesap silme temizliğinde taranır. */
    const val REWARD_IMAGES = "rewardImages"
    const val LOGO_FILE = "logo.jpg"

    /** iOS `AdminProfileViewModel` logoyu 0.7, `CreateRewardViewModel` ödülü 0.8 kalitede yükler. */
    const val LOGO_JPEG_QUALITY = 70
    const val REWARD_JPEG_QUALITY = 80
}
