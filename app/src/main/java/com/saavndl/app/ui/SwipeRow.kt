package com.saavndl.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val onClick: () -> Unit)

/**
 * Swipe a row to the left to reveal action buttons.
 *
 * Built on the standard `scrollable` modifier so it takes part in nested scrolling: a right-swipe on a closed row is not
 * used by the row, so a parent pager can still switch tabs with it. Only one row stays open at a time (openKey).
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val actionW = 76.dp
    val maxPx = with(LocalDensity.current) { (actionW * actions.size).toPx() }
    var reveal by remember { mutableFloatStateOf(0f) }      // 0 = closed .. maxPx = fully open
    val scope = rememberCoroutineScope()
    val curOpen by rememberUpdatedState(openKey)
    val notify by rememberUpdatedState(onOpenChange)
    val touched = remember { booleanArrayOf(false) }

    suspend fun settle(open: Boolean) {
        animate(reveal, if (open) maxPx else 0f) { v, _ -> reveal = v }
        if (open) notify(key) else if (curOpen == key) notify(null)
    }

    val scrollState = rememberScrollableState { delta ->
        // delta > 0 = finger moved toward the start edge (left in LTR); it opens the row
        val signed = if (rtl) -delta else delta
        val old = reveal
        val new = (old + signed).coerceIn(0f, maxPx)
        reveal = new
        if (signed > 0f) {
            touched[0] = true
            delta                       // swallow every opening drag, even past the limit
        } else {
            if (new != old) touched[0] = true
            val used = new - old        // closing drags: only what we really closed; the rest goes to the pager
            if (rtl) -used else used
        }
    }

    val fling = remember(maxPx, rtl) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (!touched[0]) return initialVelocity     // gesture belonged to the parent (e.g. tab swipe)
                touched[0] = false
                val v = if (rtl) -initialVelocity else initialVelocity
                val open = reveal > maxPx * 0.4f || (v > 900f && reveal > 0f)
                settle(open)
                return 0f
            }
        }
    }

    // another row was opened -> close this one
    LaunchedEffect(openKey) {
        if (openKey != key && reveal > 1f) settle(false)
    }

    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // action buttons live behind the row
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { a ->
                Column(
                    Modifier.width(actionW).fillMaxHeight().background(a.color)
                        .clickable { a.onClick(); scope.launch { settle(false) } },
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
                .offset { IntOffset((if (rtl) reveal else -reveal).roundToInt(), 0) }
                .fillMaxWidth()
                .background(background)
                .scrollable(state = scrollState, orientation = Orientation.Horizontal, flingBehavior = fling)
        ) {
            content()
            if (reveal > 1f) {
                // tapping an open row only closes it
                Box(
                    Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { scope.launch { settle(false) } }
                )
            }
        }
    }
}
