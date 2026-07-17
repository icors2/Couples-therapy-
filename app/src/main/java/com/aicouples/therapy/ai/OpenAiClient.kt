package com.aicouples.therapy.ai

import com.aicouples.therapy.BuildConfig
import com.aicouples.therapy.data.local.SettingsDataStore
import com.aicouples.therapy.data.model.OpenAiChatRequest
import com.aicouples.therapy.data.model.OpenAiChatResponse
import com.aicouples.therapy.data.model.OpenAiMessage
import com.aicouples.therapy.data.model.ResponseFormat
import com.aicouples.therapy.util.AppJson
import com.aicouples.therapy.util.Constants
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiCompletion(
    val text: String,
    val tokens: Int?,
    val model: String,
)

@Singleton
class OpenAiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun resolveApiKey(): String? {
        val stored = settingsDataStore.openAiApiKey.first()?.takeIf { it.isNotBlank() }
        return stored ?: BuildConfig.OPENAI_API_KEY.takeIf { it.isNotBlank() }
    }

    suspend fun resolveModel(): String {
        return settingsDataStore.preferredModel.first()?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_AI_MODEL
    }

    suspend fun complete(
        messages: List<OpenAiMessage>,
        jsonMode: Boolean = false,
        temperature: Double = 0.7,
    ): AiCompletion {
        val apiKey = resolveApiKey()
            ?: error("OpenAI API key not configured. Add it in Settings or local.properties.")
        val model = resolveModel()
        val body = OpenAiChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
            stream = false,
        )
        val json = AppJson.encodeToString(body)
        val request = Request.Builder()
            .url(Constants.OPENAI_CHAT_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OpenAI error ${response.code}: $raw")
            }
            val parsed = AppJson.decodeFromString<OpenAiChatResponse>(raw)
            val text = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            check(text.isNotBlank()) { "Empty AI response" }
            return AiCompletion(
                text = text,
                tokens = parsed.usage?.totalTokens,
                model = model,
            )
        }
    }
}
