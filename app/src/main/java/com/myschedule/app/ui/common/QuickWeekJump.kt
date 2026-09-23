package com.myschedule.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myschedule.app.data.ScheduleLogic
import com.myschedule.app.data.Semester

private val SoftInk = Color(0xFF3A3D44)
private val InkBlack = Color(0xFF15171C)
private val ChipIdleBg = Color.White
private val ChipIdleBorder = Color(0xFFE3E8F0)
private val PillIdleBg = Color(0xFFF2F4F8)

/**
 * 右上角的「快速滑动」开关:
 * 点一下变深色并就地展开挑周条,再点一下变回白色并收起。
 */
@Composable
fun QuickJumpChip(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) SoftInk else ChipIdleBg)
            .border(
                width = 1.dp,
                color = if (active) SoftInk else ChipIdleBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = "快速滑动",
            tint = if (active) Color.White else InkBlack,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "快速滑动",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Color.White else InkBlack
        )
    }
}

/**
 * 就地展开的挑周条:缩小简化版,一行小胶囊,左右滑动,点一下跳到那一周。
 * 课表页放在「第N周」和「快速滑动」中间;今日页同理(并隐去中间的"| 周X")。
 */
@Composable
fun QuickJumpStrip(
    semester: Semester,
    currentWeek: Int,
    onPreview: (Int) -> Unit = {},
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = semester.totalWeeks.coerceAtLeast(1)
    val weeks = remember(total) { (1..total).toList() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentWeek - 1).coerceIn(0, total - 1)
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .collect { (i, scrolling) ->
                if (scrolling) onPreview((i + 1).coerceIn(1, total))
            }
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(weeks) { w ->
            val selected = w == currentWeek
            val mon = ScheduleLogic.dateOfWeek(semester, w, 1)
            val sun = ScheduleLogic.dateOfWeek(semester, w, 7)
            Column(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) SoftInk else PillIdleBg)
                    .clickable { onPick(w) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "第${w}周",
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else InkBlack
                )
                if (mon != null && sun != null) {
                    Text(
                        "${mon.monthValue}/${mon.dayOfMonth}",
                        fontSize = 11.sp,
                        color = if (selected) Color(0xCCFFFFFF)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
