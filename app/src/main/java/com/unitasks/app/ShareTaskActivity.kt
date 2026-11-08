package com.unitasks.app

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unitasks.app.data.AuthMode
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.TaskList
import com.unitasks.app.ui.components.AddTaskSheet
import com.unitasks.app.ui.theme.UniTasksTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime

/** A separate entry point returns to the sending app after Save or Cancel. */
class ShareTaskActivity : ComponentActivity() {
    private val vm: ShareTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val draft = SharedTaskDraft.fromIntent(intent)
        if (draft == null) {
            Toast.makeText(this, "Share text or a link to add a task", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            UniTasksTheme {
                LaunchedEffect(vm.saved) {
                    if (vm.saved) {
                        Toast.makeText(this@ShareTaskActivity, "Task added", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (vm.lists.isEmpty() && vm.error == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AddTaskSheet(
                            lists = vm.lists,
                            initialListId = vm.lists.firstOrNull { it.isDefault }?.id
                                ?: vm.lists.firstOrNull()?.id.orEmpty(),
                            allowListSelection = true,
                            initialTitle = draft.title,
                            initialNotes = draft.notes,
                            heading = "Add to UniTasks",
                            isSaving = vm.saving,
                            errorMessage = vm.error,
                            onDismiss = { if (!vm.saving) finish() },
                            onSave = vm::save,
                        )
                    }
                }
            }
        }
    }
}

class ShareTaskViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as UniTasksApp).container
    var lists by mutableStateOf<List<TaskList>>(emptyList())
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                container.repository.ensureDefaultList()
                lists = container.repository.getLists()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not load lists. Close and share again to retry."
            }
        }
    }

    fun save(
        listId: String, title: String, notes: String, date: LocalDate?, time: LocalTime?,
        starred: Boolean, recurrence: RecurrenceRule?,
    ) {
        if (saving || saved || title.isBlank() || lists.isEmpty()) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                check(container.repository.getLists().any { it.id == listId })
                container.repository.addTask(listId, title, notes, date, time, starred, recurrence = recurrence)
                if (container.settings.authMode.value == AuthMode.NONE) container.settings.setAuth(AuthMode.LOCAL)
                saved = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not save the task. Please try again."
            } finally {
                saving = false
            }
        }
    }
}
