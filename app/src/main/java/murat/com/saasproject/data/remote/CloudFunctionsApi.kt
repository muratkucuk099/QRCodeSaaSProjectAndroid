package murat.com.saasproject.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import murat.com.saasproject.domain.config.CloudFunctionsConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cloud Function çağrısı sunucudan gelen HTTP durum kodunu taşır.
 *
 * iOS `performCloudRequest` da NSError.code olarak HTTP kodunu kullanır; şifre sıfırlamada
 * 404 (kullanıcı bulunamadı) ve davet kodunda 400/404/409 ayrımı buna dayanır.
 */
class CloudFunctionException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/**
 * iOS `FirebaseService.performCloudRequest` / `performAuthenticatedCloudRequest` karşılığı.
 *
 * Fonksiyonlar `us-central1` bölgesinde HTTP endpoint olarak yayınlanmıştır ve kimlik
 * doğrulamasını `Authorization: Bearer <Firebase ID token>` başlığından okur.
 * Sunucu tarafını değiştirmiyoruz; iOS ile aynı sözleşmeyi kullanıyoruz.
 */
object CloudFunctionsApi {

    private const val TIMEOUT_SECONDS = 60L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json".toMediaType()

    /** Kimlik doğrulaması gerektirmeyen çağrı (ör. `validateInviteCode`). */
    suspend fun callPublic(
        functionName: String,
        payload: Map<String, Any>
    ): JSONObject = execute(functionName, payload, bearerToken = null)

    /**
     * Giriş yapmış kullanıcı adına çağrı. iOS gibi token'ı zorla yeniler
     * (`getIDTokenForcingRefresh(true)`) — böylece süresi dolmuş token'la 401 alınmaz.
     */
    suspend fun callAuthenticated(
        functionName: String,
        payload: Map<String, Any>
    ): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw CloudFunctionException(401, ERROR_NO_SESSION)

        val token = withContext(Dispatchers.IO) {
            user.getIdToken(true).await().token
        } ?: throw CloudFunctionException(401, ERROR_NO_TOKEN)

        return execute(functionName, payload, bearerToken = token)
    }

    private suspend fun execute(
        functionName: String,
        payload: Map<String, Any>,
        bearerToken: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject(payload).toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(CloudFunctionsConfig.BASE_URL + functionName)
            .post(body)
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

            if (!response.isSuccessful) {
                val message = json.optString("message").takeIf { it.isNotEmpty() }
                    ?: "Sunucu hatası (${response.code})"
                throw CloudFunctionException(response.code, message)
            }

            json
        }
    }

    private const val ERROR_NO_SESSION = "Oturum bulunamadı"
    private const val ERROR_NO_TOKEN = "Oturum token'ı alınamadı"
}
