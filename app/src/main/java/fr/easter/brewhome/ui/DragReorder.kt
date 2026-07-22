package fr.easter.brewhome.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * État d'un glisser-déposer de réorganisation sur une [LazyListState].
 *
 * [onMove] est appelé pendant le glissement pour permuter deux éléments dans
 * la liste source ; [onDrop] est appelé une fois le doigt relâché pour
 * persister l'ordre final. Les index sont ceux des éléments dans la LazyColumn.
 */
class DragDropState internal constructor(
    private val state: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    /** Décalage vertical à appliquer à l'élément en cours de déplacement. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
            ?.also {
                draggingItemIndex = it.index
                draggingItemInitialOffset = it.offset
            }
    }

    internal fun onDragInterrupted() {
        if (draggingItemIndex != null) onDrop()
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y
        val dragging = draggingItemLayoutInfo ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val middleOffset = startOffset + dragging.size / 2f
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                dragging.index != item.index
        }
        if (target != null) {
            onMove(dragging.index, target.index)
            draggingItemIndex = target.index
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
): DragDropState = remember(lazyListState) { DragDropState(lazyListState, onMove, onDrop) }

/** À poser sur la LazyColumn : démarre le glisser après un appui long. */
fun Modifier.dragContainer(state: DragDropState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDrag = { change, dragAmount -> change.consume(); state.onDrag(dragAmount) },
        onDragEnd = { state.onDragInterrupted() },
        onDragCancel = { state.onDragInterrupted() },
    )
}

/**
 * Liste verticale réorganisable par appui long + glisser. La LazyColumn ne doit
 * contenir que ces éléments (pas d'en-tête) pour que les index restent alignés
 * avec la liste [items] ; place les libellés d'aide au-dessus, hors de ce bloc.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    itemContent: @Composable (item: T, itemModifier: Modifier) -> Unit,
) {
    val ordered = remember(items) { items.toMutableStateList() }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(
        lazyListState = listState,
        onMove = { from, to ->
            if (from in ordered.indices && to in ordered.indices) {
                ordered.add(to, ordered.removeAt(from))
            }
        },
        onDrop = { onReorder(ordered.toList()) },
    )
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        modifier = modifier.dragContainer(dragState),
    ) {
        itemsIndexed(ordered, key = { _, it -> key(it) }) { index, item ->
            val dragging = index == dragState.draggingItemIndex
            val itemModifier = if (dragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragState.draggingItemOffset }
            } else {
                Modifier.animateItem()
            }
            itemContent(item, itemModifier)
        }
    }
}
