package murat.com.saasproject.data.model

import com.google.firebase.auth.AuthCredential

/**
 * Google hesap seçicisinin sonucu.
 *
 * [Cancelled] kullanıcı vazgeçtiğinde döner ve hata olarak gösterilmez —
 * iOS'ta `GIDSignInError.canceled` de sessizce yutuluyor.
 */
sealed interface GoogleSignInOutcome {

    data class Success(
        val credential: AuthCredential,
        /** Sağlayıcıdan gelen görünen ad; Firestore'daki placeholder adı düzeltmek için. */
        val displayName: String?
    ) : GoogleSignInOutcome

    data object Cancelled : GoogleSignInOutcome

    /**
     * Google ile giriş yapılandırılmamış (`default_web_client_id` kaynağı yok).
     * Android uygulaması Firebase projesine kaydedilip `google-services.json`
     * eklendiğinde kendiliğinden çalışır hâle gelir.
     */
    data object Unavailable : GoogleSignInOutcome

    /** Yapılandırma var ama hesap seçici / token alınamadı. */
    data class Failure(val message: String?) : GoogleSignInOutcome
}
