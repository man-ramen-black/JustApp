package com.black.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.black.core.R
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography

/** [ToolTopBar] 요소의 testTag */
object ToolTopBarTestTags {
    const val NAVIGATE_UP_BUTTON = "navigate_up_button"
}

/**
 * 메인 목록에서 들어가는 도구 화면의 공통 틀(상단 바 + 스크롤 콘텐츠)
 *
 * 상단 바는 고정하고 [content]만 스크롤. 콘텐츠가 스크롤되면 상단 바 아래에 구분선 표시
 *
 * @param titleModifier 상단 바 제목에 적용(화면마다 testTag 지정에 사용)
 */
@Composable
fun ToolScreenScaffold(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.ScreenBackground)
            .statusBarsPadding(),
    ) {
        ToolTopBar(
            title = title,
            onNavigateUp = onNavigateUp,
            titleModifier = titleModifier,
        )
        // 스크롤 중에 콘텐츠와 상단 바의 경계 표시(Material 3 상단 바가 스크롤 시 배경색을 바꾸는 것에 대응)
        if (scrollState.canScrollBackward) {
            HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            content = content,
        )
    }
}

/**
 * 도구 화면 상단 바(왼쪽 위 뒤로 가기 + 제목 + 오른쪽 [actions])
 *
 * Material 3 small top app bar 규격(높이 64dp, 버튼 터치 영역 48dp, 아이콘 24dp)을 따르고,
 * 뒤로 가기·액션 아이콘의 바깥 끝을 화면 콘텐츠 좌우 여백(24dp)에 맞춤
 *
 * @param titleModifier 제목에 적용(화면마다 testTag 지정에 사용)
 */
@Composable
fun ToolTopBar(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIconButton(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_up),
            onClick = onNavigateUp,
            modifier = Modifier.testTag(ToolTopBarTestTags.NAVIGATE_UP_BUTTON),
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .then(titleModifier),
            style = AppTypography.TopBarTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** 상단 바 아이콘 버튼(터치 영역 48dp, 아이콘 24dp) */
@Composable
fun TopBarIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        // 벡터 고유 크기가 24dp가 아닌 아이콘(ic_save 등)도 같은 크기로 맞춤
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = AppColors.Title,
        )
    }
}
