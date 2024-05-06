package com.black.app.ui.example.etc

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.black.app.R
import com.black.app.databinding.FragmentEtcBinding
import com.black.app.ui.example.ExampleFragment
import com.black.app.ui.MainActivity
import com.black.core.util.PermissionHelper

class ETCFragment : ExampleFragment<FragmentEtcBinding>() {
    override val title: String = "ETC"
    override val layoutResId: Int = R.layout.fragment_etc

    override fun bindVariable(binding: FragmentEtcBinding) {
        binding.fragment = this
    }

    fun onClickShowEtcActivity() {
        startActivity(Intent(requireActivity(), ETCActivity::class.java))
    }

    /**
     * SplashActivity onNewIntent Lifecycle 테스트용
     */
    fun onClickRestartApp() {
        ActivityCompat.finishAffinity(requireActivity())

        val activity = requireActivity()
        activity.startActivity(Intent(activity, MainActivity::class.java))
        Handler(Looper.getMainLooper()).postDelayed({
            activity.startActivity(Intent(activity, MainActivity::class.java))
        }, 50)
    }

    fun onClickBatteryOptimizationsSetting() {
        PermissionHelper.showIgnoreBatteryOptimizationSettings(requireContext())
    }
}