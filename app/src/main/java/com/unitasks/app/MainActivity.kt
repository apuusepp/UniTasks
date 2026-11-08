package com.unitasks.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unitasks.app.data.AuthMode
import com.unitasks.app.data.remote.AuthorizationOutcome
import com.unitasks.app.ui.MainScreen
import com.unitasks.app.ui.MainViewModel
import com.unitasks.app.ui.SignInScreen
import com.unitasks.app.ui.TaskDetailScreen
import com.unitasks.app.ui.theme.UniTasksTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UniTasksApp).container
        val openTaskId = intent?.getStringExtra(EXTRA_OPEN_TASK)
        setContent {
            UniTasksTheme {
                UniTasksRoot(container = container, initialTaskId = openTaskId)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task_id"
    }
}

@Composable
fun UniTasksRoot(container: AppContainer, initialTaskId: String? = null) {
    val vm: MainViewModel = viewModel(
        factory = MainViewModel.factory(container.repository, container.settings, container.syncEngine),
    )
    val state by vm.state.collectAsState()
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state.authMode) {
        if (state.authMode != AuthMode.NONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        authBusy = false
        when (val outcome = container.auth.handleConsentResult(result.data)) {
            is AuthorizationOutcome.Authorized -> vm.onGoogleAuthorized(outcome.email)
            is AuthorizationOutcome.NeedsConsent -> authError = "Authorization cancelled"
            is AuthorizationOutcome.Failed -> authError = outcome.error.localizedMessage ?: "Sign-in failed"
        }
    }

    fun startGoogleSignIn() {
        authBusy = true
        authError = null
        scope.launch {
            when (val outcome = container.auth.authorize()) {
                is AuthorizationOutcome.Authorized -> {
                    authBusy = false
                    vm.onGoogleAuthorized(outcome.email)
                }
                is AuthorizationOutcome.NeedsConsent -> {
                    consentLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent).build())
                }
                is AuthorizationOutcome.Failed -> {
                    authBusy = false
                    authError = outcome.error.localizedMessage ?: "Sign-in failed"
                }
            }
        }
    }

    if (state.authMode == AuthMode.NONE) {
        SignInScreen(
            inProgress = authBusy,
            errorMessage = authError,
            onSignInWithGoogle = ::startGoogleSignIn,
            onContinueOffline = vm::useLocalMode,
        )
        return
    }

    LaunchedEffect(initialTaskId) {
        if (initialTaskId != null) navController.navigate("task/$initialTaskId")
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                state = state,
                undoEvents = vm.undoEvents,
                onSelectTab = vm::selectTab,
                onSetGrouping = vm::setUnifiedGrouping,
                onToggleListFilter = vm::toggleListVisibility,
                onSearchChange = vm::setSearch,
                onCreateList = vm::createList,
                onRenameCurrentList = vm::renameCurrentList,
                onDeleteCurrentList = vm::deleteCurrentList,
                onSetSort = vm::setCurrentSort,
                onAddTask = { listId, title, notes, date, time, starred, recurrence ->
                    vm.addTask(listId, title, notes, date, time, starred, parentId = null, recurrence = recurrence)
                },
                onToggleComplete = vm::toggleComplete,
                onToggleStar = vm::toggleStar,
                onUpdateTaskDateTime = vm::updateTaskDateTime,
                onOpenTask = { id -> navController.navigate("task/$id") },
                onClearCompleted = vm::clearCompleted,
                onUndo = vm::undo,
                onSyncNow = vm::syncNow,
                onConnectGoogle = ::startGoogleSignIn,
                onSignOut = {
                    container.syncRequester.cancelAll()
                    vm.signOut()
                },
                onChangeDueDates = vm::changeDueDates,
                onDropTask = vm::dropTask,
            )
        }
        composable(
            route = "task/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("taskId") ?: return@composable
            TaskDetailScreen(
                taskId = id,
                repository = container.repository,
                onBack = { navController.popBackStack() },
                onNavigateToTask = { subId -> navController.navigate("task/$subId") },
            )
        }
    }
}
