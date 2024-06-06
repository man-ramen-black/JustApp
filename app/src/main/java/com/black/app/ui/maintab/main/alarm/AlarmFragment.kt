package com.black.app.ui.maintab.main.alarm

import android.app.AlarmManager
import android.content.Context
import android.view.View
import com.black.app.R
import com.black.app.databinding.FragmentAlarmBinding
import com.black.app.ui.common.base.TitleFragment
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*


class AlarmFragment : TitleFragment<FragmentAlarmBinding>() {
    override val title: String = "Alarm"
    override val layoutResId: Int = R.layout.fragment_alarm

    override fun bindVariable(binding: FragmentAlarmBinding) {
        binding.fragment = this
    }

    fun onClickGetNextAlarm(view: View?) {
        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val alarmInfo = alarmManager?.nextAlarmClock
        if (alarmInfo == null) {
            binding?.nextAlarmText = "alarmInfo is null";
            return
        }

        val alarmDate = Date(alarmInfo.triggerTime)
        val alarmDateText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(alarmDate)
        val alarmIntent = alarmInfo.showIntent
        binding?.nextAlarmText = "alarmTime : $alarmDateText\n" +
                "alarmPackage : ${alarmIntent.creatorPackage}"
    }

    /**
     * shell 커맨드로 알람 dump 조회
     * 아래의 권한을 adb로 부여해야 함
     * adb shell pm grant com.black.study android.permission.DUMP
     * adb shell pm grant com.black.study android.permission.PACKAGE_USAGE_STATS
     * adb 권한 부여 때문에 앱 개발 시 사용은 부적합...
     * dump 내용을 활용하기 난해함
     */
    fun onClickGetAlarmDump(view: View?) {
        try {
            val process = Runtime.getRuntime()
                .exec("dumpsys alarm")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var read: Int
            val buffer = CharArray(4096)
            val output = StringBuffer()
            while (reader.read(buffer).also { read = it } > 0) {
                output.append(buffer, 0, read)
            }
            reader.close()

            // Waits for the command to finish.
            process.waitFor()

            binding?.alarmDumpText = output.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            binding?.alarmDumpText = e.stackTraceToString()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            binding?.alarmDumpText = e.stackTraceToString()
        }
    }
}