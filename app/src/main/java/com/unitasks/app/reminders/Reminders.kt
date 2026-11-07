package com.unitasks.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unitasks.app.MainActivity
import com.unitasks.app.R
import com.unitasks.app.UniTasksApp
import com.unitasks.app.data.ReminderScheduler
import com.unitasks.app.domain.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId

/** Default reminder time for tasks with a date but no time (Google Tasks uses a morning reminder). */
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/** Computes when a task's reminder should fire, or null when no reminder applies. */
fun reminderTriggerAt(task: Task, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (task.completed) return null
    val date = task.dueDate ?: return null
    return date.atTime(task.dueTime ?: DEFAULT_REMINDER_TIME).atZone(zone).toInstant().toEpochMilli()
}

/** AlarmManager-backed reminders that post a notification at the task's due date/time. */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(task: Task) {
        val triggerAt = reminderTriggerAt(task) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val pi = pendingIntent(task.id, task.title)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    override fun cancel(taskId: String) {
        alarmManager.cancel(pendingIntent(taskId, ""))
    }

    private fun pendingIntent(taskId: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(android.net.Uri.parse("unitasks://task/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_REMIND = "com.unitasks.app.REMIND"
        const val ACTION_COMPLETE = "com.unitasks.app.COMPLETE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_ID = "task_reminders"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Shows the reminder notification, or marks the task completed from the notification action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmReminderScheduler.EXTRA_TASK_ID) ?: return
        val repository = (context.applicationContext as UniTasksApp).container.repository
        val pending = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    AlarmReminderScheduler.ACTION_COMPLETE -> {
                        repository.setCompleted(taskId, true)
                        NotificationManagerCompat.from(context).cancel(taskId.hashCode())
                    }
                    else -> repository.getTask(taskId)?.takeIf { !it.completed }?.let { showNotification(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, task: Task) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        AlarmReminderScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, task.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TASK, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val complete = PendingIntent.getBroadcast(
            context, task.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(AlarmReminderScheduler.ACTION_COMPLETE)
                .setData(android.net.Uri.parse("unitasks://complete/${task.id}"))
                .putExtra(AlarmReminderScheduler.EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AlarmReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title.ifBlank { context.getString(R.string.untitled_task) })
            .setContentText(task.notes.ifBlank { null })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.mark_completed), complete)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }
}

/** Re-arms reminders after reboot / app update (alarms don't survive either). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as UniTasksApp).container.repository
        val pending = goAsync()
        receiverScope.launch {
            try { repository.rescheduleAllReminders() } finally { pending.finish() }
        }
    }
}
