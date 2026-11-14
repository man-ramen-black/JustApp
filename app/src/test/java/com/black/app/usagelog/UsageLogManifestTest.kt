package com.black.app.usagelog

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.black.app.R
import com.black.test.BaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/** 기기 사용 기록 수집에 필요한 권한과 백업 제외 규칙이 매니페스트에 선언됐는지 검증 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsageLogManifestTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** 병합된 매니페스트가 요청하는 권한 목록 */
    private fun requestedPermissions(): List<String> {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return packageInfo.requestedPermissions?.toList().orEmpty()
    }

    /**
     * 단위 테스트용 병합 매니페스트의 application 요소 속성값
     *
     * Robolectric이 dataExtractionRules를 ApplicationInfo에 채우지 않아 AGP가 넘기는 병합 매니페스트 파일을 직접 읽음.
     * 속성이 없으면 빈 문자열.
     */
    private fun mergedApplicationAttribute(name: String): String {
        val config = Properties()
        javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties").use { config.load(it) }
        val manifest = File(config.getProperty("android_merged_manifest"))
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element
        return application.getAttributeNS("http://schemas.android.com/apk/res/android", name)
    }

    /**
     * 백업 규칙 XML의 항목을 `구역 종류 도메인 경로` 문자열로 모음
     *
     * 구역은 항목을 감싼 요소 이름이며, fullBackupContent 파일은 루트인 full-backup-content가 구역.
     */
    private fun backupRuleEntries(xmlResId: Int): List<String> {
        val parser = context.resources.getXml(xmlResId)
        val entries = ArrayList<String>()
        val sections = ArrayList<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "include" || parser.name == "exclude") {
                        val domain = parser.getAttributeValue(null, "domain")
                        val path = parser.getAttributeValue(null, "path")
                        entries.add("${sections.last()} ${parser.name} $domain $path")
                    }
                    sections.add(parser.name)
                }
                XmlPullParser.END_TAG -> sections.removeAt(sections.lastIndex)
            }
        }
        return entries
    }

    /** 기기 사용 기록 DB와 설정 파일을 [section]에서 빼는 항목 */
    private fun usageLogExclusions(section: String): List<String> = listOf(
        "$section exclude database UsageLog.db",
        "$section exclude database UsageLog.db-wal",
        "$section exclude database UsageLog.db-shm",
        "$section exclude database UsageLog.db-journal",
        "$section exclude file datastore/usage_log.preferences_pb",
    )

    /**
     * 설정 앱의 사용 정보 접근 화면에서 앱을 허용할 수 있도록 사용 정보 접근 권한이 선언됐는지 검증
     *
     * Given: 병합된 앱 매니페스트
     * When: 요청 권한 목록 조회
     * Then: PACKAGE_USAGE_STATS 포함
     */
    @Test
    fun test_01_declaresUsageStatsPermission() = runTest {
        /** Given / When **/
        val permissions = requestedPermissions()

        /** Then **/
        assertTrue(permissions.contains(Manifest.permission.PACKAGE_USAGE_STATS))
    }

    /**
     * Wi-Fi 이름을 읽는 데 필요한 위치 권한 세 가지가 선언됐는지 검증
     *
     * Given: 병합된 앱 매니페스트
     * When: 요청 권한 목록 조회
     * Then: 대략적인 위치, 정확한 위치, 백그라운드 위치 포함
     */
    @Test
    fun test_02_declaresLocationPermissions() = runTest {
        /** Given / When **/
        val permissions = requestedPermissions()

        /** Then **/
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    /**
     * 기기 사용 기록 DB와 설정 파일만 클라우드 백업과 기기 이전에서 빠지는지 검증
     *
     * Given: 병합된 앱 매니페스트와 백업 규칙 파일(API 30 이하 fullBackupContent, API 31 이상 dataExtractionRules)
     * When: 백업 허용 여부, 매니페스트가 가리키는 규칙 파일, 두 규칙 파일의 항목 조회
     * Then: 백업 허용 유지, 두 속성이 규칙 파일을 가리킴, 모든 구역에서 UsageLog.db와 그 -wal·-shm·-journal, usage_log DataStore 파일 제외, include 없음
     */
    @Test
    fun test_03_excludesUsageLogFromBackup() = runTest {
        /** Given **/
        val applicationInfo = context.applicationInfo

        /** When **/
        val fullBackupContent = mergedApplicationAttribute("fullBackupContent")
        val dataExtractionRules = mergedApplicationAttribute("dataExtractionRules")
        val fullBackupEntries = backupRuleEntries(R.xml.backup_rules)
        val dataExtractionEntries = backupRuleEntries(R.xml.data_extraction_rules)

        /** Then **/
        assertTrue(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertEquals("@xml/backup_rules", fullBackupContent)
        assertEquals("@xml/data_extraction_rules", dataExtractionRules)
        assertEquals(usageLogExclusions("full-backup-content").toSet(), fullBackupEntries.toSet())
        assertEquals(
            (usageLogExclusions("cloud-backup") + usageLogExclusions("device-transfer")).toSet(),
            dataExtractionEntries.toSet(),
        )
    }
}
