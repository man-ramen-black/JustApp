package com.black.app.ui.example

import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.app.R
import com.black.core.component.BaseFragment
import com.black.core.viewmodel.EventObserver
import com.black.app.databinding.FragmentExampleListBinding

class ExampleListFragment : com.black.core.component.BaseFragment<FragmentExampleListBinding>(),
    com.black.core.viewmodel.EventObserver {

    // static list이고, 리소스 디펜던시가 있기 때문에 View에서 관리
    private val itemList = listOf(
        ExampleListAdapter.Item(R.string.fragment_name_text_editor, R.drawable.ic_editor, ExampleListFragmentDirections.actionTextEditor()),
        ExampleListAdapter.Item(R.string.fragment_name_black, R.drawable.ic_invisible, ExampleListFragmentDirections.actionBlack()),
        ExampleListAdapter.Item(R.string.fragment_name_service, R.drawable.ic_android, ExampleListFragmentDirections.actionService()),
        ExampleListAdapter.Item(R.string.fragment_name_study_popup, R.drawable.ic_quiz, ExampleListFragmentDirections.actionStudyPopup()),
        ExampleListAdapter.Item(R.string.fragment_name_usage_timer, R.drawable.ic_timer, ExampleListFragmentDirections.actionUsageTimer()),
        ExampleListAdapter.Item(R.string.fragment_name_notification, R.drawable.ic_notification, ExampleListFragmentDirections.actionNotification()),
        ExampleListAdapter.Item(R.string.fragment_name_recycler_view, R.drawable.ic_list, ExampleListFragmentDirections.actionRecyclerView()),
        ExampleListAdapter.Item(R.string.fragment_name_retrofit, R.drawable.ic_http, ExampleListFragmentDirections.actionRetrofit()),
        ExampleListAdapter.Item(R.string.fragment_name_alarm, R.drawable.ic_alarm, ExampleListFragmentDirections.actionAlarm()),
        ExampleListAdapter.Item(R.string.fragment_name_architecture, R.drawable.ic_architecture, ExampleListFragmentDirections.actionArchitecture()),
        ExampleListAdapter.Item(R.string.fragment_name_launcher, R.drawable.ic_home, ExampleListFragmentDirections.actionLauncher()),
        ExampleListAdapter.Item(R.string.fragment_name_etc, R.drawable.ic_etc, ExampleListFragmentDirections.actionEtc())
    )

    private val viewModel : ExampleViewModel by viewModels()
    private val adapter by lazy { ExampleListAdapter(viewModel) }
    private val navController by lazy { findNavController() }

    override val layoutResId: Int = R.layout.fragment_example_list

    override fun bindVariable(binding: FragmentExampleListBinding) {
        binding.viewModel = viewModel
        viewModel.observeEvent(viewLifecycleOwner, this)

        binding.recyclerView.apply {
            adapter = this@ExampleListFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        adapter.submitList(itemList)
    }

    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            ExampleViewModel.EVENT_NAVIGATE_DIRECTION -> {
                navController.navigate(data as NavDirections)
            }
        }
    }
}