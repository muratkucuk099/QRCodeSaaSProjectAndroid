package murat.com.saasproject.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.model.UserModel
import murat.com.saasproject.data.remote.CloudFunctionException
import murat.com.saasproject.data.remote.CloudFunctionsApi
import murat.com.saasproject.domain.auth.AuthRoleResolver
import murat.com.saasproject.domain.config.AuthEmailConfig
import murat.com.saasproject.domain.config.CloudFunctionsConfig
import murat.com.saasproject.domain.config.FirestorePaths
import murat.com.saasproject.domain.config.KvkkPolicy
import murat.com.saasproject.domain.config.MainAdminConfig
import murat.com.saasproject.domain.validation.UserDisplayName
import java.util.Date

/**
 * Yeni bir OAuth kullanıcısının Firestore kaydı yok; önce KVKK onayı alınmalı.
 * iOS `FirebaseServiceError.oauthNewUserNeedsKVKKConsentCode` (-4) karşılığı.
 */
class OAuthNeedsKvkkConsentException : Exception("OAuth kaydı KVKK onayı bekliyor")

/** Rol çözümlenemedi — iOS `NSError(domain: "Auth", code: -1)` "Yetkisiz kullanıcı". */
class UnauthorizedUserException : Exception("Yetkisiz kullanıcı")

/**
 * iOS `FirebaseService`'in kimlik doğrulama ve rol çözümleme bölümünün karşılığı.
 *
 * Rol, ayrı bir `role` alanından değil doküman varlığından çıkarılır (iOS `checkRole`):
 *   1. UID == MainAdminConfig.UID          -> MAIN_ADMIN
 *   2. `users/{uid}` dokümanı var           -> USER
 *   3. `businesses/{uid}` dokümanı var      -> SUB_ADMIN
 *   4. hiçbiri                              -> UnauthorizedUserException
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    val currentUserId: String? get() = auth.currentUser?.uid
    val currentUserEmail: String? get() = auth.currentUser?.email
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /** OAuth akışında sağlayıcıdan gelen ad; kayıt tamamlanırken kullanılır. */
    private var pendingOAuthProfileName: String? = null

    // region Email / password

    suspend fun createUser(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.uid ?: error("Kullanıcı oluşturulamadı")
    }

    suspend fun login(email: String, password: String): Result<LoginResult> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Giriş başarısız")
        checkRole(uid)
    }

    /** iOS `loginWithUID` — önce cache'e bakar, yoksa Firestore'dan çözer. */
    suspend fun loginWithUid(uid: String): Result<LoginResult> = runCatching {
        cache.loginRole(uid) ?: checkRole(uid)
    }

    private suspend fun checkRole(uid: String): LoginResult {
        if (uid == MainAdminConfig.UID) {
            return LoginResult.MAIN_ADMIN.also { cache.storeLoginRole(it, uid) }
        }

        val userExists = db.collection(FirestorePaths.USERS).document(uid).get().await().exists()
        val businessExists = if (userExists) {
            false
        } else {
            db.collection(FirestorePaths.BUSINESSES).document(uid).get().await().exists()
        }

        val role = AuthRoleResolver.resolve(
            uid = uid,
            userDocumentExists = userExists,
            businessDocumentExists = businessExists
        ) ?: throw UnauthorizedUserException()

        cache.storeLoginRole(role, uid)
        return role
    }

    suspend fun saveUser(user: UserModel): Result<Unit> = runCatching {
        db.collection(FirestorePaths.USERS)
            .document(user.id)
            .set(user.toFirestoreMap())
            .await()
    }

    // endregion

    // region Password reset

    /**
     * iOS davranışı: önce özel `sendPasswordResetEmail` Cloud Function'ı denenir
     * (markalı e-posta şablonu için). 404 dışındaki hatalarda Firebase Auth'un
     * kendi şifre sıfırlama e-postasına düşülür. 404 ise kullanıcı gerçekten yok,
     * hata yukarıya taşınır.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.setLanguageCode(AuthEmailConfig.PREFERRED_LANGUAGE_CODE)

        try {
            CloudFunctionsApi.callPublic(
                functionName = CloudFunctionsConfig.SEND_PASSWORD_RESET_EMAIL,
                payload = mapOf("email" to email)
            )
        } catch (e: CloudFunctionException) {
            if (e.statusCode == HTTP_NOT_FOUND) throw e
            auth.sendPasswordResetEmail(email).await()
        }
        Unit
    }

    // endregion

    // region OAuth (Google)

    /**
     * Google credential ile Firebase oturumu açar ve rolü çözer.
     *
     * Firestore kaydı olmayan yeni kullanıcı için [OAuthNeedsKvkkConsentException] fırlatılır;
     * UI katmanı KVKK onay ekranını gösterip [completeOAuthRegistrationAfterKvkkConsent]
     * çağırır. iOS `resolveLoginResultAfterOAuthSignIn` ile aynı akış.
     */
    suspend fun signInWithCredential(
        credential: AuthCredential,
        providerName: String?
    ): Result<LoginResult> = runCatching {
        pendingOAuthProfileName = UserDisplayName.normalizedProfileName(providerName)

        val uid = auth.signInWithCredential(credential).await().user?.uid
            ?: error("Firebase oturum açılamadı")

        val role = try {
            checkRole(uid)
        } catch (_: UnauthorizedUserException) {
            throw OAuthNeedsKvkkConsentException()
        }

        syncPlaceholderUserNameIfNeeded(uid, role)
        ensureKvkkConsentRecorded(uid, role)
        role
    }

    /** iOS `completeOAuthRegistrationAfterKVKKConsent` — KVKK onayı sonrası kullanıcıyı oluşturur. */
    suspend fun completeOAuthRegistrationAfterKvkkConsent(): Result<LoginResult> = runCatching {
        val firebaseUser = auth.currentUser ?: error("Kullanıcı oturumu bulunamadı")

        val resolvedName = UserDisplayName.resolveForOAuthRegistration(
            providerName = pendingOAuthProfileName,
            firebaseDisplayName = firebaseUser.displayName,
            email = firebaseUser.email
        )
        pendingOAuthProfileName = null

        val user = UserModel(
            id = firebaseUser.uid,
            name = resolvedName,
            email = firebaseUser.email.orEmpty(),
            createdAt = Date(),
            businesses = emptyList(),
            kvkkAcceptedAt = Date(),
            kvkkPolicyVersion = KvkkPolicy.VERSION
        )

        saveUser(user).getOrThrow()
        cache.storeLoginRole(LoginResult.USER, firebaseUser.uid)
        LoginResult.USER
    }

    /** iOS `cancelPendingOAuthRegistration` — KVKK ekranında vazgeçilirse oturumu kapatır. */
    fun cancelPendingOAuthRegistration() {
        pendingOAuthProfileName = null
        auth.signOut()
    }

    /**
     * Kullanıcının Firestore'daki adı placeholder ise (ör. e-posta ön eki) sağlayıcıdan
     * gelen gerçek adla günceller. iOS `syncPlaceholderUserNameIfNeeded`.
     */
    private suspend fun syncPlaceholderUserNameIfNeeded(uid: String, role: LoginResult) {
        val providerName = pendingOAuthProfileName ?: return
        if (role != LoginResult.USER) return

        val userRef = db.collection(FirestorePaths.USERS).document(uid)
        val snapshot = userRef.get().await()
        if (!snapshot.exists()) return

        val currentName = snapshot.getString("name")
        val email = snapshot.getString("email")
        if (!UserDisplayName.isPlaceholder(currentName, email)) return

        userRef.update("name", providerName).await()
    }

    /**
     * iOS `ensureKVKKConsentRecorded` — OAuth ile giren mevcut kullanıcı/işletme için
     * KVKK onay damgasını günceller. Ana admin için atlanır.
     */
    private suspend fun ensureKvkkConsentRecorded(uid: String, role: LoginResult) {
        val collection = when (role) {
            LoginResult.USER -> FirestorePaths.USERS
            LoginResult.SUB_ADMIN -> FirestorePaths.BUSINESSES
            LoginResult.MAIN_ADMIN -> return
        }

        db.collection(collection)
            .document(uid)
            .set(
                mapOf(
                    "kvkkAcceptedAt" to Timestamp(Date()),
                    "kvkkPolicyVersion" to KvkkPolicy.VERSION
                ),
                SetOptions.merge()
            )
            .await()
    }

    // endregion

    // region Session

    fun signOut() {
        auth.signOut()
    }

    /** iOS `resetSessionState()` — listener'ları kapatıp cache'i temizler. */
    fun resetSessionState() {
        cache.clearAll()
    }

    // endregion

    companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
