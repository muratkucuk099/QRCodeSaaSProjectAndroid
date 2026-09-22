package murat.com.saasproject.ui.mainadmin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import murat.com.saasproject.R
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.databinding.ItemMainAdminBusinessBinding

class MainAdminBusinessAdapter(
    private val onClick: (Business) -> Unit
) : ListAdapter<Business, MainAdminBusinessAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMainAdminBusinessBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onClick)

    class Holder(private val binding: ItemMainAdminBusinessBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(business: Business, onClick: (Business) -> Unit) {
            binding.nameTextView.text = business.name
            binding.statusTextView.text = subscriptionSummary(business)
            binding.root.setOnClickListener { onClick(business) }
        }

        /** iOS `MainAdminBusinessListViewModel.subscriptionSummary`. */
        private fun subscriptionSummary(business: Business): String {
            val context = binding.root.context
            if (!business.isActive) return context.getString(R.string.subscription_passive)
            val access = BusinessSubscriptionAccess.from(business)
            val days = access.remainingDays ?: return context.getString(R.string.subscription_no_date)
            return when {
                days > 0 -> context.getString(R.string.subscription_days_remaining, days)
                days == 0 -> context.getString(R.string.subscription_last_day)
                else -> context.getString(R.string.subscription_expired_short)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Business>() {
        override fun areItemsTheSame(a: Business, b: Business) = a.id == b.id
        override fun areContentsTheSame(a: Business, b: Business) = a == b
    }
}
