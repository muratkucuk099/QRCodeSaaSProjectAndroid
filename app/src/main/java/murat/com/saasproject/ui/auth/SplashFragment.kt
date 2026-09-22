package murat.com.saasproject.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import murat.com.saasproject.R
import murat.com.saasproject.ui.SessionViewModel

/**
 * iOS `SplashViewController` karşılığı.
 *
 * Kayıtlı oturumun rolünü çözer; sonuç [SessionViewModel] üzerinden yayınlanır ve
 * MainActivity kökü değiştirir. Bu fragment yalnızca marka ekranını gösterir.
 */
class SplashFragment : Fragment(R.layout.fragment_splash) {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionViewModel.resolveSessionIfNeeded()
    }
}
