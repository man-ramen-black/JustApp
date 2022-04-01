package com.black.code.contents.architecture.mvc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.black.code.R
import com.black.code.contents.architecture.CounterModel
import kotlinx.android.synthetic.main.activity_mvc.*

/**
 * MVC에서는 Activity, Layout xml이 View와 Controller의 역할을 모두 담당
 * https://blog.crazzero.com/m/152
 */
class MVCActivity : AppCompatActivity() {
    // Model 초기화
    private val model by lazy { CounterModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mvc)

        // 텍스트뷰에 초기 카운트(0)을 설정
        countTextView.text = model.count.toString()

        // 버튼을 터치했을 때 동작 설정
        countButton.setOnClickListener {
            // Model을 통해 값을 증가시키고, 증가시킨 값을 텍스트뷰에 설정
            countTextView.text = model.addCount().toString()
        }
    }
}