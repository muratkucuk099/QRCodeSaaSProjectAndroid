package murat.com.saasproject.ui.legal

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentKvkkPolicyBinding
import murat.com.saasproject.domain.config.KvkkPolicy
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.Formatters
import java.util.Date

/**
 * iOS `KVKKPolicyViewController` karşılığı: KVKK aydınlatma metnini gösterir.
 *
 * Metin `strings.xml`'de tutulur, değişkenler burada doldurulur — böylece
 * İngilizce sürüm eklenmek istendiğinde yalnızca kaynak dosyası çevrilir.
 */
class KvkkPolicyFragment : Fragment(R.layout.fragment_kvkk_policy) {

    private val binding by viewBinding(FragmentKvkkPolicyBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.policyTextView.text = getString(
            R.string.kvkk_policy_full_text,
            KvkkPolicy.APP_NAME,
            Formatters.longDate(Date()),
            KvkkPolicy.VERSION,
            KvkkPolicy.DATA_CONTROLLER_TITLE,
            KvkkPolicy.CONTACT_EMAIL,
            KvkkPolicy.SUPPORT_URL
        )
    }
}
