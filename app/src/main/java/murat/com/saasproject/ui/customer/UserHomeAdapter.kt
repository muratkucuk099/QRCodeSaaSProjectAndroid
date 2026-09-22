package murat.com.saasproject.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import murat.com.saasproject.R
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.databinding.ItemPlaceholderBinding
import murat.com.saasproject.databinding.ItemSectionHeaderBinding
import murat.com.saasproject.databinding.ItemUserBusinessBinding
import murat.com.saasproject.ui.common.loadRemoteImage

sealed class UserHomeRow {
    data class Header(val title: String) : UserHomeRow()
    data class BusinessRow(val business: Business, val points: Int?, val enrolled: Boolean) : UserHomeRow()
    data class Placeholder(val message: String) : UserHomeRow()
}

class UserHomeAdapter(
    private val onBusinessClick: (Business, Int) -> Unit
) : ListAdapter<UserHomeRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is UserHomeRow.Header -> TYPE_HEADER
        is UserHomeRow.BusinessRow -> TYPE_BUSINESS
        is UserHomeRow.Placeholder -> TYPE_PLACEHOLDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
            TYPE_PLACEHOLDER -> PlaceholderHolder(ItemPlaceholderBinding.inflate(inflater, parent, false))
            else -> BusinessHolder(ItemUserBusinessBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is UserHomeRow.Header -> (holder as HeaderHolder).bind(item.title)
            is UserHomeRow.Placeholder -> (holder as PlaceholderHolder).bind(item.message)
            is UserHomeRow.BusinessRow -> (holder as BusinessHolder).bind(item, onBusinessClick)
        }
    }

    private class HeaderHolder(private val binding: ItemSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.sectionHeaderTextView.text = title
        }
    }

    private class PlaceholderHolder(private val binding: ItemPlaceholderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: String) {
            binding.placeholderTextView.text = message
        }
    }

    private class BusinessHolder(private val binding: ItemUserBusinessBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserHomeRow.BusinessRow, onClick: (Business, Int) -> Unit) {
            binding.nameTextView.text = item.business.name.ifBlank {
                binding.root.context.getString(R.string.business_fallback_name)
            }
            binding.typeTextView.text = item.business.businessType
            binding.logoImageView.loadRemoteImage(item.business.logoURL)
            val points = item.points ?: 0
            binding.pointsTextView.isVisible = item.enrolled
            if (item.enrolled) {
                binding.pointsTextView.text =
                    binding.root.context.getString(R.string.points_badge, points)
            }
            binding.root.setOnClickListener { onClick(item.business, points) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<UserHomeRow>() {
        override fun areItemsTheSame(old: UserHomeRow, new: UserHomeRow): Boolean = when {
            old is UserHomeRow.Header && new is UserHomeRow.Header -> old.title == new.title
            old is UserHomeRow.Placeholder && new is UserHomeRow.Placeholder -> old.message == new.message
            old is UserHomeRow.BusinessRow && new is UserHomeRow.BusinessRow ->
                old.business.id == new.business.id && old.enrolled == new.enrolled
            else -> false
        }

        override fun areContentsTheSame(old: UserHomeRow, new: UserHomeRow) = old == new
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BUSINESS = 1
        private const val TYPE_PLACEHOLDER = 2
    }
}
