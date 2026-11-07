package com.unitasks.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    @Query("SELECT * FROM task_lists WHERE pendingDelete = 0 ORDER BY sortIndex, title COLLATE NOCASE")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists ORDER BY sortIndex, title COLLATE NOCASE")
    suspend fun getAll(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getById(id: String): TaskListEntity?

    @Query("SELECT * FROM task_lists WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): TaskListEntity?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM task_lists")
    suspend fun maxSortIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: TaskListEntity)

    @Update
    suspend fun update(list: TaskListEntity)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE pendingDelete = 0")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND pendingDelete = 0")
    fun observeChildren(parentId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    suspend fun getForList(listId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId")
    suspend fun getChildren(parentId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE pendingDelete = 1")
    suspend fun getPendingDeletes(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE remoteId IS NULL AND pendingDelete = 0")
    suspend fun getUnsyncedNew(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE remoteId IS NOT NULL AND pendingDelete = 0 AND isDirty = 1")
    suspend fun getDirty(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE remoteId IS NOT NULL AND pendingDelete = 0 AND needsMove = 1")
    suspend fun getNeedsMove(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE pendingDelete = 0 AND completed = 0 AND dueDate IS NOT NULL")
    suspend fun getActiveWithDueDate(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Database(entities = [TaskListEntity::class, TaskEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "unitasks.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .setTransactionExecutor(Runnable::run)
                .setQueryExecutor(Runnable::run)
                .build()
    }
}
