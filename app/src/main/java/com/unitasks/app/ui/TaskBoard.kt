package com.unitasks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.unitasks.app.domain.*
import com.unitasks.app.ui.components.TaskRow
import com.unitasks.app.ui.components.formatDueChip
import com.unitasks.app.ui.theme.listAccentColor
import kotlin.math.roundToInt

private data class DropZone(val section: TaskSection, val task: Task?, val bounds: Rect)
private data class DragSession(
    val task: Task, val origin: Offset, val point: Offset,
    val delta: Offset = Offset.Zero, val startPoint: Offset = point,
)

/** Shared list surface for list, starred and unified tabs. Dragging never changes list or parent. */
@Composable
internal fun TaskBoard(
    sections: List<TaskSection>, completed: List<Task>, today: java.time.LocalDate,
    dateGrouped: Boolean, compactDates: Boolean,
    crossListOrdering: Boolean,
    listTitles: Map<String, String>, showBadges: Boolean,
    selectionMode: Boolean, selectedIds: Set<String>,
    onSelect: (String) -> Unit, onOpenTask: (String) -> Unit,
    onToggleComplete: (Task) -> Unit, onToggleStar: (String) -> Unit,
    onDateClick: (Task) -> Unit, onDrop: (TaskDrop) -> Unit,
    onAddInList: (String) -> Unit, onSelectList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val zones = remember { mutableStateMapOf<String, DropZone>() }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var completedExpanded by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    val currentDrag by rememberUpdatedState(drag)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val scrollStep = with(LocalDensity.current) { 10.dp.toPx() }
    val highlight = MaterialTheme.colorScheme.primary

    fun target(session: DragSession): DropZone? = zones.values.firstOrNull {
        session.point.y in boardBounds.top..boardBounds.bottom && it.bounds.contains(session.point) &&
            it.task?.id != session.task.id && (dateGrouped ||
            (it.task != null && (crossListOrdering || it.task.listId == session.task.listId) && it.task.parentId == session.task.parentId))
    }
    val hovered = drag?.let(::target)
    // Empty groups appear while dragging so dates can be changed even when a bucket has no tasks.
    val displaySections = if (dateGrouped && drag != null) {
        val defaults = listOf(
            TaskSection("past", if (compactDates) "Overdue" else "Past", emptyList(), overdue = true),
            TaskSection("today", "Today", emptyList()), TaskSection("tomorrow", "Tomorrow", emptyList()),
        ) + if (compactDates) listOf(
            TaskSection("week", "Next 7 days", emptyList()), TaskSection("later", "Later", emptyList()),
        ) else emptyList()
        val withEmpty = sections + (defaults + TaskSection("nodate", "No date", emptyList()))
            .filter { default -> sections.none { it.key == default.key } }
        withEmpty.sortedWith(compareBy<TaskSection> {
            when (it.key) { "past" -> 0; "today" -> 1; "tomorrow" -> 2; "nodate" -> 4; else -> 3 }
        }.thenBy { it.dateDropTarget(today)?.date })
    } else sections

    LaunchedEffect(drag?.task?.id) {
        while (currentDrag != null) {
            withFrameNanos { }
            val point = currentDrag?.point ?: break
            when {
                point.y < boardBounds.top + edge -> listState.scrollBy(-scrollStep)
                point.y > boardBounds.bottom - edge -> listState.scrollBy(scrollStep)
            }
        }
    }

    Box(modifier.onGloballyPositioned { boardBounds = it.boundsInRoot() }) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            displaySections.forEach { section ->
                if (section.title != null) item(key = "header:${section.key}") {
                    val key = "header:${section.key}"
                    DisposableEffect(key) { onDispose { zones.remove(key) } }
                    Column(
                        Modifier.fillMaxWidth().testTag("drop_group:${section.key}")
                            .onGloballyPositioned { zones[key] = DropZone(section, null, it.boundsInRoot()) }
                            .background(if (hovered?.section?.key == section.key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { collapsed[section.key] = collapsed[section.key] != true }
                            .padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            section.listId?.let { listId ->
                                Box(Modifier.size(10.dp).clip(CircleShape).background(listAccentColor(listId)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(section.title, style = MaterialTheme.typography.titleSmall,
                                color = if (section.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).then(if (section.listId != null) Modifier.clickable { onSelectList(section.listId) } else Modifier))
                            Text(section.nodes.sumOf { 1 + it.children.size }.toString(), style = MaterialTheme.typography.labelMedium)
                            if (section.listId != null) IconButton(onClick = { onAddInList(section.listId) }, modifier = Modifier.testTag("add_in_list:${section.listId}")) {
                                Icon(Icons.Default.Add, contentDescription = "Add task to ${section.title}")
                            }
                            Icon(if (collapsed[section.key] == true) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = if (collapsed[section.key] == true) "Expand" else "Collapse")
                        }
                        if (drag != null && dateGrouped) section.dateDropTarget(today)?.let { destination ->
                            Text("Drop: ${destination.date?.let { formatDueChip(it, null, today) } ?: "Remove date"}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        }
                    }
                }
                if (collapsed[section.key] != true) {
                    if (section.nodes.isEmpty() && section.listId != null) item(key = "empty:${section.key}") {
                        Text(if (section.completedCount > 0) "All tasks completed (${section.completedCount})" else "No active tasks",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    val rows = section.nodes.flatMap { node -> listOf(node.task to false) + node.children.map { it to true } }
                    items(rows, key = { "task:${it.first.id}" }) { (task, indented) ->
                        val key = "task:${task.id}"
                        var handlePosition by remember { mutableStateOf(Offset.Zero) }
                        val pinnable = LocalPinnableContainer.current
                        var pinnedHandle by remember { mutableStateOf<PinnableContainer.PinnedHandle?>(null) }
                        DisposableEffect(key) { onDispose { pinnedHandle?.release(); zones.remove(key) } }
                        val zone = hovered?.takeIf { it.task?.id == task.id }
                        val before = drag?.point?.y?.let { it < (zone?.bounds?.center?.y ?: 0f) } == true
                        TaskRow(
                            task, if (showBadges) listTitles[task.listId] else null, today,
                            onToggleComplete = { onToggleComplete(task) }, onToggleStar = { onToggleStar(task.id) },
                            onClick = { onOpenTask(task.id) }, onDateClick = { onDateClick(task) }, indented = indented,
                            selectionMode = selectionMode, selected = task.id in selectedIds, onSelect = { onSelect(task.id) },
                            modifier = Modifier.onGloballyPositioned { zones[key] = DropZone(section, task, it.boundsInRoot()) }
                                .graphicsLayer { alpha = if (drag?.task?.id == task.id) 0.25f else 1f }
                                .drawBehind {
                                    if (zone != null) drawLine(highlight, Offset(0f, if (before) 0f else size.height),
                                        Offset(size.width, if (before) 0f else size.height), 3.dp.toPx())
                                },
                            trailingDragHandle = {
                                Icon(
                                    Icons.Default.DragIndicator, contentDescription = "Drag ${task.title}",
                                    modifier = Modifier.size(48.dp).testTag("drag:${task.id}")
                                        .onGloballyPositioned { handlePosition = it.positionInRoot() }
                                        .pointerInput(task.id, selectionMode) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    zones[key]?.let {
                                                        pinnedHandle = pinnable?.pin()
                                                        drag = DragSession(task, it.bounds.topLeft, handlePosition + offset)
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val point = handlePosition + change.position
                                                    drag = drag?.let { it.copy(point = point, delta = point - it.startPoint) }
                                                },
                                                onDragCancel = { drag = null; pinnedHandle?.release(); pinnedHandle = null },
                                                onDragEnd = {
                                                    drag?.let { session -> target(session)?.let { destination ->
                                                        val dateTarget = if (dateGrouped) {
                                                            if (destination.task != null) DateDropTarget(destination.task.dueDate)
                                                            else destination.section.dateDropTarget(today)
                                                        } else null
                                                        currentOnDrop(TaskDrop(session.task.id, destination.task?.id,
                                                            session.point.y < destination.bounds.center.y, dateTarget, dateGrouped))
                                                    } }
                                                    drag = null
                                                    pinnedHandle?.release()
                                                    pinnedHandle = null
                                                },
                                            )
                                        }.padding(12.dp),
                                )
                            },
                        )
                    }
                }
            }
            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    Text("Completed (${completed.size})", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().testTag("completed_header").clickable { completedExpanded = !completedExpanded }.padding(16.dp))
                }
                if (completedExpanded) items(completed, key = { "done:${it.id}" }) { task ->
                    TaskRow(task, if (showBadges) listTitles[task.listId] else null, today,
                        onToggleComplete = { onToggleComplete(task) }, onToggleStar = { onToggleStar(task.id) },
                        onClick = { onOpenTask(task.id) }, onDateClick = {})
                }
            }
        }
        drag?.let { session ->
            Surface(
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, (session.origin.y + session.delta.y - boardBounds.top).roundToInt()) }
                    .clearAndSetSemantics { }, shadowElevation = 8.dp, tonalElevation = 4.dp,
            ) {
                TaskRow(session.task, listTitles[session.task.listId], today, {}, {}, {}, {})
            }
        }
    }
}
