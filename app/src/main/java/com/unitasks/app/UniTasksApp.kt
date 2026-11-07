package com.unitasks.app

import android.app.Application
import android.content.Context
import com.unitasks.app.data.Settings
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.data.local.AppDatabase
import com.unitasks.app.data.remote.GoogleAuthManager
import com.unitasks.app.data.remote.TasksApi
import com.unitasks.app.data.remote.buildHttpClient
import com.unitasks.app.data.sync.SyncEngine
import com.unitasks.app.data.sync.WorkManagerSyncRequester
import com.unitasks.app.reminders.AlarmReminderScheduler

class AppContainer(context: Context) {
    val settings = Settings(context)
    val db = AppDatabase.create(context)
    val reminders = AlarmReminderScheduler(context)
    val syncRequester = WorkManagerSyncRequester(context, settings)
    val repository = TasksRepository(db, settings, reminders, syncRequester)
    val auth = GoogleAuthManager(context)
    val api: TasksApi by lazy { TasksApi.create(buildHttpClient(auth)) }
    val syncEngine by lazy { SyncEngine(db, settings, api, reminders) }
}

class UniTasksApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmReminderScheduler.ensureChannel(this)
        container.syncRequester.schedulePeriodic()
    }
}
