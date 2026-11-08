package com.unitasks.app.data

import android.content.Context
import android.content.SharedPreferences
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.UnifiedGrouping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthMode { NONE, LOCAL, GOOGLE }

/** Small SharedPreferences-backed store for app state and UI preferences. */
class Settings(context: Context, name: String = "unitasks_settings") {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val _authMode = MutableStateFlow(
        runCatching { AuthMode.valueOf(prefs.getString(KEY_AUTH, AuthMode.NONE.name)!!) }.getOrDefault(AuthMode.NONE),
    )
    val authMode: StateFlow<AuthMode> = _authMode.asStateFlow()

    private val _accountEmail = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _grouping = MutableStateFlow(
        runCatching { UnifiedGrouping.valueOf(prefs.getString(KEY_GROUPING, UnifiedGrouping.BY_LIST.name)!!) }
            .getOrDefault(UnifiedGrouping.BY_LIST),
    )
    val unifiedGrouping: StateFlow<UnifiedGrouping> = _grouping.asStateFlow()

    private val _hiddenLists = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toSet())
    val hiddenListIds: StateFlow<Set<String>> = _hiddenLists.asStateFlow()

    private val _starredSort = MutableStateFlow(
        runCatching { SortMode.valueOf(prefs.getString(KEY_STARRED_SORT, SortMode.STARRED_RECENTLY.name)!!) }
            .getOrDefault(SortMode.STARRED_RECENTLY),
    )
    val starredSort: StateFlow<SortMode> = _starredSort.asStateFlow()

    private val _allTasksSort = MutableStateFlow(
        runCatching { SortMode.valueOf(prefs.getString(KEY_ALL_TASKS_SORT, SortMode.MY_ORDER.name)!!) }
            .getOrDefault(SortMode.MY_ORDER),
    )
    val allTasksSort: StateFlow<SortMode> = _allTasksSort.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0 })
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt.asStateFlow()

    var defaultListId: String?
        get() = prefs.getString(KEY_DEFAULT_LIST, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_LIST, value).apply()

    fun setAuth(mode: AuthMode, email: String? = null) {
        prefs.edit().putString(KEY_AUTH, mode.name).putString(KEY_EMAIL, email).apply()
        _authMode.value = mode
        _accountEmail.value = email
    }

    fun setUnifiedGrouping(grouping: UnifiedGrouping) {
        prefs.edit().putString(KEY_GROUPING, grouping.name).apply()
        _grouping.value = grouping
    }

    fun setListHidden(listId: String, hidden: Boolean) {
        val updated = if (hidden) _hiddenLists.value + listId else _hiddenLists.value - listId
        prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
        _hiddenLists.value = updated
    }

    fun setStarredSort(sort: SortMode) {
        prefs.edit().putString(KEY_STARRED_SORT, sort.name).apply()
        _starredSort.value = sort
    }

    fun setAllTasksSort(sort: SortMode) {
        prefs.edit().putString(KEY_ALL_TASKS_SORT, sort.name).apply()
        _allTasksSort.value = sort
    }

    /** RFC3339 `updatedMin` watermark per remote list id for incremental sync. */
    fun getListSyncToken(remoteListId: String): String? = prefs.getString(KEY_LIST_SYNC + remoteListId, null)

    fun setListSyncToken(remoteListId: String, value: String?) {
        prefs.edit().putString(KEY_LIST_SYNC + remoteListId, value).apply()
    }

    fun markSynced(at: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, at).apply()
        _lastSyncAt.value = at
    }

    /** Wipes everything (used on sign-out / account switch). */
    fun clear() {
        prefs.edit().clear().apply()
        _authMode.value = AuthMode.NONE
        _accountEmail.value = null
        _grouping.value = UnifiedGrouping.BY_LIST
        _hiddenLists.value = emptySet()
        _starredSort.value = SortMode.STARRED_RECENTLY
        _allTasksSort.value = SortMode.MY_ORDER
        _lastSyncAt.value = null
    }

    private companion object {
        const val KEY_AUTH = "auth_mode"
        const val KEY_EMAIL = "account_email"
        const val KEY_GROUPING = "unified_grouping"
        const val KEY_HIDDEN = "hidden_lists"
        const val KEY_STARRED_SORT = "starred_sort"
        const val KEY_ALL_TASKS_SORT = "all_tasks_sort"
        const val KEY_DEFAULT_LIST = "default_list"
        const val KEY_LIST_SYNC = "list_sync_"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}
