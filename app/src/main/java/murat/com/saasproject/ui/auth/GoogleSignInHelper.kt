package murat.com.saasproject.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import murat.com.saasproject.data.model.GoogleSignInOutcome

/**
 * Google ile giriş.
 *
 * IOS davranışı: `GIDSignIn.sharedInstance.signIn(withPresenting:)` ile Google SDK'sı
 * kendi ekranını açar, dönen idToken Firebase credential'a çevrilir.
 *
 * ANDROID karşılığı: Credential Manager (Jetpack) kullanılır. Bu, Google'ın Android'de
 * önerdiği güncel yöntemdir; kullanıcı hesap seçiciyi sistem arayüzünde görür.
 * Kullanıcı vazgeçtiğinde hata gösterilmez — iOS'ta `GIDSignInError.canceled`
 * sessizce yutuluyor, burada da aynısı yapılır.
 */
object GoogleSignInHelper {

    /**
     * Sunucu (web) client id'si `google-services.json`'dan üretilir.
     *
     * Kaynak derleme zamanında var olmayabileceği için (Android uygulaması Firebase
     * projesine kaydedilmeden önce) isimle çözülür. Bulunamazsa Google ile giriş
     * devre dışı kalır ve kullanıcıya açıklayıcı hata gösterilir; uygulama çökmez.
     */
    fun webClientId(context: Context): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return if (resourceId == 0) null else context.getString(resourceId)
    }

    /**
     * Hesap seçiciyi açar ve Firebase credential döndürür.
     *
     * @return kullanıcı vazgeçtiyse [GoogleSignInOutcome.Cancelled].
     */
    suspend fun requestCredential(context: Context): GoogleSignInOutcome {
        val clientId = webClientId(context)
            ?: return GoogleSignInOutcome.Unavailable

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)

            GoogleSignInOutcome.Success(
                credential = GoogleAuthProvider.getCredential(googleCredential.idToken, null),
                displayName = googleCredential.displayName
            )
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (error: Exception) {
            GoogleSignInOutcome.Failure(error.message)
        }
    }
}
