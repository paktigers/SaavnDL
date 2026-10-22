package com.saavndl.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

/**
 * A row that reveals action buttons when swiped to the left. Swiping right on a closed row is left alone,
 * so a parent pager can still use it to change tabs. Only one row is open at a time (openKey).
 */
@Composable
fun SwipeActionRow(
    key: String,
    openKey: String?,
    onOpenChange: (String?) -> Unit,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit
) {
    val actionW = 76.dp
    val maxPx = with(LocalDensity.current) { (actionW * actions.size).toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    var target by remember { mutableFloatStateOf(0f) }

    fun settle(v: Float) {
        target = v
        tick++
        if (v < 0f) onOpenChange(key) else if (openKey == key) onOpenChange(null)
    }

    LaunchedEffect(tick) {
        if (tick > 0) animate(offsetX, target) { v, _ -> offsetX = v }
    }
    LaunchedEffect(openKey) {
        // another row was opened: close this one
        if (openKey != key && offsetX < -1f) { target = 0f; tick++ }
    }

    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // action buttons live behind the row
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { a ->
                Column(
                    Modifier.width(actionW).fillMaxHeight().background(a.color)
                        .clickable { a.onClick(); settle(0f) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(a.icon, a.label, tint = Color.White)
                    Text(a.label, color = Color.White, fontSize = 11.sp)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .background(background)
                .pointerInput(maxPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            // claim the gesture when swiping left, or when the row is already open
                            if (over < 0f || offsetX < -1f) change.consume()
                        }
                        if (slop != null) {
                            horizontalDrag(slop.id) { change ->
                                change.consume()
                                offsetX = (offsetX + change.positionChange().x).coerceIn(-maxPx, 0f)
                            }
                            settle(if (offsetX < -maxPx * 0.4f) -maxPx else 0f)
                        }
                    }
                }
        ) {
            content()
            if (offsetX < -1f) {
                // tapping an open row just closes it
                Box(
                    Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { settle(0f) }
                )
            }
        }
    }
}
