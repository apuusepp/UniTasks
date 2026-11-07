package com.unitasks.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unitasks.app.domain.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatDueChip(date: LocalDate, time: LocalTime?, today: LocalDate = LocalDate.now()): String {
    val dateLabel = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
    return if (time == null) dateLabel else "$dateLabel, ${time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}"
}

/**
 * Google Tasks-style date, time and repeat picker dialog.
 * Note: subtasks cannot repeat in Google Tasks; pass `canRepeat = false` for subtasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRepeatDialog(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    initialRecurrence: RecurrenceRule?,
    canRepeat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalTime?, RecurrenceRule?) -> Unit,
) {
    val startDate = initialDate ?: LocalDate.now()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var time by remember { mutableStateOf(initialTime) }
    var recurrence by remember { mutableStateOf(initialRecurrence) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }

    val selectedDate = dateState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    } ?: startDate

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = dateState, showModeToggle = false)
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AccessTime, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(time?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "Set time")
                        }
                        if (time != null) {
                            IconButton(onClick = { time = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear time")
                            }
                        }
                    }
                    if (canRepeat) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { showRepeatDialog = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Repeat, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(recurrence?.describe() ?: "Does not repeat")
                            }
                            if (recurrence != null) {
                                IconButton(onClick = { recurrence = null }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear repeat")
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (initialDate != null) {
                        TextButton(onClick = { onConfirm(null, null, null) }) { Text("Clear") }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = { onConfirm(selectedDate, time, if (canRepeat) recurrence else null) }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val init = time ?: LocalTime.of(9, 0)
        val tpState = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(tpState.hour, tpState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = tpState) },
        )
    }

    if (showRepeatDialog) {
        RepeatDialog(
            initial = recurrence,
            anchorDate = selectedDate,
            onDismiss = { showRepeatDialog = false },
            onConfirm = {
                recurrence = it
                showRepeatDialog = false
            },
        )
    }
}

private enum class EndMode { NEVER, ON_DATE, AFTER_COUNT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(
    initial: RecurrenceRule?,
    anchorDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit,
) {
    var intervalText by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: RecurrenceRule.Frequency.WEEKLY) }
    var days by remember {
        mutableStateOf(initial?.daysOfWeek?.ifEmpty { setOf(anchorDate.dayOfWeek) } ?: setOf(anchorDate.dayOfWeek))
    }
    var endMode by remember {
        mutableStateOf(
            when {
                initial?.endDate != null -> EndMode.ON_DATE
                initial?.remainingOccurrences != null -> EndMode.AFTER_COUNT
                else -> EndMode.NEVER
            },
        )
    }
    var endDate by remember { mutableStateOf(initial?.endDate ?: anchorDate.plusMonths(1)) }
    var countText by remember { mutableStateOf((initial?.remainingOccurrences ?: 5).toString()) }
    var freqExpanded by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var unused by remember { mutableIntStateOf(0) } // keeps recomposition simple

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Repeats every", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = frequency.name.lowercase().replaceFirstChar(Char::uppercase),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                            RecurrenceRule.Frequency.entries.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                    onClick = { frequency = f; freqExpanded = false },
                                )
                            }
                        }
                    }
                }
                if (frequency == RecurrenceRule.Frequency.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            val selected = d in days
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    days = if (selected && days.size > 1) days - d else days + d
                                    unused++
                                },
                                label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                            )
                        }
                    }
                }
                Text("Ends", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == EndMode.NEVER, onClick = { endMode = EndMode.NEVER })
                    Text("Never")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == EndMode.ON_DATE, onClick = { endMode = EndMode.ON_DATE })
                    Text("On ")
                    TextButton(onClick = { endMode = EndMode.ON_DATE; showEndDatePicker = true }) {
                        Text(endDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == EndMode.AFTER_COUNT, onClick = { endMode = EndMode.AFTER_COUNT })
                    Text("After ")
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { endMode = EndMode.AFTER_COUNT; countText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(" occurrences")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val count = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                onConfirm(
                    RecurrenceRule(
                        frequency = frequency,
                        interval = interval,
                        daysOfWeek = if (frequency == RecurrenceRule.Frequency.WEEKLY) days else emptySet(),
                        endDate = if (endMode == EndMode.ON_DATE) endDate else null,
                        remainingOccurrences = if (endMode == EndMode.AFTER_COUNT) count else null,
                    ),
                )
            }) { Text("Done") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text("Stop repeating") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        endDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}
