package com.black.study

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.NumberFormatException

@SuppressLint("CheckResult")
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        simpleInput.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                Observable.just(text)
                    .map { text -> Integer.parseInt("$text") }
                    .onErrorReturn { 0 }
                    .flatMap { num ->
                        Observable.range(1, 9)
                        .map { dan -> "$num x $dan = ${num*dan}\n"}}
                    .scan{x, y -> x + y}
                    .subscribe{result -> simpleResult.text = result}
            }//
        })

        /**
         * BehaviorSubject : 구독하면 최근 항목부터 발행 시작
         * PublishSubject : 구독하면 구독 시작 후 발행된 항목을 발행 시작 (구독 시작 전 발행된 데이터 소실)
         * http://reactivex.io/documentation/ko/subject.html
         */
        val left = PublishSubject.create<Int>()
        val right = PublishSubject.create<Int>()

        Observable.combineLatest(left, right,
            BiFunction<Int, Int, String>{leftVal, rightVal ->
                "$leftVal x $rightVal = ${leftVal*rightVal}"})
            .onErrorReturn { e -> e.message }
            .subscribe{result -> mergeResult.text = result}

        leftInput.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                var num = 0
                try {
                    num = Integer.parseInt("$text")
                }catch (e : NumberFormatException){
                    e.printStackTrace()
                }
                left.onNext(num)
            }
        })

        rightInput.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                var num = 0
                try {
                    num = Integer.parseInt("$text")
                }catch (e : NumberFormatException){
                    e.printStackTrace()
                }
                right.onNext(num)
            }
        })
    }
}