package com.black.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.black.core.R
import com.black.core.ui.theme.AppColors
import com.black.core.ui.theme.AppTypography

/**
 * 도구 화면 섹션 위 디바이더 + 한글 캡션
 *
 * [trailing]이 있으면 캡션 줄 끝에 붙여 표시(접고 펴는 구획의 상태 아이콘 등).
 */
@Composable
fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    HorizontalDivider(modifier = Modifier.padding(top = 24.dp))
    if (trailing == null) {
        Text(
            text = title,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 12.dp),
            style = AppTypography.Caption,
        )
        trailing()
    }
}

/**
 * 라벨 + 스위치 행
 *
 * 라벨까지 행 전체를 눌러 토글하고, TalkBack이 라벨과 스위치를 한 항목으로 읽도록 행에 토글 의미 부여.
 * [enabled]가 false면 [ActionButton]처럼 흐리게 보이고 눌리지 않음.
 * [leading]은 라벨 앞 장식(색 점 등), [supportingText]는 라벨 아래 보조 문구(계정 이름 등).
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
) {
    Row(
        // 스위치(32dp)만으로는 Android 권장 최소 터치 높이(48dp)에 못 미쳐 행 높이를 보장
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppTypography.ItemName,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = AppTypography.Caption,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            // 테마 primary(#121212)가 검은 배경과 구분되지 않아 토큰 색 지정
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.ScreenBackground,
                checkedTrackColor = AppColors.Title,
                uncheckedThumbColor = AppColors.UncheckedControl,
                uncheckedTrackColor = AppColors.ContainerBackground,
                uncheckedBorderColor = AppColors.UncheckedControl,
            ),
        )
    }
}

/**
 * 상태 섹션 한 행(라벨 + 상태값)
 *
 * [onClick]이 있으면 조치가 필요한 상태라 상태값 뒤에 ›를 붙이고, 행을 누르면 조치 화면으로 이동
 */
@Composable
fun StatusRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 누를 수 있는 행이 Android 권장 최소 터치 높이(48dp)를 갖도록 보장
            .heightIn(min = 48.dp)
            .clickableIfPresent(onClick)
            // 라벨·상태값을 한 노드로 검증하도록 병합
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = AppTypography.Body,
        )
        // 조치가 필요한 상태값은 흰색으로 강조
        val valueStyle = AppTypography.Body.copy(
            color = if (onClick != null) AppColors.Title else AppColors.Icon,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style = valueStyle,
        )
        if (onClick != null) {
            // 스크린 리더가 ›를 읽지 않도록 의미 제거, 상태값과 같은 스타일로만 표시
            Text(
                text = " ›",
                modifier = Modifier.clearAndSetSemantics {},
                style = valueStyle,
            )
        }
    }
}

/**
 * 권한 상태 행
 *
 * 허용됨이면 누를 수 없고, 아니면 [requiredValue] 상태값을 보여 주며 눌러 조치 화면으로 이동.
 */
@Composable
fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requiredValue: String = stringResource(R.string.permission_status_required),
) {
    StatusRow(
        label = label,
        value = if (granted) stringResource(R.string.permission_status_allowed) else requiredValue,
        onClick = if (granted) null else onClick,
        modifier = modifier,
    )
}

/** [onClick]이 있을 때만 버튼 역할의 클릭 처리 추가 */
private fun Modifier.clickableIfPresent(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) {
        return this
    }
    return clickable(role = Role.Button, onClick = onClick)
}
