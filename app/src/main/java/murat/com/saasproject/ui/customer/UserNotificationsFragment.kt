package murat.com.saasproject.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import murat.com.saasproject.R
import murat.com.saasproject.data.model.UserNotification
import murat.com.saasproject.databinding.FragmentUserNotificationsBinding
import murat.com.saasproject.databinding.ItemUserNotificationBinding
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.loadRemoteImage
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.Formatters

class UserNotificationsFragment : Fragment(R.layout.fragment_user_notifications) {

    private val binding by viewBinding(FragmentUserNotificationsBinding::bind)
    private val viewModel: UserNotificationsViewModel by viewModels()
    private val adapter = UserNotificationAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.stateLayout.attachContent(binding.swipeRefresh)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.load(getString(R.string.business_fallback_name), forceRefresh = true)
        }

        collectWhileStarted(viewModel.state) { state ->
            binding.swipeRefresh.isRefreshing = false
            binding.stateLayout.bind(
                state = if (state is UiState.Error && state.message.isBlank()) {
                    UiState.Error(getString(R.string.error_no_session))
                } else {
                    state
                },
                emptyTitle = getString(R.string.user_notifications_empty_title),
                emptyDescription = getString(R.string.user_notifications_empty_message),
                emptyIcon = R.drawable.ic_bell,
                onRetry = { viewModel.load(getString(R.string.business_fallback_name), forceRefresh = true) }
            ) { adapter.submitList(it) }
        }

        viewModel.load(getString(R.string.business_fallback_name))
    }
}

private class UserNotificationAdapter :
    ListAdapter<UserNotification, UserNotificationAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemUserNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(private val binding: ItemUserNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UserNotification) {
            binding.titleTextView.text = item.title
            binding.bodyTextView.text = item.body
            binding.detailTextView.text = binding.root.context.getString(
                R.string.notification_detail_format,
                item.businessName,
                Formatters.dateTime(item.createdAt)
            )
            binding.logoImageView.loadRemoteImage(item.businessLogoURL)
        }
    }

    private object Diff : DiffUtil.ItemCallback<UserNotification>() {
        override fun areItemsTheSame(a: UserNotification, b: UserNotification) = a.id == b.id
        override fun areContentsTheSame(a: UserNotification, b: UserNotification) = a == b
    }
}
