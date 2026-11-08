# UniTasks

Native Android (Kotlin + Jetpack Compose + Material 3 + Room) task manager with feature parity with the official **Google Tasks** Android app, plus a unified **"All tasks"** view that shows tasks across every list in one place.

## Features

- **Unified "All tasks" view** (default tab):
  - **Group by list**: collapsible section per list with accent color, active task counter, and inline `+` button to add directly into any list.
  - **Group by date**: chronological buckets (*Overdue*, *Today*, *Tomorrow*, *Next 7 days*, *Later*, *No date*) across all lists, with a colored list badge on every task row.
  - **Compact view controls**: one toolbar for grouping, sorting, and list filtering; choose visible lists in a scrollable sheet with a selection count and a quick **Show all lists** reset.
  - **Global search** across all lists.
- **Google Tasks feature parity**:
  - Multiple task lists (create, rename, delete; default list protected from deletion).
  - **Starred** tab aggregating starred tasks across all lists.
  - Tasks with title, details/notes, due date & time, and custom repeat rules (every N day/week/month/year, weekday selection, ends never / on date / after N occurrences).
  - Subtasks (one level of indentation, indent/unindent, completion cascade).
  - Sort by *My order*, *Date*, *Starred recently*, or *Title*.
  - Swipe or tap to complete, undo snackbar, collapsible *Completed (n)* section, and *Delete all completed tasks*.
  - Move tasks (with their subtasks) between lists.
  - Read-only source links (e.g. Gmail / Docs links attached to synced tasks).
  - Local due-date/time reminder notifications (`AlarmManager`) with a *Mark completed* notification action and boot reschedule receiver.
  - Two-way offline-first sync with the **Google Tasks REST API v1** (`SyncEngine` + `WorkManager`).
- **Add from other apps**: share plain text or links to **Add to UniTasks**, or select text and choose **Add to UniTasks** from Android's context menu. Edit the prefilled title/details, choose a destination list, then save or cancel. Works without signing in; shared content is saved only after confirmation. File/image attachments are not supported.

## Enabling Google Tasks API Sync

1. In the [Google Cloud Console](https://console.cloud.google.com/), enable the **Google Tasks API**.
2. Configure the OAuth consent screen and add the scope `https://www.googleapis.com/auth/tasks`.
3. Create an **OAuth 2.0 Client ID** of type **Android**:
   - Package name: `com.unitasks.app`
   - SHA-1 certificate fingerprint: your debug/release signing key SHA-1 (`./gradlew signingReport`).
4. Sign in from the welcome screen (or *Account & sync* in the top bar). You can also use the app immediately in **on-device mode** without an account.

> **Note on Google Tasks API limitations:** The public Google Tasks REST API v1 does not expose the *starred* flag, the *time-of-day* portion of due dates, or *recurrence rules*. UniTasks persists those fields locally in Room and preserves them across syncs.

## Build & Test

```bash
./gradlew testDebugUnitTest assembleDebug
```
