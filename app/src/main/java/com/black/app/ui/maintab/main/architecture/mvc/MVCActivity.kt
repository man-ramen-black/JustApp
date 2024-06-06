package com.black.app.ui.maintab.main.architecture.mvc

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.black.app.R
import com.black.app.ui.maintab.main.architecture.CounterModel

/**
 * MVC에서는 Activity가 View와 Controller의 역할을 모두 담당
 * https://blog.crazzero.com/m/152
 */
class MVCActivity : AppCompatActivity() {
    // Model 초기화
    private val model by lazy { CounterModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout xml 적용
        setContentView(R.layout.activity_mvc)

        // 버튼을 터치했을 때 동작 설정
        val countTextView = findViewById<TextView>(R.id.count_text_view)
        val countButton = findViewById<Button>(R.id.count_button)
        countButton.setOnClickListener {
            // Model을 통해 값을 증가시키고, 증가시킨 값을 텍스트뷰에 설정
            countTextView.text = model.addCount().toString()
        }
    }
}