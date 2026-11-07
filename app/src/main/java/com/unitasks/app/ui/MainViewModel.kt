package com.unitasks.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unitasks.app.data.AuthMode
import com.unitasks.app.data.CompletionResult
import com.unitasks.app.data.Settings
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.data.local.TaskEntity
import com.unitasks.app.data.sync.SyncEngine
import com.unitasks.app.data.sync.SyncResult
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskList
import com.unitasks.app.domain.TaskOrganizer
import com.unitasks.app.domain.TaskSection
import com.unitasks.app.domain.UnifiedGrouping
import com.unitasks.app.domain.UnifiedViewBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface SelectedTab {
    data object Starred : SelectedTab
    data object AllTasks : SelectedTab
    data class ListTab(val listId: String) : SelectedTab
}

sealed interface UndoAction {
    val message: String
    data class Completed(val result: CompletionResult, override val message: String = "1 completed") : UndoAction
    data class Deleted(val snapshot: List<TaskEntity>, override val message: String) : UndoAction
}

data class MainUiState(
    val lists: List<TaskList> = emptyList(),
    val selectedTab: SelectedTab = SelectedTab.AllTasks,
    val unifiedGrouping: UnifiedGrouping = UnifiedGrouping.BY_LIST,
    val hiddenListIds: Set<String> = emptySet(),
    val starredSort: SortMode = SortMode.STARRED_RECENTLY,
    val sections: List<TaskSection> = emptyList(),
    val completed: List<Task> = emptyList(),
    val listTitles: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val authMode: AuthMode = AuthMode.NONE,
    val accountEmail: String? = null,
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val needsReauth: Boolean = false,
) {
    val currentList: TaskList?
        get() = (selectedTab as? SelectedTab.ListTab)?.let { tab -> lists.firstOrNull { it.id == tab.listId } }

    val defaultTargetListId: String?
        get() = currentList?.id
            ?: lists.firstOrNull { it.id !in hiddenListIds }?.id
            ?: lists.firstOrNull()?.id
}

class MainViewModel(
    private val repository: TasksRepository,
    private val settings: Settings,
    private val syncEngine: SyncEngine?,
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private var latestLists: List<TaskList> = emptyList()
    private var latestTasks: List<Task> = emptyList()
    private var selectedTab: SelectedTab = SelectedTab.AllTasks
    private var searchQuery: String = ""
    private var isSearching: Boolean = false
    private var isSyncing: Boolean = false
    private var syncError: String? = null
    private var needsReauth: Boolean = false

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _undoEvents = Channel<UndoAction>(Channel.BUFFERED)
    val undoEvents: Flow<UndoAction> = _undoEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (settings.authMode.value) {
                AuthMode.LOCAL -> repository.ensureDefaultList()
                AuthMode.GOOGLE -> syncNow()
                AuthMode.NONE -> Unit
            }
            refresh()
        }
        viewModelScope.launch {
            repository.observeLists().collect {
                latestLists = it
                recompute()
            }
        }
        viewModelScope.launch {
            repository.observeTasks().collect {
                latestTasks = it
                recompute()
            }
        }
    }

    private suspend fun refresh() {
        latestLists = repository.getLists()
        latestTasks = repository.getTasks()
        recompute()
    }

    private fun recompute() {
        _state.value = buildState()
    }

    private fun buildState(): MainUiState {
        val lists = latestLists
        val listIds = lists.mapTo(HashSet()) { it.id }
        val tasks = latestTasks.filter { it.listId in listIds }
        val grouping = settings.unifiedGrouping.value
        val hidden = settings.hiddenListIds.value
        val starredSort = settings.starredSort.value
        val today = todayProvider()

        val effectiveTab = when (val t = selectedTab) {
            is SelectedTab.ListTab -> if (lists.any { it.id == t.listId }) t else SelectedTab.AllTasks
            else -> t
        }
        val titles = lists.associate { it.id to it.title }
        val (sections, completed) = when (effectiveTab) {
            SelectedTab.AllTasks -> {
                val agg = UnifiedViewBuilder.buildAllTasks(
                    lists = lists,
                    tasks = tasks,
                    grouping = grouping,
                    hiddenListIds = hidden,
                    query = searchQuery,
                    today = today,
                )
                agg.sections to agg.completed
            }
            SelectedTab.Starred -> {
                val agg = UnifiedViewBuilder.buildStarred(
                    lists = lists,
                    tasks = tasks,
                    sort = starredSort,
                    query = searchQuery,
                    today = today,
                )
                agg.sections to agg.completed
            }
            is SelectedTab.ListTab -> {
                val list = lists.first { it.id == effectiveTab.listId }
                val forList = tasks.filter { it.listId == list.id }
                val rawSections = TaskOrganizer.sectionsForList(forList, list.sortMode, today)
                val filtered = rawSections.mapNotNull { s ->
                    val nodes = TaskOrganizer.filterNodes(s.nodes, searchQuery)
                    if (nodes.isEmpty() && s.title != null) null else s.copy(nodes = nodes)
                }
                val done = TaskOrganizer.completed(forList.filter { TaskOrganizer.matches(it, searchQuery) })
                filtered to done
            }
        }
        return MainUiState(
            lists = lists,
            selectedTab = effectiveTab,
            unifiedGrouping = grouping,
            hiddenListIds = hidden,
            starredSort = starredSort,
            sections = sections,
            completed = completed,
            listTitles = titles,
            searchQuery = searchQuery,
            isSearching = isSearching,
            authMode = settings.authMode.value,
            accountEmail = settings.accountEmail.value,
            lastSyncAt = settings.lastSyncAt.value,
            isSyncing = isSyncing,
            syncError = syncError,
            needsReauth = needsReauth,
        )
    }

    fun selectTab(tab: SelectedTab) {
        selectedTab = tab
        recompute()
    }

    fun setUnifiedGrouping(grouping: UnifiedGrouping) {
        settings.setUnifiedGrouping(grouping)
        recompute()
    }

    fun toggleListVisibility(listId: String) {
        val hidden = listId in settings.hiddenListIds.value
        settings.setListHidden(listId, !hidden)
        recompute()
    }

    fun setSearch(searching: Boolean, query: String = "") {
        isSearching = searching
        searchQuery = if (searching) query else ""
        recompute()
    }

    fun createList(title: String) {
        viewModelScope.launch {
            val id = repository.createList(title)
            selectedTab = SelectedTab.ListTab(id)
            refresh()
        }
    }

    fun renameCurrentList(title: String) {
        val id = (state.value.selectedTab as? SelectedTab.ListTab)?.listId ?: return
        viewModelScope.launch {
            repository.renameList(id, title)
            refresh()
        }
    }

    fun deleteCurrentList() {
        val id = (state.value.selectedTab as? SelectedTab.ListTab)?.listId ?: return
        viewModelScope.launch {
            if (repository.deleteList(id)) {
                selectedTab = SelectedTab.AllTasks
                refresh()
            }
        }
    }

    fun setCurrentSort(sortMode: SortMode) {
        when (val tab = state.value.selectedTab) {
            SelectedTab.Starred -> {
                settings.setStarredSort(sortMode)
                recompute()
            }
            is SelectedTab.ListTab -> viewModelScope.launch {
                repository.setListSort(tab.listId, sortMode)
                refresh()
            }
            SelectedTab.AllTasks -> Unit
        }
    }

    fun addTask(
        listId: String,
        title: String,
        notes: String = "",
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        starred: Boolean = false,
        parentId: String? = null,
        recurrence: RecurrenceRule? = null,
    ) {
        viewModelScope.launch {
            val target = listId.ifBlank { repository.ensureDefaultList() }
            repository.addTask(target, title, notes, dueDate, dueTime, starred, parentId, recurrence)
            refresh()
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            val result = repository.setCompleted(task.id, !task.completed) ?: return@launch
            refresh()
            if (!task.completed) _undoEvents.trySend(UndoAction.Completed(result))
        }
    }

    fun toggleStar(taskId: String) {
        viewModelScope.launch {
            repository.toggleStar(taskId)
            refresh()
        }
    }

    fun updateTaskDateTime(task: Task, dueDate: LocalDate?, dueTime: LocalTime?, recurrence: RecurrenceRule?) {
        viewModelScope.launch {
            repository.updateTask(task.id, task.title, task.notes, dueDate, dueTime, recurrence)
            refresh()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val snap = repository.deleteTask(taskId)
            refresh()
            if (snap.isNotEmpty()) _undoEvents.trySend(UndoAction.Deleted(snap, "Task deleted"))
        }
    }

    fun clearCompleted(listId: String? = null) {
        viewModelScope.launch {
            val targetIds = when {
                listId != null -> listOf(listId)
                state.value.selectedTab is SelectedTab.ListTab -> listOf((state.value.selectedTab as SelectedTab.ListTab).listId)
                else -> state.value.lists.filter { it.id !in state.value.hiddenListIds }.map { it.id }
            }
            val removed = targetIds.flatMap { repository.clearCompleted(it) }
            refresh()
            if (removed.isNotEmpty()) {
                _undoEvents.trySend(UndoAction.Deleted(removed, "Completed tasks deleted"))
            }
        }
    }

    fun reorder(orderedIds: List<String>, movedId: String) {
        viewModelScope.launch {
            repository.reorder(orderedIds, movedId)
            refresh()
        }
    }

    fun indent(taskId: String) {
        viewModelScope.launch {
            repository.indent(taskId)
            refresh()
        }
    }

    fun outdent(taskId: String) {
        viewModelScope.launch {
            repository.outdent(taskId)
            refresh()
        }
    }

    fun undo(action: UndoAction) {
        viewModelScope.launch {
            when (action) {
                is UndoAction.Completed -> repository.undoCompletion(action.result)
                is UndoAction.Deleted -> repository.restoreTasks(action.snapshot)
            }
            refresh()
        }
    }

    fun syncNow() {
        val engine = syncEngine ?: return
        if (settings.authMode.value != AuthMode.GOOGLE) return
        viewModelScope.launch {
            isSyncing = true
            syncError = null
            recompute()
            when (val result = engine.sync()) {
                SyncResult.Success -> {
                    isSyncing = false
                    syncError = null
                    needsReauth = false
                }
                SyncResult.AuthRequired -> {
                    isSyncing = false
                    needsReauth = true
                }
                is SyncResult.Failure -> {
                    isSyncing = false
                    syncError = result.error.localizedMessage ?: "Sync failed"
                }
            }
            recompute()
        }
    }

    fun useLocalMode() {
        viewModelScope.launch {
            settings.setAuth(AuthMode.LOCAL, null)
            repository.ensureDefaultList()
            recompute()
        }
    }

    fun onGoogleAuthorized(email: String?) {
        settings.setAuth(AuthMode.GOOGLE, email)
        needsReauth = false
        syncError = null
        recompute()
        syncNow()
    }

    fun signOut() {
        viewModelScope.launch {
            repository.clearAll()
            selectedTab = SelectedTab.AllTasks
            searchQuery = ""
            isSearching = false
            isSyncing = false
            syncError = null
            needsReauth = false
            recompute()
        }
    }

    companion object {
        fun factory(repository: TasksRepository, settings: Settings, syncEngine: SyncEngine?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(repository, settings, syncEngine) as T
            }
    }
}
