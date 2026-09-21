package murat.com.saasproject

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.launch
import murat.com.saasproject.databinding.ActivityMainBinding
import murat.com.saasproject.ui.SessionViewModel

/**
 * Uygulamanın tek activity'si — iOS'taki `UIWindow` + `AppRootRouter` ikilisinin karşılığı.
 *
 * Rol ve abonelik durumu [SessionViewModel] tarafından yayınlanır; burada yalnızca
 * navigasyon köküne uygulanır.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val sessionViewModel: SessionViewModel by viewModels()

    private val navController: NavController
        get() = (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment)
            .navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeSessionRoot()
        if (savedInstanceState != null) {
            sessionViewModel.resolveSessionIfNeeded()
        }
    }

    private fun observeSessionRoot() {
        lifecycleScope.launch {
            // Yalnızca ekran görünürken yönlendirme yapılır; arka planda navigasyon
            // yapmak IllegalStateException'a yol açabilir.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.root.collect { root -> AppRouter.apply(navController, root) }
            }
        }
    }

}
