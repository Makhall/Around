package com.around.app.data.remote

import com.around.app.BuildConfig
import com.around.app.data.model.CircleError
import com.around.app.data.model.CircleOperationException
import com.around.app.data.model.CreatedCircle
import com.around.app.data.model.InviteCode
import com.around.app.data.model.JoinedCircle
import com.around.app.data.model.circleErrorFromApiCode
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AroundApi @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun createCircle(name: String): Result<CreatedCircle> =
        post("createCircle", buildJsonObject { put("name", name) }).map { data ->
            CreatedCircle(
                circleId = data.requireString("circleId"),
                circleName = data.requireString("circleName"),
                inviteCode = data.requireString("inviteCode"),
                expiresAt = data.requireLong("expiresAt"),
            )
        }

    suspend fun joinCircle(code: String): Result<JoinedCircle> =
        post("joinCircle", buildJsonObject { put("code", code) }).map { data ->
            JoinedCircle(
                circleId = data.requireString("circleId"),
                circleName = data.requireString("circleName"),
            )
        }

    suspend fun rotateInviteCode(circleId: String): Result<InviteCode> =
        post(
            "rotateInviteCode",
            buildJsonObject { put("circleId", circleId) },
        ).map { data ->
            InviteCode(
                code = data.requireString("inviteCode"),
                expiresAt = data.requireLong("expiresAt"),
            )
        }

    suspend fun leaveCircle(circleId: String): Result<Unit> =
        post(
            "leaveCircle",
            buildJsonObject { put("circleId", circleId) },
        ).map { }

    suspend fun removeMember(circleId: String, targetUid: String): Result<Unit> =
        post(
            "removeMember",
            buildJsonObject {
                put("circleId", circleId)
                put("targetUid", targetUid)
            },
        ).map { }

    suspend fun sendPlaceEvent(
        circleId: String,
        placeId: String,
        type: String,
    ): Result<Unit> = post(
        "placeEvent",
        buildJsonObject {
            put("circleId", circleId)
            put("placeId", placeId)
            put("type", type)
        },
    ).map { }

    private suspend fun post(
        path: String,
        body: JsonObject,
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser
                ?: return@withContext Result.failure(
                    CircleOperationException(CircleError.UNAUTHENTICATED),
                )
            val token = user.getIdToken(false).await().token
                ?: return@withContext Result.failure(
                    CircleOperationException(CircleError.UNAUTHENTICATED),
                )

            val request = Request.Builder()
                .url("$baseUrl/api/$path")
                .post(body.toString().toRequestBody(mediaType))
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                val root = if (text.isBlank()) {
                    JsonObject(emptyMap())
                } else {
                    json.parseToJsonElement(text).jsonObject
                }

                if (!response.isSuccessful) {
                    val code = root["error"]
                        ?.jsonObject
                        ?.get("code")
                        ?.jsonPrimitive
                        ?.contentOrNull
                    Result.failure(
                        CircleOperationException(circleErrorFromApiCode(code)),
                    )
                } else {
                    Result.success(root)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.failure(CircleOperationException(CircleError.NETWORK))
        } catch (error: Exception) {
            Result.failure(CircleOperationException(CircleError.UNKNOWN))
        }
    }

    private fun JsonObject.requireString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("Missing $key")

    private fun JsonObject.requireLong(key: String): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: error("Missing $key")
}
