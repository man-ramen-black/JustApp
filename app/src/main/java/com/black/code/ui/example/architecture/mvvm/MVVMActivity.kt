package com.black.code.ui.example.architecture.mvvm

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.black.code.ui.example.architecture.CounterModel
import com.black.code.databinding.ActivityMvvmBinding

/**
 * MVVM은 Activity가 View 역할을 담당
 * View는 ViewModel만을 참조함
 * https://velog.io/@its-mingyu/%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C%EB%94%94%EC%9E%90%EC%9D%B8-%ED%8C%A8%ED%84%B4MVC-MVP-MVVM
 */
class MVVMActivity : AppCompatActivity() {
    private var binding : ActivityMvvmBinding? = null

    // ViewModel 초기화
    // activity-ktx 라이브러리로 아래의 코드를 by viewModels()로 축약 가능
    // ViewModelProvider(this).get(MVVMViewModel::class.java)
    private val viewModel : MVVMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Binding 객체 생성
        binding = ActivityMvvmBinding.inflate(LayoutInflater.from(this)).apply {
            // Lifecycle 동기화를 위해 LifecycleOwner 적용
            lifecycleOwner = this@MVVMActivity
            // Binding에 ViewModel 연결
            viewModel = this@MVVMActivity.viewModel.apply {
                // ViewModel에 Model을 설정
                setModel(CounterModel())
            }
        }

        // Activity에 레이아웃 적용
        setContentView(binding!!.root)
    }
}