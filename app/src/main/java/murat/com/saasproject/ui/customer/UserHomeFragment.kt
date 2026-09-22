package murat.com.saasproject.ui.customer

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentUserHomeBinding
import murat.com.saasproject.ui.account.AccountMenuBottomSheet
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.PushTokenManager

class UserHomeFragment : Fragment(R.layout.fragment_user_home) {

    private val binding by viewBinding(FragmentUserHomeBinding::bind)
    private val viewModel: UserHomeViewModel by viewModels()
    private val adapter = UserHomeAdapter { business, points ->
        findNavController().navigate(
            R.id.action_home_to_rewardList,
            bundleOf(
                "businessId" to business.id,
                "points" to points,
                "businessName" to business.name.ifBlank { getString(R.string.business_fallback_name) }
            )
        )
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) syncPushToken()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.toolbar.setNavigationOnClickListener { AccountMenuBottomSheet.show(this) }
        binding.notificationsButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_notifications)
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        collectWhileStarted(viewModel.content) { content -> adapter.submitList(buildRows(content)) }
        collectWhileStarted(viewModel.isRefreshing) { binding.swipeRefresh.isRefreshing = it }
        collectEvents(viewModel.events) { message ->
            if (message.isNotBlank()) showSnackbar(requireContext().resolveErrorMessage(message))
        }

        viewModel.start()
        requestNotificationPermissionIfNeeded()
    }

    /** QR okutulunca listener yetmezse katalog/puan listesini tazeler. */
    fun refreshAfterQrScan() {
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (PushTokenManager.hasNotificationPermission(requireContext())) syncPushToken()
    }

    private fun buildRows(content: UserHomeContent): List<UserHomeRow> = buildList {
        add(UserHomeRow.Header(getString(R.string.user_home_my_businesses)))
        if (content.myBusinesses.isEmpty()) {
            add(UserHomeRow.Placeholder(getString(R.string.user_home_my_businesses_empty)))
        } else {
            addAll(content.myBusinesses)
        }
        add(UserHomeRow.Header(getString(R.string.user_home_all_businesses)))
        if (content.discoverBusinesses.isEmpty()) {
            add(UserHomeRow.Placeholder(getString(R.string.user_home_all_businesses_empty)))
        } else {
            addAll(content.discoverBusinesses)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!PushTokenManager.isNotificationPermissionRequired()) {
            syncPushToken()
            return
        }
        if (PushTokenManager.hasNotificationPermission(requireContext())) {
            syncPushToken()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun syncPushToken() {
        viewLifecycleOwner.lifecycleScope.launch {
            PushTokenManager.syncTokenForCurrentUser(requireContext())
        }
    }
}
