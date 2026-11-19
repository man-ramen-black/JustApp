package com.black.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.black.core.model.OverlayPosition
import com.black.core.model.OverlayPosition.HorizontalAnchor
import com.black.core.model.OverlayPosition.VerticalAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object OverlayViewUtil {
    /**
     * 창 배치 값(gravity와 x, y)
     *
     * gravity가 오른쪽·아래이면 x·y는 그 가장자리에서 떨어진 거리이고,
     * 가운데이면 가운데에서 밀린 거리(WindowManager 규칙)
     */
    data class WindowPlacement(val gravity: Int, val x: Int, val y: Int)

    /**
     * 오버레이 뷰 생성용 Context(앱 테마를 입힌 applicationContext)
     *
     * TYPE_APPLICATION_OVERLAY 창은 Activity가 파괴돼도 걷히지 않아 Activity context로 만들면 Activity를 붙잡으므로 applicationContext 사용.
     * applicationContext는 매니페스트 application 테마가 입혀진다는 보장이 없어 그 테마를 직접 입힘.
     * 기능 모듈은 앱 테마 리소스를 참조할 수 없어 리소스 이름 대신 매니페스트 값으로 조회
     */
    fun themedContext(context: Context): Context {
        return appThemedContext(context.applicationContext)
    }

    /**
     * [context]를 바꾸지 않고 매니페스트 application 테마만 입힌 Context
     *
     * 접근성 서비스 창처럼 서비스 context로 만들어야 하는 오버레이 뷰에 씀. 그 밖의 오버레이는 [themedContext] 사용
     */
    fun appThemedContext(context: Context): Context {
        return ContextThemeWrapper(context, context.applicationInfo.theme)
    }

    fun attachView(
        view: View,
        onSetWindowParams: ((windowParams: WindowManager.LayoutParams) -> Unit)? = null
    ) {
        Log.d()

        val context = view.context
        if (view.parent != null) {
            Log.w("view is already attached")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // https://jjun5050.tistory.com/23
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // 컷아웃 영역까지 배치·이동 허용
        // 기본 모드는 가로 화면에서 컷아웃 쪽 가장자리를 x, y 기준 영역에서 빼므로, 시스템 바만 빼는 이동 제한과 어긋나 반대쪽 화면 밖까지 이동됨
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        onSetWindowParams?.invoke(layoutParams)

        Log.d("isCenter : ${layoutParams.gravity and Gravity.CENTER == Gravity.CENTER}")

        windowManager.addView(view, layoutParams)
    }

    /**
     * [attachView]를 시도하고 성공 여부 반환
     *
     * 다른 앱 위에 표시 권한을 확인한 뒤 실제로 붙이기 전에 권한이 회수되면 시스템이 창 추가를 거부(BadTokenException·SecurityException)하므로,
     * 서비스·접근성 서비스의 부착 경로가 프로세스를 죽이지 않도록 실패로 돌려줌
     */
    fun tryAttachView(
        view: View,
        onSetWindowParams: ((windowParams: WindowManager.LayoutParams) -> Unit)? = null
    ): Boolean {
        return try {
            attachView(view, onSetWindowParams)
            true
        } catch (e: WindowManager.BadTokenException) {
            Log.e("attach overlay failed : $e")
            false
        } catch (e: SecurityException) {
            Log.e("attach overlay failed : $e")
            false
        }
    }

    fun updateView(view: View, onUpdate: (windowParams: WindowManager.LayoutParams) -> Unit) {
        val windowParams = view.layoutParams as? WindowManager.LayoutParams
        windowParams ?: run {
            throw ClassCastException("LayoutParams is not WindowManager.LayoutParams")
        }

        onUpdate(windowParams)

        val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.updateViewLayout(view, windowParams)
    }

    fun detachView(view: View) {
        Log.d()

        if (view.parent == null) {
            Log.w("view is not attached")
            return
        }

        val context = view.context
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeView(view)
    }

    /** 화면의 x, y 좌표로 뷰를 이동 */
    @SuppressLint("RtlHardcoded")
    fun moveView(view: View, x: Float, y: Float) {
        updateView(view) {
            it.gravity = Gravity.TOP or Gravity.LEFT
            val screenSize = UiUtil.getCurrentViewportSize(view.context)
            // 뷰가 화면 밖으로 벗어나지 않게 제한
            it.x = min(max(x.toInt(), 0), screenSize.x - view.width)
            it.y = min(max(y.toInt(), 0), screenSize.y - view.height)
        }
    }

    /**
     * Gravity가 right인 경우 x값은 오른쪽에서부터의 x이고, bottom도 아래부터의 y이기 때문에,
     * 화면 사이즈, view size를 계산하여 Top, Left 기준의 x, y 위치를 반환
     * 화면 사이즈는 [moveView]와 같은 기준 영역(시스템 바 제외)이어야 이동 시작 시 뷰가 튀지 않음
     */
    fun getAbsoluteWindowPosition(view: View) : Point {
        val context = view.context
        val screenSize = UiUtil.getCurrentViewportSize(context)

        val windowParams = view.layoutParams as? WindowManager.LayoutParams
            ?: throw ClassCastException("LayoutParams is not WindowManager.LayoutParams")

        return calculateTopLeftPosition(windowParams.gravity, windowParams.x, windowParams.y, screenSize, view.width, view.height)
    }

    /** Gravity 기준 창 x, y를 containerSize 영역의 Top, Left 기준 위치로 변환 */
    @SuppressLint("RtlHardcoded")
    fun calculateTopLeftPosition(
        gravity: Int,
        windowX: Int,
        windowY: Int,
        containerSize: Point,
        viewWidth: Int,
        viewHeight: Int
    ) : Point {
        val x = when {
            gravity and Gravity.LEFT == Gravity.LEFT -> {
                Log.d("gravity is left")
                windowX
            }
            gravity and Gravity.RIGHT == Gravity.RIGHT -> {
                Log.d("gravity is right")
                containerSize.x - viewWidth - windowX
            }
            gravity and Gravity.CENTER_HORIZONTAL == Gravity.CENTER_HORIZONTAL -> {
                Log.d("gravity is center horizontal")
                containerSize.x / 2 - viewWidth / 2 + windowX
            }
            else -> {
                Log.d("gravity is unknown")
                windowX
            }
        }

        val y = when {
            gravity and Gravity.TOP == Gravity.TOP -> {
                Log.d("gravity is top")
                windowY
            }
            gravity and Gravity.BOTTOM == Gravity.BOTTOM -> {
                Log.d("gravity is bottom")
                containerSize.y - viewHeight - windowY
            }
            gravity and Gravity.CENTER_VERTICAL == Gravity.CENTER_VERTICAL -> {
                Log.d("gravity is center vertical")
                containerSize.y / 2 - viewHeight / 2 + windowY
            }
            else -> {
                Log.d("gravity is unknown")
                windowY
            }
        }
        return Point(x, y)
    }

    /**
     * [position]을 창의 gravity와 x, y로 변환
     *
     * 붙은 쪽 여백만 쓰고 가운데 기준은 양쪽 여백의 차이를 절반으로 나눈 오프셋을 씀.
     * 오른쪽·아래 gravity의 x·y는 그 가장자리에서 떨어진 거리라는 WindowManager 규칙을 따름
     */
    @SuppressLint("RtlHardcoded")
    fun calculateWindowPlacement(position: OverlayPosition, density: Float): WindowPlacement {
        val (horizontalGravity, x) = when (position.horizontal) {
            HorizontalAnchor.LEFT -> Gravity.LEFT to (position.marginLeftDp * density).roundToInt()
            HorizontalAnchor.RIGHT -> Gravity.RIGHT to (position.marginRightDp * density).roundToInt()
            HorizontalAnchor.CENTER -> Gravity.CENTER_HORIZONTAL to
                    ((position.marginLeftDp - position.marginRightDp) * density / 2f).roundToInt()
        }
        val (verticalGravity, y) = when (position.vertical) {
            VerticalAnchor.TOP -> Gravity.TOP to (position.marginTopDp * density).roundToInt()
            VerticalAnchor.BOTTOM -> Gravity.BOTTOM to (position.marginBottomDp * density).roundToInt()
            VerticalAnchor.CENTER -> Gravity.CENTER_VERTICAL to
                    ((position.marginTopDp - position.marginBottomDp) * density / 2f).roundToInt()
        }
        return WindowPlacement(horizontalGravity or verticalGravity, x, y)
    }

    /** 현재 창의 배치 값, 창 LayoutParams가 아니면 null */
    fun currentWindowPlacement(view: View): WindowPlacement? {
        val windowParams = view.layoutParams as? WindowManager.LayoutParams ?: return null
        return WindowPlacement(windowParams.gravity, windowParams.x, windowParams.y)
    }

    /**
     * [placement]를 창에 반영
     *
     * [updateView]로 gravity·x·y를 갱신
     */
    fun applyWindowPlacement(view: View, placement: WindowPlacement) {
        updateView(view) {
            it.gravity = placement.gravity
            it.x = placement.x
            it.y = placement.y
        }
    }

    /** [flow]가 예외로 끝나면 [default]를 내보내는 Flow */
    fun positionOrDefault(flow: Flow<OverlayPosition>, default: OverlayPosition): Flow<OverlayPosition> {
        return flow.catch { emit(default) }
    }
}
