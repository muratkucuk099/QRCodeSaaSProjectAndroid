package murat.com.saasproject.ui.common

import android.text.InputType
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import murat.com.saasproject.R
import murat.com.saasproject.databinding.DialogTextInputBinding

/**
 * Tek alanlı metin girişi diyaloğu — iOS'taki `UIAlertController` + `addTextField`
 * kalıbının Android karşılığı.
 *
 * Davet kodu, elle puan girişi gibi kısa girdiler için kullanılır; ayrı bir ekran
 * açmak yerine akışı bölmeden değer alınır.
 */
object TextInputDialog {

    /**
     * @param inputType girişin klavye tipi (ör. sayı için [InputType.TYPE_CLASS_NUMBER]).
     * @param allCaps büyük harfe zorlanır (davet kodları büyük harflidir).
     * @param onSubmit kullanıcı onayladığında girilen ham metinle çağrılır.
     */
    fun show(
        fragment: Fragment,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int? = null,
        @StringRes hintRes: Int? = null,
        @StringRes positiveRes: Int = R.string.confirm,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        allCaps: Boolean = false,
        onSubmit: (String) -> Unit
    ) {
        val binding = DialogTextInputBinding.inflate(fragment.layoutInflater)

        binding.dialogEditText.inputType = if (allCaps) {
            inputType or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        } else {
            inputType
        }
        hintRes?.let { binding.dialogInputLayout.hint = fragment.getString(it) }

        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(titleRes)
            .apply { messageRes?.let(::setMessage) }
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positiveRes) { _, _ ->
                onSubmit(binding.dialogEditText.text?.toString().orEmpty())
            }
            .show()
    }
}
