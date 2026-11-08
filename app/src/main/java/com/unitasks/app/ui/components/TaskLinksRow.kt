package com.unitasks.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskLinks

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskLinksRow(task: Task, enabled: Boolean = true) {
    val links = TaskLinks.extract(task)
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        links.forEachIndexed { index, url ->
            AssistChip(
                enabled = enabled,
                onClick = { runCatching { uriHandler.openUri(url) } },
                label = {
                    Text(
                        if (url == task.linkUrl && !task.linkTitle.isNullOrBlank()) task.linkTitle else url.removePrefix("https://").removePrefix("http://"),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.widthIn(max = 260.dp).testTag("task_link:${task.id}:$index")
                    .semantics { contentDescription = "Open link: $url" },
            )
        }
    }
}
