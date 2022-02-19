package com.black.code.contents.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.viewModels
import com.black.code.MainActivity
import com.black.code.R
import com.black.code.contents.ContentsFragment
import com.black.code.databinding.FragmentNotificationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NotificationFragment : ContentsFragment<FragmentNotificationBinding>() {

    companion object {
        private const val REQUEST_CODE_NOTIFICATION = 920725
    }

    override val layoutResId: Int = R.layout.fragment_notification
    override val title: String = "Notification"
    private val viewModel : NotificationViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.fragment = this
        binding?.viewModel = viewModel
        createNotificationChannel()
    }

    /**
     * https://developer.android.com/training/notify-user/build-notification?hl=ko#Priority
     * 알림 채널이 만들어진 상태라면 아무 작업도 실행되지 않으므로 이 코드를 반복적으로 호출하는 것이 안전합니다.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Default"
            val descriptionText = "Default Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel("Default", name, importance)
                .apply {
                    description = descriptionText
                }

            val notificationManager: NotificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
        val notification = NotificationCompat.Builder(requireContext(), "Default")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(viewModel.title.value?:"")
            .setContentText(viewModel.message.value?:"")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createContentIntent())
            .setAutoCancel(true) // 터치 시 알림 제거
            .build()

        NotificationManagerCompat.from(requireContext())
            .notify(viewModel.notificationId.value?:1, notification)
    }

    fun onClickContentIntentFlag(view: View?) {
        val items = NotificationViewModel.PendingIntentFlag
            .values()
            .map { it.toString() }
            .toTypedArray()

        showSimpleDialog(requireContext(), "ContentIntentFlag", items) { dialog, which, item ->
            viewModel.contentIntentFlag.value = NotificationViewModel.PendingIntentFlag.valueOf(item)
            binding?.invalidateAll()
        }
    }

    fun showSimpleDialog(context: Context, title: String, items: Array<String>, onItemClick: (dialog: DialogInterface, which: Int, item: String) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(items) { dialog, which ->
                onItemClick(dialog, which, items[which])
            }
            .show()
    }
}