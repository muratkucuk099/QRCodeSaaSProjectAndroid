package murat.com.saasproject.ui.common

/**
 * Liste/veri ekranlarının dört durumu. Her network işleminde Loading / Success / Empty / Error
 * durumlarının tümü ele alınabilsin diye tek tipte modellendi — hiçbir ekran boş veya
 * yarı yüklenmiş görünmez.
 *
 * iOS tarafında bu durumlar `LoadingOverlayView` + `EmptyStateView` + alert üçlüsüyle
 * ayrı ayrı yönetiliyor; Android'de tek bir state akışına indirgeyip `StateLayout`
 * bileşeninin render etmesini sağlıyoruz.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    /** Veri geldi ve doluysa. */
    data class Success<T>(val data: T) : UiState<T>

    /** İstek başarılı ama sonuç boş — kullanıcıya yönlendirici bir mesaj gösterilir. */
    data object Empty : UiState<Nothing>

    /** [message] kullanıcıya gösterilebilir Türkçe metin. */
    data class Error(val message: String) : UiState<Nothing>
}

/** Tek seferlik olaylar (toast, alert, navigasyon) için sarmalayıcı. */
class Event<out T>(private val content: T) {
    private var handled = false

    fun getIfNotHandled(): T? = if (handled) null else content.also { handled = true }
}
