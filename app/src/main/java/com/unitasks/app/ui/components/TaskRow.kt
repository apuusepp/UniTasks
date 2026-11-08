package com.unitasks.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unitasks.app.domain.Task
import com.unitasks.app.ui.theme.listAccentColor
import java.time.LocalDate

/**
 * Single task row modelled on Google Tasks:
 * circle completion button, title, optional details preview, due date/time + repeat chip,
 * optional link chip, optional list badge (for the unified / starred views), and star toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: Task,
    listBadge: String?,
    today: LocalDate,
    onToggleComplete: () -> Unit,
    onToggleStar: () -> Unit,
    onClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
    indented: Boolean = false,
    trailingDragHandle: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .testTag("task:${task.id}")
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) 44.dp else 8.dp,
                end = 4.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onToggleComplete,
            modifier = Modifier.size(40.dp).testTag("complete:${task.id}"),
        ) {
            if (task.completed) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Mark uncompleted",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Mark completed",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = task.title.ifBlank { "(No title)" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (task.notes.isNotBlank() && !task.completed) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasChips = (!task.completed && task.dueDate != null) || !task.linkUrl.isNullOrBlank() || listBadge != null
            if (hasChips) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!task.completed && task.dueDate != null) {
                        DueChip(
                            date = task.dueDate,
                            time = task.dueTime,
                            repeating = task.recurrence != null,
                            today = today,
                            onClick = onDateClick,
                        )
                    }
                    if (!task.linkUrl.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.linkUrl))) }
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = task.linkTitle?.ifBlank { null } ?: "Link",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (listBadge != null) {
                        ListBadge(listId = task.listId, title = listBadge)
                    }
                }
            }
        }
        if (!task.completed) {
            IconButton(
                onClick = onToggleStar,
                modifier = Modifier.size(40.dp).testTag("star:${task.id}"),
            ) {
                if (task.starred) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Remove from Starred",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = "Add to Starred",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailingDragHandle?.invoke()
        }
    }
}

@Composable
private fun DueChip(
    date: LocalDate,
    time: java.time.LocalTime?,
    repeating: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val overdue = date.isBefore(today)
    val isToday = date == today
    val tint = when {
        overdue -> MaterialTheme.colorScheme.error
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (overdue || isToday) tint.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = formatDueChip(date, time, today), style = MaterialTheme.typography.labelMedium, color = tint)
            if (repeating) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Repeat, contentDescription = "Repeating", tint = tint, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Pill badge identifying which list a task belongs to in the unified / starred views. */
@Composable
fun ListBadge(listId: String, title: String, modifier: Modifier = Modifier) {
    val accent = listAccentColor(listId)
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.testTag("badge:$listId"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(5.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
        }
    }
}
