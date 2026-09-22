package murat.com.saasproject.ui.customer

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentCustomerRewardListBinding
import murat.com.saasproject.ui.common.RewardAdapter
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.viewBinding

class CustomerRewardListFragment : Fragment(R.layout.fragment_customer_reward_list) {

    private val binding by viewBinding(FragmentCustomerRewardListBinding::bind)
    private val viewModel: CustomerRewardListViewModel by viewModels()
    private val adapter = RewardAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val businessId = requireArguments().getString(ARG_BUSINESS_ID).orEmpty()
        val points = requireArguments().getInt(ARG_POINTS)
        val businessName = requireArguments().getString(ARG_BUSINESS_NAME)
            ?: getString(R.string.business_fallback_name)

        binding.toolbar.title = businessName
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.pointsTextView.text = points.toString()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.stateLayout.attachContent(binding.recyclerView)

        collectWhileStarted(viewModel.business) { business ->
            val rules = business?.displayPointEarningRules
            binding.rulesCard.isVisible = !rules.isNullOrBlank()
            binding.rulesTextView.text = rules
        }
        collectWhileStarted(viewModel.state) { state ->
            binding.stateLayout.bind(
                state = state,
                emptyTitle = getString(R.string.reward_list_empty_title),
                emptyDescription = getString(R.string.reward_list_empty_message),
                emptyIcon = R.drawable.ic_gift
            ) { adapter.submitList(it) }
        }

        viewModel.load(businessId)
    }

    companion object {
        const val ARG_BUSINESS_ID = "businessId"
        const val ARG_POINTS = "points"
        const val ARG_BUSINESS_NAME = "businessName"
    }
}
