package com.black.app.broadcast

import android.content.Intent

/**
 * 앱 업데이트(MY_PACKAGE_REPLACED) 후 ForegroundService 재시작
 *
 * 업데이트 설치가 앱 프로세스를 종료해 서비스도 함께 종료되고 START_STICKY 재생성도 일어나지 않으므로,
 * 실행 설정이 켜져 있으면 여기서 다시 시작
 **/
class AppUpdateReceiver : ForegroundServiceStartReceiver(Intent.ACTION_MY_PACKAGE_REPLACED)
