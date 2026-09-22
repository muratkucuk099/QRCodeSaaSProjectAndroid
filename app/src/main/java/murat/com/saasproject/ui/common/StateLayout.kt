package murat.com.saasproject.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import murat.com.saasproject.R
import murat.com.saasproject.databinding.ViewStateBinding

/**
 * Yükleniyor / boş / hata durumlarını tek bir bileşende toplayan katman.
 *
 * Gereksinim: "Hiçbir ekran boş veya bozuk görünmesin." Bu view içeriğin üzerine
 * yerleştirilir; [showLoading], [showEmpty], [showError] çağrıldığında içerik gizlenir,
 * [showContent] çağrıldığında geri gelir.
 *
 * Layout'ta içerik view'ının kimliğini [contentViewId] ile bildirmek yeterlidir.
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewStateBinding.inflate(LayoutInflater.from(context), this)

    /** Durum gösterilirken gizlenecek içerik view'ı. */
    var contentView: View? = null

    private var retryAction: (() -> Unit)? = null

    init {
        binding.stateActionButton.setOnClickListener { retryAction?.invoke() }
    }

    fun showContent() {
        contentView?.isVisible = true
        binding.stateProgress.isVisible = false
        binding.stateMessageContainer.isVisible = false
    }

    fun showLoading() {
        contentView?.isVisible = false
        binding.stateProgress.isVisible = true
        binding.stateMessageContainer.isVisible = false
    }

    /**
     * İçerik yüklenirken mevcut içeriği ekranda bırakır (yenileme sırasında ekranın
     * boşalmasını önler).
     */
    fun showRefreshing() {
        binding.stateProgress.isVisible = true
        binding.stateMessageContainer.isVisible = false
    }

    fun showEmpty(
        title: String,
        description: String? = null,
        @DrawableRes icon: Int? = null,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) = showMessage(title, description, icon, actionText, action)

    fun showError(
        title: String = context.getString(R.string.generic_error_title),
        description: String? = context.getString(R.string.no_connection),
        @DrawableRes icon: Int? = R.drawable.ic_wifi_error,
        actionText: String? = context.getString(R.string.retry),
        action: (() -> Unit)? = null
    ) = showMessage(title, description, icon, actionText, action)

    private fun showMessage(
        title: String,
        description: String?,
        @DrawableRes icon: Int?,
        actionText: String?,
        action: (() -> Unit)?
    ) {
        contentView?.isVisible = false
        binding.stateProgress.isVisible = false
        binding.stateMessageContainer.isVisible = true

        binding.stateTitle.text = title

        binding.stateDescription.isVisible = !description.isNullOrBlank()
        binding.stateDescription.text = description

        if (icon != null) {
            binding.stateIcon.isVisible = true
            binding.stateIcon.setImageResource(icon)
        } else {
            binding.stateIcon.isVisible = false
        }

        retryAction = action
        binding.stateActionButton.isVisible = action != null && !actionText.isNullOrBlank()
        binding.stateActionButton.text = actionText
    }

    /**
     * [UiState] akışını doğrudan bu katmana bağlar.
     *
     * @param onSuccess başarı durumunda içeriği doldurmak için çağrılır.
     * @param emptyTitle boş durumda gösterilecek başlık.
     * @param onRetry hata durumunda tekrar deneme aksiyonu.
     */
    fun <T> bind(
        state: UiState<T>,
        emptyTitle: String,
        emptyDescription: String? = null,
        @DrawableRes emptyIcon: Int? = null,
        onRetry: (() -> Unit)? = null,
        onSuccess: (T) -> Unit
    ) {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Empty -> showEmpty(emptyTitle, emptyDescription, emptyIcon)
            is UiState.Error -> showError(
                description = state.message ?: context.getString(R.string.no_connection),
                action = onRetry
            )
            is UiState.Success -> {
                onSuccess(state.data)
                showContent()
            }
        }
    }

    /**
     * Layout'tan `app:contentViewId` benzeri bir bağ kurmak yerine, ekran kendi
     * içerik view'ını burada bildirir. Basit ve derleme zamanı güvenli.
     */
    fun attachContent(view: View) {
        contentView = view
    }
}
