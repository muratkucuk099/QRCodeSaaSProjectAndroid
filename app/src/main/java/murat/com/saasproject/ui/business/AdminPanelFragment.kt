package murat.com.saasproject.ui.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import murat.com.saasproject.R
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.RewardsScreenMode
import murat.com.saasproject.databinding.FragmentAdminPanelBinding
import murat.com.saasproject.databinding.ItemBusinessNotificationBinding
import murat.com.saasproject.ui.common.RewardAdapter
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.businessHost
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showConfirmDialog
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.Formatters
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class AdminPanelFragment : Fragment(R.layout.fragment_admin_panel) {

    private val binding by viewBinding(FragmentAdminPanelBinding::bind)
    private val viewModel: AdminPanelViewModel by viewModels()

    private val rewardAdapter = RewardAdapter { reward ->
        if (viewModel.mode != RewardsScreenMode.SELECT) return@RewardAdapter
        parentFragmentManager.setFragmentResult(
            AdminMainFragment.REQUEST_REWARD,
            bundleOf(
                AdminMainFragment.KEY_REWARD_ID to reward.rewardId,
                AdminMainFragment.KEY_REWARD_NAME to reward.name,
                AdminMainFragment.KEY_POINTS to -reward.requiredPoints
            )
        )
        findNavController().navigateUp()
    }

    private val notificationAdapter = BusinessNotificationAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.mode = RewardsScreenMode.valueOf(
            arguments?.getString(ARG_MODE) ?: RewardsScreenMode.MANAGE.name
        )

        binding.toolbar.title = if (viewModel.mode == RewardsScreenMode.SELECT) {
            getString(R.string.admin_panel_select_reward_title)
        } else {
            getString(R.string.tab_rewards)
        }
        if (viewModel.mode == RewardsScreenMode.SELECT) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            binding.contentToggle.isVisible = false
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = rewardAdapter
        binding.stateLayout.attachContent(binding.swipeRefresh)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh(force = true) }

        binding.contentToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.selectContent(
                if (checkedId == R.id.rewardsToggleButton) {
                    AdminPanelContent.REWARDS
                } else {
                    AdminPanelContent.NOTIFICATIONS
                }
            )
        }

        if (viewModel.mode == RewardsScreenMode.MANAGE) attachSwipeToDelete()

        collectWhileStarted(viewModel.content) { content ->
            binding.recyclerView.adapter =
                if (content == AdminPanelContent.REWARDS) rewardAdapter else notificationAdapter
            renderCurrent()
        }
        collectWhileStarted(viewModel.rewards) { if (viewModel.content.value == AdminPanelContent.REWARDS) renderCurrent() }
        collectWhileStarted(viewModel.notifications) {
            if (viewModel.content.value == AdminPanelContent.NOTIFICATIONS) renderCurrent()
        }
        collectEvents(viewModel.events) { showSnackbar(requireContext().resolveErrorMessage(it)) }

        viewModel.selectContent(AdminPanelContent.REWARDS)
    }

    private fun renderCurrent() {
        binding.swipeRefresh.isRefreshing = false
        val isRewards = viewModel.content.value == AdminPanelContent.REWARDS
        val emptyTitle: String
        val emptyMessage: String
        val emptyAction: String?
        val emptyClick: (() -> Unit)?
        if (isRewards) {
            if (viewModel.mode == RewardsScreenMode.SELECT) {
                emptyTitle = getString(R.string.admin_panel_rewards_empty_select_title)
                emptyMessage = getString(R.string.admin_panel_rewards_empty_select_message)
                emptyAction = null
                emptyClick = null
            } else {
                emptyTitle = getString(R.string.admin_panel_rewards_empty_manage_title)
                emptyMessage = getString(R.string.admin_panel_rewards_empty_manage_message)
                emptyAction = getString(R.string.admin_panel_rewards_empty_manage_action)
                emptyClick = { businessHost()?.switchToCreate() }
            }
            bindPanelState(
                state = viewModel.rewards.value,
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                emptyIcon = R.drawable.ic_gift,
                emptyAction = emptyAction,
                emptyClick = emptyClick
            ) { rewardAdapter.submitList(it) }
        } else {
            emptyTitle = getString(R.string.admin_panel_notifications_empty_title)
            emptyMessage = getString(R.string.admin_panel_notifications_empty_message)
            emptyAction = getString(R.string.admin_panel_notifications_empty_action)
            emptyClick = { businessHost()?.switchToCreate() }
            bindPanelState(
                state = viewModel.notifications.value,
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                emptyIcon = R.drawable.ic_bell,
                emptyAction = emptyAction,
                emptyClick = emptyClick
            ) { notificationAdapter.submitList(it) }
        }
    }

    private fun <T> bindPanelState(
        state: UiState<List<T>>,
        emptyTitle: String,
        emptyMessage: String,
        emptyIcon: Int,
        emptyAction: String?,
        emptyClick: (() -> Unit)?,
        onSuccess: (List<T>) -> Unit
    ) {
        when (state) {
            is UiState.Loading -> binding.stateLayout.showLoading()
            is UiState.Empty -> binding.stateLayout.showEmpty(
                title = emptyTitle,
                description = emptyMessage,
                icon = emptyIcon,
                actionText = emptyAction,
                action = emptyClick
            )
            is UiState.Error -> binding.stateLayout.showError(
                title = getString(R.string.admin_panel_list_error_title),
                description = state.message.ifBlank { getString(R.string.error_no_session) },
                action = { viewModel.refresh(force = true) }
            )
            is UiState.Success -> {
                onSuccess(state.data)
                binding.stateLayout.showContent()
            }
        }
    }

    private fun attachSwipeToDelete() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (viewModel.content.value == AdminPanelContent.REWARDS) {
                    val reward = rewardAdapter.currentList.getOrNull(position) ?: return
                    showConfirmDialog(
                        title = getString(R.string.delete_confirm_title),
                        message = getString(R.string.delete_reward_confirm_message),
                        confirmRes = R.string.delete,
                        destructive = true
                    ) { viewModel.deleteReward(reward) }
                    rewardAdapter.notifyItemChanged(position)
                } else {
                    val notification = notificationAdapter.currentList.getOrNull(position) ?: return
                    showConfirmDialog(
                        title = getString(R.string.delete_confirm_title),
                        message = getString(R.string.delete_notification_confirm_message, notification.title),
                        confirmRes = R.string.delete,
                        destructive = true
                    ) { viewModel.deleteNotification(notification) }
                    notificationAdapter.notifyItemChanged(position)
                }
            }
        }).attachToRecyclerView(binding.recyclerView)
    }

    companion object {
        const val ARG_MODE = "mode"
    }
}

private class BusinessNotificationAdapter :
    ListAdapter<BusinessNotification, BusinessNotificationAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBusinessNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(private val binding: ItemBusinessNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BusinessNotification) {
            binding.titleTextView.text = item.title
            binding.bodyTextView.text = item.body
            val summary = when (item.status) {
                BusinessNotification.STATUS_NO_RECIPIENTS ->
                    binding.root.context.getString(R.string.notification_status_no_recipients)
                BusinessNotification.STATUS_NO_TOKENS ->
                    binding.root.context.getString(R.string.notification_status_no_tokens)
                BusinessNotification.STATUS_SENT ->
                    binding.root.context.getString(R.string.notification_status_sent, item.sentCount)
                else -> binding.root.context.getString(R.string.notification_status_default, item.sentCount)
            }
            binding.detailTextView.text = binding.root.context.getString(
                R.string.notification_detail_format,
                Formatters.dateTime(item.createdAt),
                summary
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<BusinessNotification>() {
        override fun areItemsTheSame(a: BusinessNotification, b: BusinessNotification) = a.id == b.id
        override fun areContentsTheSame(a: BusinessNotification, b: BusinessNotification) = a == b
    }
}
