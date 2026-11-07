package com.unitasks.app.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ----------------------------------------------------------------------------- DTOs
// See https://developers.google.com/workspace/tasks/reference/rest

@Serializable
data class TaskListDto(
    val id: String,
    val title: String = "",
    val updated: String? = null,
    val etag: String? = null,
)

@Serializable
data class TaskListsResponse(
    val items: List<TaskListDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class TaskLinkDto(val type: String? = null, val description: String? = null, val link: String? = null)

@Serializable
data class TaskDto(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    /** "needsAction" or "completed". */
    val status: String? = null,
    /** RFC3339; only the date part is meaningful. */
    val due: String? = null,
    val completed: String? = null,
    val updated: String? = null,
    val parent: String? = null,
    val position: String? = null,
    val deleted: Boolean? = null,
    val hidden: Boolean? = null,
    val links: List<TaskLinkDto>? = null,
    val webViewLink: String? = null,
)

@Serializable
data class TasksResponse(
    val items: List<TaskDto> = emptyList(),
    val nextPageToken: String? = null,
)

/** Body for tasklists.insert / tasklists.patch. */
@Serializable
data class TaskListBody(val title: String)

/**
 * Body for tasks.insert / tasks.patch. Encoded with explicit nulls so clearing a due date
 * (`"due": null`) is sent to the server.
 */
@Serializable
data class TaskBody(
    val title: String,
    val notes: String?,
    val status: String,
    val due: String?,
    val completed: String?,
)

// ----------------------------------------------------------------------------- API

interface TasksApi {
    @GET("tasks/v1/users/@me/lists")
    suspend fun listTaskLists(
        @Query("maxResults") maxResults: Int = 100,
        @Query("pageToken") pageToken: String? = null,
    ): TaskListsResponse

    @POST("tasks/v1/users/@me/lists")
    suspend fun insertTaskList(@Body body: TaskListBody): TaskListDto

    @PATCH("tasks/v1/users/@me/lists/{tasklist}")
    suspend fun patchTaskList(@Path("tasklist") listId: String, @Body body: TaskListBody): TaskListDto

    @DELETE("tasks/v1/users/@me/lists/{tasklist}")
    suspend fun deleteTaskList(@Path("tasklist") listId: String): Response<ResponseBody>

    @GET("tasks/v1/lists/{tasklist}/tasks")
    suspend fun listTasks(
        @Path("tasklist") listId: String,
        @Query("updatedMin") updatedMin: String? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("maxResults") maxResults: Int = 100,
        @Query("showCompleted") showCompleted: Boolean = true,
        @Query("showDeleted") showDeleted: Boolean = true,
        // Must be true to see tasks completed in Google's first-party apps.
        @Query("showHidden") showHidden: Boolean = true,
    ): TasksResponse

    @POST("tasks/v1/lists/{tasklist}/tasks")
    suspend fun insertTask(
        @Path("tasklist") listId: String,
        @Body body: TaskBody,
        @Query("parent") parent: String? = null,
        @Query("previous") previous: String? = null,
    ): TaskDto

    @PATCH("tasks/v1/lists/{tasklist}/tasks/{task}")
    suspend fun patchTask(
        @Path("tasklist") listId: String,
        @Path("task") taskId: String,
        @Body body: TaskBody,
    ): TaskDto

    @POST("tasks/v1/lists/{tasklist}/tasks/{task}/move")
    suspend fun moveTask(
        @Path("tasklist") listId: String,
        @Path("task") taskId: String,
        @Query("parent") parent: String? = null,
        @Query("previous") previous: String? = null,
    ): TaskDto

    @DELETE("tasks/v1/lists/{tasklist}/tasks/{task}")
    suspend fun deleteTask(@Path("tasklist") listId: String, @Path("task") taskId: String): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://tasks.googleapis.com/"

        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = true
        }

        fun create(client: OkHttpClient, baseUrl: String = BASE_URL): TasksApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(TasksApi::class.java)
    }
}
