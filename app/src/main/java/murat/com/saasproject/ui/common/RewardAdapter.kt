package murat.com.saasproject.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import murat.com.saasproject.R
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.databinding.ItemRewardBinding

class RewardAdapter(
    private val onClick: ((Reward) -> Unit)? = null
) : ListAdapter<Reward, RewardAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemRewardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onClick)

    class Holder(private val binding: ItemRewardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reward: Reward, onClick: ((Reward) -> Unit)?) {
            binding.rewardNameTextView.text = reward.name
            binding.rewardDescriptionTextView.text = reward.description
            binding.rewardPointsTextView.text =
                binding.root.context.getString(R.string.reward_required_points, reward.requiredPoints)
            binding.rewardImageView.loadRemoteImage(reward.imageUrl)
            binding.root.setOnClickListener { onClick?.invoke(reward) }
            binding.root.isClickable = onClick != null
        }
    }

    private object Diff : DiffUtil.ItemCallback<Reward>() {
        override fun areItemsTheSame(old: Reward, new: Reward) = old.rewardId == new.rewardId
        override fun areContentsTheSame(old: Reward, new: Reward) = old == new
    }
}
