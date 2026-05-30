# 호스팅 템플릿

Fragment를 `HiltTestActivity`에 띄우는 헬퍼 두 패턴. 생명주기 중 `findNavController()`에 도달하면 **패턴 A**, 아니면 **패턴 B**. 애매하면 A(NavController가 붙어 있어도 무해하지만, 없는데 호출되면 크래시).

## 패턴 A — NavController 사용

```kotlin
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelStore
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertDisplayed
import com.black.app.testutil.assertEffectiveGone
import com.black.app.testutil.assertEffectiveVisible
import com.black.app.testutil.assertText
import com.black.app.testutil.clickView
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class XxxFragmentTest : BaseUiTest() {

    private lateinit var navController: TestNavHostController

    // ... @Test 함수들 ...

    /**
     * XxxFragment를 HiltTestActivity에 호스팅하고 [navController] 부착
     *
     * Fragment가 onViewCreated에서 findNavController()를 사용하므로, View 생성 전에 컨테이너에 NavController 부착 필요
     */
    private fun hostXxxFragment() {
        onHostActivity { activity ->
            navController = TestNavHostController(activity).apply {
                // savedStateHandle(popBackStack 결과 observe) 접근에 ViewModelStore 필요
                setViewModelStore(ViewModelStore())
                setGraph(R.navigation.root_navigation)
                setCurrentDestination(R.id.xxx_fragment)
            }
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)
            Navigation.setViewNavController(container, navController)

            activity.supportFragmentManager.beginTransaction()
                .add(container.id, XxxFragment(), "xxx")
                .commitNow()
        }
    }
}
```

채울 값: `R.id.xxx_fragment`(그래프상 이 Fragment destination id), `setGraph(...)`(그 destination이 속한 그래프). `setViewModelStore(...)` 줄은 popBackStack 결과를 observe하는 화면에만 필요하고 아니면 제거.

네비게이션 검증:

```kotlin
@Test
fun test_03_clickXxxNavigatesToYyy() {
    /** Given **/
    hostXxxFragment()

    /** When **/
    // 하단에 있을 수 있어 보이도록 스크롤 후 클릭
    clickView(R.id.xxx_button, scrollTo = true)

    /** Then **/
    assertEquals(
        "xxx_button 클릭 후 yyy로 네비게이션되지 않았습니다",
        R.id.yyy_destination,
        navController.currentDestination?.id,
    )
}
```

## 패턴 B — NavController 미사용

`navController` 필드와 부착을 생략. 네비게이션 검증이 없으면 import에서 `androidx.navigation.*`·`ViewModelStore`·`assertEquals`도 빠진다.

```kotlin
import android.view.View
import android.widget.FrameLayout
import com.black.app.R
import com.black.app.testutil.BaseUiTest
import com.black.app.testutil.assertDisplayed
import com.black.app.testutil.assertText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@HiltAndroidTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class XxxFragmentTest : BaseUiTest() {

    // ... @Test 함수들 ...

    /** XxxFragment를 HiltTestActivity에 호스팅 */
    private fun hostXxxFragment() {
        onHostActivity { activity ->
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)

            activity.supportFragmentManager.beginTransaction()
                .add(container.id, XxxFragment(), "xxx")
                .commitNow()
        }
    }
}
```

## 헬퍼 확장 (EspressoExtensions)

필요한 헬퍼가 없으면 raw `onView` 대신 `EspressoExtensions.kt`에 기존 패턴대로 추가한다(실패 메시지에 뷰 이름+한국어 사유 포함).

```kotlin
/** [viewId] 뷰의 hint가 [hint]와 일치하는지 검증 */
fun assertHint(@IdRes viewId: Int, hint: String): ViewInteraction =
    checkView(viewId, "hint가 \"$hint\"와 일치하지 않습니다", matches(withHint(hint)))

/** [viewId] 뷰의 텍스트를 모두 지움 */
fun clearText(@IdRes viewId: Int): ViewInteraction =
    performOnView(viewId, "텍스트 삭제에 실패했습니다", ViewActions.clearText())
```

`matches(...)` matcher(`withHint` 등)·`ViewActions.*`는 사용 시 import 추가.
