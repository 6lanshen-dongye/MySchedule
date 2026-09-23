package com.myschedule.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val InkBlack = Color(0xFF15171C)
private val InkSoft = Color(0xFF23262C)
private val DayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 日期条:整条可自由左右滑动(不吸附、不翻页,像水流一样顺),
 * 滑动过程中只回报"预览日期",是否切换课表由调用方在点击时决定。
 */
@Composable
fun DayStripRibbon(
    days: List<LocalDate>,
    initialIndex: Int,
    today: LocalDate,
    highlight: LocalDate?,
    onPreview: (LocalDate) -> Unit,
    onPick: (LocalDate) -> Unit,
    /* 松手吸附到周一之后回报一次(用于"停稳即切换") */
    onSettle: (LocalDate) -> Unit = {},
    showTodayDot: Boolean = false,
    onTodayDot: () -> Unit = {},
    /* 这个值变化时把日期条拉回对齐(点日期/点周卡片/换周) */
    alignTick: Int = 0,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, days.size - 1)
    )
    /* 唯一的动画器:滑动中回报预览;松手后把日期条吸附到"该显示的周一" */
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .collect { (i, scrolling) ->
                val idx = i.coerceIn(0, days.size - 1)
                if (scrolling) {
                    days.getOrNull(idx)?.let(onPreview)
                } else {
                    /* 用可见窗口正中间那天做基准,往前/往后对称 */
                    val centerIdx = (idx + 3).coerceIn(0, days.size - 1)
                    val center = days.getOrNull(centerIdx) ?: return@collect
                    val mondayIdx = (centerIdx - (center.dayOfWeek.value - 1))
                        .coerceIn(0, days.size - 1)
                    days.getOrNull(mondayIdx)?.let(onSettle)
                    if (listState.firstVisibleItemIndex != mondayIdx ||
                        listState.firstVisibleItemScrollOffset != 0
                    ) {
                        listState.animateScrollToItem(mondayIdx)
                    }
                }
            }
    }
    /* 外部提交(点日期/点周卡片/换了周):等空闲后直接定位,不跟吸附动画抢 */
    LaunchedEffect(initialIndex, alignTick) {
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        val target = initialIndex.coerceIn(0, days.size - 1)
        if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(target)
        }
    }
    BoxWithConstraints(modifier) {
        val itemWidth = maxWidth / 7
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            itemsIndexed(days) { _, date ->
                val isToday = date == today
                val isPicked = highlight != null && date == highlight
                Column(
                    Modifier
                        .width(itemWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(date) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        DayNames[date.dayOfWeek.value - 1],
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) InkBlack else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isToday) InkBlack else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${date.dayOfMonth}",
                            fontSize = 14.sp,
                            fontWeight = if (isToday || isPicked) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) Color.White else InkSoft
                        )
                    }
                    /* 今天的小点:点一下回到当前日期 */
                    if (showTodayDot) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(enabled = isToday) { onTodayDot() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isToday) InkSoft else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
