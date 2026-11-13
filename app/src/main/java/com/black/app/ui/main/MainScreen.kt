package com.black.app.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.black.app.R
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppShapes
import com.black.core.ui.theme.AppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [MainScreen] 요소의 testTag
 *
 * 화면과 계측 테스트가 같은 값을 쓰도록 상수로 공유
 */
internal object MainTestTags {
    const val DATE = "main_date"
}

/** 메인 리스트 항목 */
data class MainItem(
    val name: String,
    @DrawableRes val iconResId: Int,
    val onClick: () -> Unit,
)

/**
 * [MainFragment]가 표시하는 메인 화면
 *
 * 헤더 + 기능 리스트 + 푸터 구성
 */
@Composable
fun MainScreen(
    itemList: List<MainItem>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.ScreenBackground),
    ) {
        // 팝업 창 등으로 높이가 부족하면 스크롤로 하단 항목까지 접근
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 높이가 충분하면 위아래 weight 여백으로 리스트를 가운데 배치하도록 최소 높이를 화면 높이로 지정
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
        ) {
            MainHeader()
            Spacer(modifier = Modifier.weight(1f))
            MainItemList(
                itemList = itemList,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.weight(1f))
            MainFooter(itemCount = itemList.size)
        }
    }
}

/** 모노스페이스 날짜 + 타이틀 헤더 */
@Composable
private fun MainHeader(modifier: Modifier = Modifier) {
    val date = remember { SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date()) }
    Column(modifier = modifier.padding(top = 26.dp)) {
        Text(
            text = date,
            modifier = Modifier.testTag(MainTestTags.DATE),
            style = AppTypography.MonoHeaderCaption,
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 10.dp),
            style = AppTypography.ScreenTitle.copy(fontSize = 48.sp, letterSpacing = (-2).sp, lineHeight = 50.sp),
        )
    }
}

/** 기능 리스트(리스트 최상단 디바이더 + 각 row 하단 디바이더) */
@Composable
private fun MainItemList(
    itemList: List<MainItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider()
        itemList.forEach { item ->
            MainItemRow(item = item)
            HorizontalDivider()
        }
    }
}

/** 기능 항목 row(아이콘 컨테이너 + 이름 + › 꺾쇠) */
@Composable
private fun MainItemRow(
    item: MainItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(vertical = 21.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AppColors.ContainerBackground, AppShapes.Container)
                .border(1.dp, AppColors.ContainerBorder, AppShapes.Container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconResId),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = AppColors.Icon,
            )
        }
        Text(
            text = item.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp),
            style = AppTypography.ItemName.copy(fontSize = 23.sp, letterSpacing = (-0.8).sp),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = AppColors.Arrow,
        )
    }
}

/** `N TOOLS · DARK ALWAYS` 모노스페이스 캡션 푸터 */
@Composable
private fun MainFooter(
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.main_footer_hint, itemCount),
        modifier = modifier.padding(top = 18.dp, bottom = 14.dp, start = 2.dp),
        style = AppTypography.MonoCaption.copy(letterSpacing = 1.5.sp),
    )
}
