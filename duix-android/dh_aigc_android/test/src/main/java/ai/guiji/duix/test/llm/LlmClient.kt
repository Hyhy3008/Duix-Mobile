package ai.guiji.duix.test.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class LlmClient {

    companion object {
        private const val API_KEY = "csk-dwtjyxt4yrvdxf2d28fk3x8whdkdtf526njm925enm3pt32w"
        private const val ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b"
    }

    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            )
        }

        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $API_KEY")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Cerebras HTTP ${resp.code}: $text")

            val json = JSONObject(text)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
