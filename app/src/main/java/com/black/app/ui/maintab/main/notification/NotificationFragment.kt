package com.black.app.ui.maintab.main.notification

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.viewModels
import com.black.app.ui.MainActivity
import com.black.app.R
import com.black.app.databinding.FragmentNotificationBinding
import com.black.app.ui.common.base.TitleFragment
import com.black.app.util.DialogUtil
import com.black.app.util.NotificationUtil

class NotificationFragment : TitleFragment<FragmentNotificationBinding>() {

    companion object {
        private const val REQUEST_CODE_NOTIFICATION = 920725
        private const val NOTIFICATION_CHANNEL_ID = "Default"
    }

    override val layoutResId: Int = R.layout.fragment_notification
    override val title: String = "Notification"
    private val viewModel : NotificationViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        NotificationUtil.createNotificationChannel(requireContext(), NOTIFICATION_CHANNEL_ID, "Default", NotificationManagerCompat.IMPORTANCE_MAX)
    }

    override fun bindVariable(binding: FragmentNotificationBinding) {
        binding.fragment = this
        binding.viewModel = viewModel
    }

    private fun createContentIntent() : PendingIntent{
        // https://developer.android.com/about/versions/12/behavior-changes-12?hl=ko#notification-trampolines
        // Android 12부터 알림 트램폴린을 방지하기 위해 백그라운드에서 Activity를 노출시킬 수 없음
        val intent = Intent(requireContext(), MainActivity::class.java)
        /*
        [Intent flags 참고]
        https://developer.android.com/reference/android/content/Intent?hl=ko
        https://stackoverflow.com/questions/5370314/android-launchmode-singletask-and-notifications

        [Notification에서 마지막 상태 유지해서 실행(launchMode:singleTop일 때만 동작)]
        https://like-tomato.tistory.com/156
         */
        val pendingIntentFlags =
            (viewModel.contentIntentFlag.value?.value?:0) or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
        return PendingIntent.getActivity(requireContext(), REQUEST_CODE_NOTIFICATION, intent, pendingIntentFlags)
    }

    fun onClickShowNotification(view: View?) {
        NotificationUtil.showNotification(
            requireContext(),
            NOTIFICATION_CHANNEL_ID,
            viewModel.title.value?:"",
            viewModel.message.value?:"",
            viewModel.notificationId.value?:1,
            null,
        ) {
            it.setAutoCancel(true) // 터치 시 알림 제거
                .setContentIntent(createContentIntent())
        }
    }

    fun onClickContentIntentFlag(view: View?) {
        val items = NotificationViewModel.PendingIntentFlag
            .values()
            .map { it.toString() }
            .toTypedArray()

        DialogUtil.showListDialog(requireContext(), "ContentIntentFlag", items) { dialog, which, item ->
            viewModel.contentIntentFlag.value = NotificationViewModel.PendingIntentFlag.valueOf(item)
            binding?.invalidateAll()
        }
    }
}