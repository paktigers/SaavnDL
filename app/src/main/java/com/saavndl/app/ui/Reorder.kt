package com.saavndl.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a LazyColumn. The drag starts only from a small handle icon (Modifier from [handle]),
 * so normal scrolling and taps are never affected.
 */
class ReorderState<T>(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    source: List<T>,
    private val keyOf: (T) -> String
) {
    var items by mutableStateOf(source)
    var dragKey by mutableStateOf<String?>(null)
    var dragOffset by mutableFloatStateOf(0f)

    fun handle(key: String, onCommit: (List<T>) -> Unit): Modifier = Modifier.pointerInput(key) {
        detectDragGestures(
            onDragStart = { dragKey = key; dragOffset = 0f },
            onDrag = { change, amount -> change.consume(); drag(key, amount.y) },
            onDragEnd = { dragKey = null; dragOffset = 0f; onCommit(items) },
            onDragCancel = { dragKey = null; dragOffset = 0f; onCommit(items) }
        )
    }

    /** Visual state of the item that is being dragged (lifted, follows the finger). */
    fun visual(key: String): Modifier {
        val dragging = dragKey == key
        return Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (dragging) dragOffset else 0f
                scaleX = if (dragging) 1.03f else 1f
                scaleY = if (dragging) 1.03f else 1f
            }
    }

    private fun drag(key: String, dy: Float) {
        dragOffset += dy
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.key == key } ?: return
        val center = cur.offset + cur.size / 2f + dragOffset
        val target = infos.firstOrNull { info ->
            info.key != key && center >= info.offset && center <= info.offset + info.size &&
                items.any { x -> keyOf(x) == info.key }
        }
        if (target != null) {
            val from = items.indexOfFirst { keyOf(it) == key }
            val to = items.indexOfFirst { keyOf(it) == target.key }
            if (from >= 0 && to >= 0) {
                items = items.toMutableList().also { l -> val m = l.removeAt(from); l.add(to, m) }
                dragOffset += (cur.offset - target.offset)
            }
        }
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val info = listState.layoutInfo
        if (top < info.viewportStartOffset + 40) scope.launch { listState.scrollBy(-24f) }
        else if (bottom > info.viewportEndOffset - 40) scope.launch { listState.scrollBy(24f) }
    }
}

@Composable
fun <T> rememberReorderState(source: List<T>, listState: LazyListState, keyOf: (T) -> String): ReorderState<T> {
    val scope = rememberCoroutineScope()
    return remember(source) { ReorderState(listState, scope, source, keyOf) }
}
