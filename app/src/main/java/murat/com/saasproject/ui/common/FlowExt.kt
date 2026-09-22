package murat.com.saasproject.ui.common

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Akışı yalnızca fragment'ın view'ı görünürken toplar.
 *
 * Arka planda Firestore listener'larının çalışmaya devam etmesini ve gereksiz
 * okuma yapılmasını engeller (performans gereksinimi).
 */
fun <T> Fragment.collectWhileStarted(flow: Flow<T>, collector: suspend (T) -> Unit): Job =
    viewLifecycleOwner.lifecycleScope.launch {
        flow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect(collector)
    }

/**
 * [Event] akışını toplar ve her olayı yalnızca bir kez işler.
 * Ekran yeniden görünür olduğunda eski hata mesajının tekrar gösterilmesini önler.
 */
fun <T> Fragment.collectEvents(flow: StateFlow<Event<T>?>, handler: (T) -> Unit): Job =
    collectWhileStarted(flow) { event ->
        event?.getIfNotHandled()?.let(handler)
    }

/** Coroutine scope içinde akışı toplar (Activity/özel kullanım için). */
fun <T> CoroutineScope.collectIn(flow: Flow<T>, collector: suspend (T) -> Unit): Job =
    launch { flow.collect(collector) }
