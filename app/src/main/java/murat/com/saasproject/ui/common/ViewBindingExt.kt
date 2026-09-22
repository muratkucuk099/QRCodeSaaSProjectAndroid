package murat.com.saasproject.ui.common

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Fragment içinde ViewBinding referansını yaşam döngüsüne bağlar.
 *
 * `onDestroyView`'da binding'i otomatik temizler; aksi halde fragment geri yığında
 * beklerken yok edilmiş view'lara referans tutulur ve bellek sızıntısı oluşur.
 *
 * Kullanım:
 * ```
 * private val binding by viewBinding(FragmentLoginBinding::bind)
 * ```
 */
fun <T : ViewBinding> Fragment.viewBinding(bind: (View) -> T): ReadOnlyProperty<Fragment, T> =
    FragmentViewBindingDelegate(this, bind)

private class FragmentViewBindingDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val bind: (View) -> T
) : ReadOnlyProperty<Fragment, T> {

    private var binding: T? = null

    init {
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
                    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            binding = null
                        }
                    })
                }
            }
        })
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        binding?.let { return it }

        val view = thisRef.requireView()
        return bind(view).also { binding = it }
    }
}
