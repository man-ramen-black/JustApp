package com.black.core.viewmodel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/** Fragment용 [collectEvents]의 뷰 수명주기에 따른 이벤트 수집 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EventViewModelFragmentTest : BaseTest() {

    /** 테스트용 이벤트 */
    private data class TestEvent(val value: Int) : ViewModelEvent

    /** onViewCreated에서 collectEvents로 이벤트를 받아 기록하는 Fragment */
    class CollectingFragment : Fragment() {
        val viewModel = EventViewModel()
        val receivedEvents = mutableListOf<ViewModelEvent>()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            return View(requireContext())
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            collectEvents(viewModel) { receivedEvents.add(it) }
        }
    }

    /** RESUMED Activity에 [fragment]를 붙이고 수집 시작까지 대기 */
    private fun launchFragment(fragment: Fragment): ActivityController<FragmentActivity> {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.get().supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        waitCoroutine()
        return controller
    }

    /**
     * 뷰가 STARTED 이상이면 보낸 이벤트를 바로 수신하는지 검증
     *
     * Given: RESUMED Activity에 붙은 Fragment
     * When: TestEvent(1) 전송
     * Then: Fragment가 TestEvent(1) 수신
     */
    @Test
    fun test_01_receivesEventWhileStarted() = runTest {
        /** Given **/
        val fragment = CollectingFragment()
        launchFragment(fragment)

        /** When **/
        fragment.viewModel.sendEvent(TestEvent(1))
        waitCoroutine()

        /** Then **/
        assertEquals(listOf(TestEvent(1)), fragment.receivedEvents)
    }

    /**
     * STARTED 미만에서 보낸 이벤트는 수신하지 않다가 다시 STARTED가 되면 수신하는지 검증
     *
     * Given: Fragment를 붙인 Activity를 STOPPED로 전환
     * When: TestEvent(2) 전송 후 Activity를 다시 시작
     * Then: 정지 중에는 미수신, 재시작 뒤 TestEvent(2) 수신
     */
    @Test
    fun test_02_holdsEventUntilStartedAgain() = runTest {
        /** Given **/
        val fragment = CollectingFragment()
        val controller = launchFragment(fragment)
        controller.pause().stop()
        waitCoroutine()

        /** When **/
        fragment.viewModel.sendEvent(TestEvent(2))
        waitCoroutine()
        val receivedWhileStopped = fragment.receivedEvents.toList()
        controller.start()
        waitCoroutine()

        /** Then **/
        assertTrue(receivedWhileStopped.isEmpty())
        assertEquals(listOf(TestEvent(2)), fragment.receivedEvents)
    }

    /**
     * 뷰가 파기되면 수집을 끝내는지 검증
     *
     * Given: RESUMED Activity에 붙은 Fragment
     * When: Fragment 뷰 파기(detach) 후 TestEvent(3) 전송
     * Then: TestEvent(3) 미수신
     */
    @Test
    fun test_03_stopsCollectingAfterViewDestroyed() = runTest {
        /** Given **/
        val fragment = CollectingFragment()
        val controller = launchFragment(fragment)

        /** When **/
        controller.get().supportFragmentManager
            .beginTransaction()
            .detach(fragment)
            .commitNow()
        waitCoroutine()
        fragment.viewModel.sendEvent(TestEvent(3))
        waitCoroutine()

        /** Then **/
        assertTrue(fragment.receivedEvents.isEmpty())
    }
}
