package com.unitasks.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkDateDialog(count: Int, onDismiss: () -> Unit, today: LocalDate = LocalDate.now(), onApply: (LocalDate?) -> Unit) {
    val picker = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = { picker.selectedDateMillis?.let { onApply(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                modifier = Modifier.testTag("bulk_date_apply"),
            ) { Text("Apply to $count") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null) }, modifier = Modifier.testTag("bulk_date_remove")) { Text("Remove date") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        Column {
            Row {
                TextButton(onClick = { onApply(today) }, modifier = Modifier.testTag("bulk_date_today")) { Text("Today") }
                TextButton(onClick = { onApply(today.plusDays(1)) }, modifier = Modifier.testTag("bulk_date_tomorrow")) { Text("Tomorrow") }
            }
            DatePicker(state = picker, title = { Text("Due date for $count tasks") })
        }
    }
}
