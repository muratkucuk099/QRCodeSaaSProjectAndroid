package murat.com.saasproject.ui.common

import android.content.Context
import murat.com.saasproject.R

/**
 * Firebase/ağ hatalarının kullanıcıya gösterilecek metnini çözer.
 *
 * ViewModel'lar sabit metin tutmaz (yerelleştirme gereksinimi): hata mesajı yoksa
 * `null` döner ve view katmanı `strings.xml`'den genel metni koyar.
 */
fun Throwable.readableMessage(): String? =
    localizedMessage?.takeIf { it.isNotBlank() }

/** `null` mesajları genel hata metnine düşürür. */
fun Context.resolveErrorMessage(message: String?): String =
    message?.takeIf { it.isNotBlank() } ?: getString(R.string.no_connection)
