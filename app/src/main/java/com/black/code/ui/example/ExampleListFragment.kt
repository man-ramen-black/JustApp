package com.black.code.ui.example

import androidx.fragment.app.viewModels
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.black.code.R
import com.black.code.base.component.BaseFragment
import com.black.code.base.viewmodel.EventObserver
import com.black.code.databinding.FragmentExampleListBinding

class ExampleListFragment : BaseFragment<FragmentExampleListBinding>(), EventObserver {

    private val viewModel : ExampleViewModel by viewModels()
    private val adapter by lazy { ExampleListAdapter(viewModel) }
    private val navController by lazy { findNavController() }

    private val exampleFragmentIdList : List<Int> = listOf(
        R.id.serviceFragment,
        R.id.studyPopupFragment,
        R.id.textEditorFragment,
        R.id.usageTimerFragment,
        R.id.notificationFragment,
        R.id.recyclerViewFragment,
        R.id.retrofitFragment,
        R.id.alarmFragment,
        R.id.architectureFragment,
        R.id.launcherFragment,
        R.id.etcFragment,
    )

    override val layoutResId: Int = R.layout.fragment_example_list

    override fun bindVariable(binding: FragmentExampleListBinding) {
        binding.viewModel = viewModel
        viewModel.observeEvent(viewLifecycleOwner, this)

        binding.recyclerView.apply {
            adapter = this@ExampleListFragment.adapter
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        viewModel.initList(toDestinationList(exampleFragmentIdList))
    }

    @Suppress("UNCHECKED_CAST")
    override fun onReceivedEvent(action: String, data: Any?) {
        when (action) {
            ExampleViewModel.EVENT_SUBMIT_LIST -> {
                adapter.submitList(data as List<NavDestination>)
            }
            ExampleViewModel.EVENT_NAVIGATE_FRAGMENT -> {
                navigate(data as NavDestination)
            }
        }
    }

    private fun navigate(destination: NavDestination) {
        navController.navigate(destination.id)
    }

    private fun toDestinationList(destinationIdList: List<Int>) : List<NavDestination> {
        return destinationIdList.mapNotNull { navController.findDestination(it) }
    }

//    private fun initExampleList() {
//        for (contentsFragment in exampleList) {
//            /*
//            Theme, Style 적용하여 뷰 생성
//            val button = Button(ContextThemeWrapper(context, R.style.AppTheme), null, R.style.AppTheme)
//             */
//             // AppTheme이 Material 라이브러리 테마인 경우 Material View를 생성해주면 앱 테마에 맞는 뷰가 생성됨
//            val button = MaterialButton(requireContext())
//                .apply {
//                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
//                    text = contentsFragment.title
//                    setOnClickListener {
//
//                        /*
//                        parentFragmentManager.beginTransaction()
//                            // add는 Fragment가 오버랩되어 추가됨
//                            // 일반적으로 생각하는 add를 하려면 replace -> addToBackStack으로 구현
//                            .replace(R.id.fragmentContainer, contentsFragment)
//                            // addToBackStack은 commit 전에 호출
//                            // FragmentManager.BackStackEntry 등의 기능으로 백스택 추적, 관리하지 않으면 name은 null로 설정
//                            // https://developer.android.com/training/basics/fragments/fragment-ui?hl=ko#Replace
//                            .addToBackStack(null)
//                            .commit()
//                         */
//                    }
//                }
//            contentsContainer.addView(button)
//        }
//    }
}