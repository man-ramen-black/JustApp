package com.black.code.ui.example.etc

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.black.code.R
import com.black.code.databinding.FragmentEtcBinding
import com.black.code.ui.SplashActivity

class ETCFragment : com.black.code.ui.example.ExampleFragment<FragmentEtcBinding>() {
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
        activity.startActivity(Intent(activity, SplashActivity::class.java))
        Handler(Looper.getMainLooper()).postDelayed({
            activity.startActivity(Intent(activity, SplashActivity::class.java))
        }, 50)
    }
}