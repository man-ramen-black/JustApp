package com.black.app.shortcut

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Xml
import androidx.annotation.StringRes
import com.black.app.R
import com.black.app.deeplink.Deeplink
import com.black.app.ui.MainActivity
import com.black.app.ui.navigation.AppRoute
import com.black.core.service.BaseForegroundService
import com.black.core.service.ServiceActivity
import com.black.feature.floatingbutton.service.FloatingForegroundService
import com.black.feature.sleeproutine.service.SleepRoutineService
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser
import java.io.File
import com.black.feature.floatingbutton.R as FloatingR

/**
 * res/xml/shortcuts.xml 정적 바로가기의 ID·이름과, 인텐트가 실제로 여는 화면·서비스 검증
 *
 * 정적 바로가기는 패키지·클래스·extra 이름을 문자열로 적어 코드가 바뀌어도 따라가지 않으므로 이 테스트로 확인.
 * testDebugUnitTest에서는 리소스로 debug 소스셋 파일(개발 앱판)을 읽으므로, main 파일은 debug 파일과 패키지만 다른지 원문으로 비교
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StaticShortcutsTest : BaseTest() {
    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val SHORTCUTS_META_DATA = "android.app.shortcuts"
        const val STABLE_PACKAGE = "com.black.app"
        const val DEV_PACKAGE = "com.black.app.dev"

        /** 바로가기 ID(바꾸면 이 정의로 홈 화면에 고정한 바로가기가 비활성화됨) */
        const val MEMO_ID = "shortcut_memo"
        const val SLEEP_ROUTINE_ID = "shortcut_sleep_routine"
        const val FLOATING_ON_ID = "floating_on"
        const val FLOATING_OFF_ID = "floating_off"
    }

    /** shortcuts.xml에서 읽은 바로가기 한 개 */
    private data class StaticShortcut(val id: String, @StringRes val shortLabelRes: Int, val intent: Intent)

    private val application = RuntimeEnvironment.getApplication()

    /**
     * 런처가 정적 바로가기를 읽는 MainActivity meta-data가 shortcuts.xml을 가리키는지 검증
     *
     * Given: 앱 매니페스트
     * When: MainActivity의 meta-data 조회
     * Then: android.app.shortcuts 값이 R.xml.shortcuts
     */
    @Test
    fun test_01_mainActivityDeclaresShortcuts() = runTest {
        /** Given **/
        val component = ComponentName(application, MainActivity::class.java)

        /** When **/
        val metaData = application.packageManager.getActivityInfo(component, PackageManager.GET_META_DATA).metaData

        /** Then **/
        assertEquals(R.xml.shortcuts, metaData.getInt(SHORTCUTS_META_DATA))
    }

    /**
     * 정적 바로가기 ID와 이름이 기존 바로가기 네 개와 같은 순서로 선언됐는지 검증
     *
     * Given: shortcuts.xml
     * When: 바로가기 목록 파싱
     * Then: 메모, 수면 루틴, 플로팅 On, 플로팅 Off 순서의 ID·이름
     */
    @Test
    fun test_02_declaresIdsAndLabelsInOrder() = runTest {
        /** Given / When **/
        val shortcuts = parseStaticShortcuts()

        /** Then **/
        assertEquals(listOf(MEMO_ID, SLEEP_ROUTINE_ID, FLOATING_ON_ID, FLOATING_OFF_ID), shortcuts.map { it.id })
        val expectedLabels = listOf(
            R.string.shortcut_memo_label,
            R.string.shortcut_sleep_routine_label,
            FloatingR.string.shortcut_floating_on_label,
            FloatingR.string.shortcut_floating_off_label,
        ).map { application.getString(it) }
        assertEquals(expectedLabels, shortcuts.map { application.getString(it.shortLabelRes) })
    }

    /**
     * 메모 바로가기가 이 앱의 MainActivity를 메모 화면 딥링크로 여는지 검증
     *
     * Given: shortcuts.xml의 메모 바로가기
     * When: 인텐트의 딥링크 파싱
     * Then: MainActivity 대상, ACTION_VIEW, TextEditor로 백스택 정리 후 이동
     */
    @Test
    fun test_03_memoOpensTextEditor() = runTest {
        /** Given **/
        val intent = findStaticShortcut(MEMO_ID).intent

        /** When **/
        val deeplink = Deeplink.parse(intent.data)

        /** Then **/
        assertEquals(ComponentName(application, MainActivity::class.java), intent.component)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Deeplink.Navigate(AppRoute.TextEditor, clearBackStack = true), deeplink)
    }

    /**
     * 수면 루틴 바로가기가 ServiceActivity를 거쳐 수면 루틴 서비스를 시작 인텐트로 실행하는지 검증
     *
     * Given: shortcuts.xml의 수면 루틴 바로가기
     * When: 바로가기 인텐트로 ServiceActivity 실행
     * Then: ServiceActivity 대상, ACTION_VIEW, SleepRoutineService 시작 인텐트에 EXTRA_STOP 없음
     */
    @Test
    fun test_04_sleepRoutineStartsService() = runTest {
        /** Given **/
        val intent = findStaticShortcut(SLEEP_ROUTINE_ID).intent

        /** When **/
        val serviceIntent = launchServiceActivity(intent)

        /** Then **/
        assertEquals(ComponentName(application, ServiceActivity::class.java), intent.component)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName(application, SleepRoutineService::class.java), serviceIntent.component)
        assertFalse(serviceIntent.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /**
     * 플로팅 On 바로가기가 ServiceActivity를 거쳐 플로팅 버튼 서비스를 시작 인텐트로 실행하는지 검증
     *
     * Given: shortcuts.xml의 플로팅 On 바로가기
     * When: 바로가기 인텐트로 ServiceActivity 실행
     * Then: ServiceActivity 대상, ACTION_VIEW, FloatingForegroundService 시작 인텐트에 EXTRA_STOP 없음
     */
    @Test
    fun test_05_floatingOnStartsService() = runTest {
        /** Given **/
        val intent = findStaticShortcut(FLOATING_ON_ID).intent

        /** When **/
        val serviceIntent = launchServiceActivity(intent)

        /** Then **/
        assertEquals(ComponentName(application, ServiceActivity::class.java), intent.component)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName(application, FloatingForegroundService::class.java), serviceIntent.component)
        assertFalse(serviceIntent.hasExtra(BaseForegroundService.EXTRA_STOP))
    }

    /**
     * 플로팅 Off 바로가기가 ServiceActivity를 거쳐 플로팅 버튼 서비스를 중지 인텐트로 실행하는지 검증
     *
     * Given: shortcuts.xml의 플로팅 Off 바로가기
     * When: 바로가기 인텐트로 ServiceActivity 실행
     * Then: ServiceActivity 대상, ACTION_VIEW, FloatingForegroundService 시작 인텐트에 EXTRA_STOP true
     */
    @Test
    fun test_06_floatingOffStopsService() = runTest {
        /** Given **/
        val intent = findStaticShortcut(FLOATING_OFF_ID).intent

        /** When **/
        val serviceIntent = launchServiceActivity(intent)

        /** Then **/
        assertEquals(ComponentName(application, ServiceActivity::class.java), intent.component)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName(application, FloatingForegroundService::class.java), serviceIntent.component)
        assertTrue(serviceIntent.getBooleanExtra(BaseForegroundService.EXTRA_STOP, false))
    }

    /**
     * 실사용 앱이 쓰는 main 소스셋 shortcuts.xml이 debug 파일과 인텐트 패키지만 다른지 검증
     *
     * Given: main·debug 소스셋의 shortcuts.xml 원문(주석 제외)
     * When: debug 파일의 개발 앱 패키지를 실사용 앱 패키지로 바꿔 비교
     * Then: 두 파일 내용 일치, main 파일에 개발 앱 패키지 없음
     */
    @Test
    fun test_07_mainFileDiffersOnlyInPackage() = runTest {
        /** Given **/
        val mainXml = readShortcutsSource("main")
        val debugXml = readShortcutsSource("debug")

        /** When **/
        val debugXmlForStable = debugXml.replace("\"$DEV_PACKAGE\"", "\"$STABLE_PACKAGE\"")

        /** Then **/
        assertEquals(debugXmlForStable, mainXml)
        assertFalse(mainXml.contains(DEV_PACKAGE))
    }

    /** 모듈 디렉터리(유닛 테스트 작업 디렉터리) 기준 [sourceSet] 소스셋 shortcuts.xml 원문에서 주석을 빼고 공백을 정리한 내용 */
    private fun readShortcutsSource(sourceSet: String): String =
        File("src/$sourceSet/res/xml/shortcuts.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** shortcuts.xml의 바로가기를 선언 순서대로 파싱 */
    private fun parseStaticShortcuts(): List<StaticShortcut> {
        val shortcuts = mutableListOf<StaticShortcut>()
        application.resources.getXml(R.xml.shortcuts).use { parser ->
            var id: String? = null
            var shortLabelRes = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                when (parser.name) {
                    "shortcut" -> {
                        id = parser.getAttributeValue(ANDROID_NAMESPACE, "shortcutId")
                        shortLabelRes = parser.getAttributeResourceValue(ANDROID_NAMESPACE, "shortcutShortLabel", 0)
                    }
                    // 시스템 ShortcutParser와 같은 Intent.parseIntent로 해석해 extra 값 타입(boolean 등)까지 같게 읽음
                    "intent" -> shortcuts += StaticShortcut(
                        id = checkNotNull(id) { "intent outside shortcut" },
                        shortLabelRes = shortLabelRes,
                        intent = Intent.parseIntent(application.resources, parser, Xml.asAttributeSet(parser)),
                    )
                }
            }
        }
        return shortcuts
    }

    private fun findStaticShortcut(id: String): StaticShortcut =
        parseStaticShortcuts().single { it.id == id }

    /** 바로가기 인텐트로 ServiceActivity를 실행해 시작된 서비스 인텐트 */
    private fun launchServiceActivity(shortcutIntent: Intent): Intent {
        Robolectric.buildActivity(ServiceActivity::class.java, shortcutIntent).create()
        return shadowOf(application).nextStartedService
    }
}
