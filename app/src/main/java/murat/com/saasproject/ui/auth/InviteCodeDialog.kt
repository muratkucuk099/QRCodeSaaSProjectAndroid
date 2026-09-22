package murat.com.saasproject.ui.auth

import android.text.InputType
import androidx.fragment.app.Fragment
import murat.com.saasproject.R
import murat.com.saasproject.ui.common.TextInputDialog

/**
 * Davet kodu girişi. iOS'ta aynı akış `UIAlertController` ile sunuluyor.
 *
 * Kodlar büyük harf ve rakamlardan oluştuğu için klavye büyük harfe ayarlanır.
 */
object InviteCodeDialog {

    fun show(fragment: Fragment, onSubmit: (String) -> Unit) {
        TextInputDialog.show(
            fragment = fragment,
            titleRes = R.string.invite_code_title,
            messageRes = R.string.invite_code_message,
            hintRes = R.string.invite_code_hint,
            positiveRes = R.string.continue_action,
            inputType = InputType.TYPE_CLASS_TEXT,
            allCaps = true,
            onSubmit = onSubmit
        )
    }
}
