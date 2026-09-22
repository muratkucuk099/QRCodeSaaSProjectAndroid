package murat.com.saasproject.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import murat.com.saasproject.R

/**
 * Ekranlarda tekrar eden Android'e özgü UI işlerini tek yerde topluyoruz.
 *
 * IOS davranışı: hata ve başarı geri bildirimleri `UIAlertController` ile veriliyor.
 * ANDROID karşılığı: bloke etmeyen bilgilendirmeler için Snackbar, kullanıcı kararı
 * gereken durumlar için MaterialAlertDialog kullanılır (Android yönergeleri).
 */

fun Fragment.showSnackbar(@StringRes messageRes: Int) =
    showSnackbar(getString(messageRes))

fun Fragment.showSnackbar(message: String) {
    val root = view ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
}

/** Kalıcı kalan, kullanıcı aksiyonu bekleyen hata bildirimi. */
fun Fragment.showErrorSnackbar(message: String, actionText: String, action: () -> Unit) {
    val root = view ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
        .setAction(actionText) { action() }
        .show()
}

fun Fragment.showAlert(
    title: String,
    message: String? = null,
    @StringRes positiveRes: Int = R.string.ok,
    onPositive: (() -> Unit)? = null
) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveRes) { _, _ -> onPositive?.invoke() }
        .show()
}

fun Fragment.showAlert(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int? = null,
    onPositive: (() -> Unit)? = null
) = showAlert(
    title = getString(titleRes),
    message = messageRes?.let(::getString),
    onPositive = onPositive
)

/**
 * Onay diyaloğu. [destructive] true ise onay butonu yıkıcı işlem rengiyle gösterilir
 * (hesap silme, ödül silme, abonelik iptali gibi geri alınamaz işlemler için).
 */
fun Fragment.showConfirmDialog(
    title: String,
    message: String,
    @StringRes confirmRes: Int = R.string.confirm,
    @StringRes cancelRes: Int = R.string.cancel,
    destructive: Boolean = false,
    onConfirm: () -> Unit
) {
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(cancelRes, null)
        .setPositiveButton(confirmRes) { _, _ -> onConfirm() }
        .show()

    if (destructive) {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(requireContext().getColor(R.color.error))
    }
}

/**
 * Coil ile görsel yükler. Firebase Storage görselleri için bellek + disk cache
 * varsayılan olarak açıktır, böylece liste kaydırmalarında yeniden indirme olmaz.
 *
 * @param placeholder yükleme sırasında ve URL boşsa gösterilecek görsel.
 */
fun ImageView.loadRemoteImage(
    url: String?,
    @DrawableRes placeholder: Int = R.drawable.bg_image_placeholder,
    @DrawableRes error: Int = placeholder
) {
    if (url.isNullOrBlank()) {
        setImageResource(placeholder)
        return
    }
    load(url) {
        crossfade(true)
        placeholder(placeholder)
        error(error)
    }
}

/** Boşluk dışındaki metni döndürür; alan boşsa null. */
val TextInputLayout.trimmedText: String?
    get() = editText?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Alan hatasını gösterir/temizler. `null` verilmesi hatayı temizler.
 * iOS'ta hatalar tek bir alert'te toplanıyor; Android'de alan bazlı hata
 * göstermek platform standardıdır.
 */
fun TextInputLayout.setFieldError(message: String?) {
    error = message
    isErrorEnabled = message != null
}

fun Fragment.hideKeyboard() {
    val focused = requireActivity().currentFocus ?: view ?: return
    requireContext().getSystemService<InputMethodManager>()
        ?.hideSoftInputFromWindow(focused.windowToken, 0)
}

fun Context.copyToClipboard(label: String, text: String) {
    getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Kullanıcıyı uygulamanın sistem ayarları sayfasına götürür (kalıcı izin reddi sonrası). */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}

/**
 * Harici bağlantı açar (WhatsApp, KVKK metni vb.).
 * @return uygun uygulama bulunamadıysa false.
 */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Kullanıcı yazmaya başladığında alan hatasını temizlemek gibi basit tepkiler için.
 */
fun EditText.doOnTextChange(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = action(s?.toString().orEmpty())
    })
}

fun View.setSingleClickListener(onClick: () -> Unit) {
    setOnClickListener {
        isEnabled = false
        post { isEnabled = true }
        onClick()
    }
}
