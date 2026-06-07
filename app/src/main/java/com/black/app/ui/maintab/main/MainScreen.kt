package com.black.app.ui.maintab.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.black.app.R
import com.black.app.ui.theme.BoldListTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 메인 리스트 항목 */
data class MainItem(
    val name: String,
    @DrawableRes val iconResId: Int,
    val onClick: () -> Unit,
)

/** 메인 화면(헤더 + 기능 리스트 + 푸터) — Bold Icon List [MainFragment] */
@Composable
fun MainScreen(
    itemList: List<MainItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BoldListTokens.ScreenBackground)
            .padding(horizontal = 24.dp),
    ) {
        MainHeader()
        MainItemList(
            itemList = itemList,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        MainFooter(itemCount = itemList.size)
    }
}

/** 모노스페이스 날짜 + 타이틀 헤더 */
@Composable
private fun MainHeader(modifier: Modifier = Modifier) {
    val date = remember { SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date()) }
    Column(modifier = modifier.padding(top = 26.dp)) {
        Text(
            text = date,
            modifier = Modifier.testTag("main_date"),
            color = BoldListTokens.CaptionColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.5.sp,
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 10.dp),
            color = BoldListTokens.TitleColor,
            fontSize = 48.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            lineHeight = 50.sp,
        )
    }
}

/** 기능 리스트(세로 중앙 정렬, 리스트 최상단 디바이더 + 각 row 하단 디바이더) */
@Composable
private fun MainItemList(
    itemList: List<MainItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(thickness = 1.dp, color = BoldListTokens.DividerColor)
        itemList.forEach { item ->
            MainItemRow(item = item)
            HorizontalDivider(thickness = 1.dp, color = BoldListTokens.DividerColor)
        }
    }
}

/** 기능 항목 row(아이콘 컨테이너 + 이름 + ↗ 화살표) */
@Composable
private fun MainItemRow(
    item: MainItem,
    modifier: Modifier = Modifier,
) {
    val iconContainerShape = RoundedCornerShape(12.dp)
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
                .background(BoldListTokens.ContainerBackground, iconContainerShape)
                .border(1.dp, BoldListTokens.ContainerBorder, iconContainerShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconResId),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = BoldListTokens.IconColor,
            )
        }
        Text(
            text = item.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp),
            color = BoldListTokens.ItemNameColor,
            fontSize = 23.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_up_right),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = BoldListTokens.ArrowColor,
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
        color = BoldListTokens.ArrowColor,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
    )
}
