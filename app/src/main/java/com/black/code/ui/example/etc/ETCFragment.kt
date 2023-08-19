package com.black.code.ui.example.etc

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.black.code.R
import com.black.code.databinding.FragmentEtcBinding
import com.black.code.ui.example.ExampleFragment
import com.black.code.ui.MainActivity
import com.black.code.util.PermissionHelper

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