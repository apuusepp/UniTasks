package com.unitasks.app.data.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when the user must (re-)grant access through UI. */
class AuthRequiredException(message: String = "Google account authorization required") : IOException(message)

/** Supplies OAuth access tokens for the Google Tasks scope. */
interface TokenProvider {
    suspend fun getToken(): String?
    suspend fun invalidate(token: String)
}

/** Adds `Authorization: Bearer` and retries once with a fresh token on HTTP 401. */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.getToken() } ?: throw AuthRequiredException()
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != 401) return response
        response.close()
        runBlocking { tokens.invalidate(token) }
        val fresh = runBlocking { tokens.getToken() } ?: throw AuthRequiredException()
        return chain.proceed(chain.request().withToken(fresh))
    }

    private fun Request.withToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()
}

fun buildHttpClient(tokens: TokenProvider): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor(tokens))
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

/** Outcome of an interactive authorization attempt. */
sealed interface AuthorizationOutcome {
    data class Authorized(val accessToken: String, val email: String?) : AuthorizationOutcome
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthorizationOutcome
    data class Failed(val error: Throwable) : AuthorizationOutcome
}

/**
 * Google Identity Services based authorization for the Tasks scope.
 *
 * Requires an *Android* OAuth client registered in Google Cloud Console for this package name and
 * signing certificate SHA-1, with the Google Tasks API enabled.
 */
class GoogleAuthManager(private val context: Context) : TokenProvider {

    private val client get() = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
        .build()

    /** Starts authorization; may require showing a consent screen via the returned PendingIntent. */
    suspend fun authorize(): AuthorizationOutcome = try {
        client.authorize(request).await().toOutcome()
    } catch (t: Throwable) {
        AuthorizationOutcome.Failed(t)
    }

    /** Completes authorization after the consent activity returns. */
    fun handleConsentResult(data: Intent?): AuthorizationOutcome = try {
        client.getAuthorizationResultFromIntent(data).toOutcome()
    } catch (t: Throwable) {
        AuthorizationOutcome.Failed(t)
    }

    override suspend fun getToken(): String? {
        val result = runCatching { client.authorize(request).await() }.getOrNull() ?: return null
        if (result.hasResolution()) throw AuthRequiredException()
        return result.accessToken
    }

    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
    }

    @Suppress("DEPRECATION")
    private fun AuthorizationResult.toOutcome(): AuthorizationOutcome {
        val intent = pendingIntent
        if (hasResolution() && intent != null) return AuthorizationOutcome.NeedsConsent(intent)
        val token = accessToken ?: return AuthorizationOutcome.Failed(IllegalStateException("No access token returned"))
        return AuthorizationOutcome.Authorized(token, toGoogleSignInAccount()?.email)
    }

    companion object {
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
    }
}
