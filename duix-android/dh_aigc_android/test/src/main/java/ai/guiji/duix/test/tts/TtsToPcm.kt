package ai.guiji.duix.test.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min

class TtsToPcm(ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val ready = CompletableDeferred<Unit>()

    // Nullable để tránh lỗi "Variable 'tts' must be initialized"
    private var tts: TextToSpeech? = null

    init {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("vi", "VN")
                ready.complete(Unit)
            } else {
                ready.completeExceptionally(IllegalStateException("TTS init failed: $status"))
            }
        }
        tts = engine
    }

    suspend fun synthesizePcm16k(text: String): ByteArray {
        ready.await()
        val engine = tts ?: error("TTS is null")

        val outFile = File(appCtx.cacheDir, "tts_tmp.wav").apply { if (exists()) delete() }
        val uttId = "utt_${System.currentTimeMillis()}"
        val done = CompletableDeferred<Unit>()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) { if (utteranceId == uttId) done.complete(Unit) }
            override fun onError(utteranceId: String) { if (utteranceId == uttId) done.completeExceptionally(RuntimeException("TTS error")) }
        })

        @Suppress("DEPRECATION")
        engine.synthesizeToFile(text, null, outFile, uttId)

        done.await()

        val wav = outFile.readBytes()
        var pcm = WavUtil.toPcm16kMono16(wav)

        // DUIX yêu cầu >= 1s (32000 bytes)
        if (pcm.size < 32000) pcm = pcm + ByteArray(32000 - pcm.size)
        return pcm
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private object WavUtil {
        data class WavInfo(
            val sampleRate: Int,
            val channels: Int,
            val bitsPerSample: Int,
            val pcmData: ByteArray
        )

        fun toPcm16kMono16(wav: ByteArray): ByteArray {
            val info = parseWav(wav)
            require(info.bitsPerSample == 16) { "WAV bitsPerSample=${info.bitsPerSample} (need 16)" }

            var pcm = info.pcmData
            pcm = when (info.channels) {
                1 -> pcm
                2 -> stereoToMono16(pcm)
                else -> error("WAV channels=${info.channels} (need 1 or 2)")
            }

            if (info.sampleRate != 16000) {
                pcm = resample16(pcm, info.sampleRate, 16000)
            }
            return pcm
        }

        private fun parseWav(wav: ByteArray): WavInfo {
            fun leInt(off: Int) = ByteBuffer.wrap(wav, off, 4).order(ByteOrder.LITTLE_ENDIAN).int
            fun leShort(off: Int) = ByteBuffer.wrap(wav, off, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            var offset = 12
            var sampleRate = -1
            var channels = -1
            var bitsPerSample = -1
            var dataOff = -1
            var dataSize = -1

            while (offset + 8 <= wav.size) {
                val id = String(wav, offset, 4)
                val size = leInt(offset + 4)
                val dataStart = offset + 8

                if (id == "fmt ") {
                    channels = leShort(dataStart + 2)
                    sampleRate = leInt(dataStart + 4)
                    bitsPerSample = leShort(dataStart + 14)
                } else if (id == "data") {
                    dataOff = dataStart
                    dataSize = size
                    break
                }
                offset = dataStart + size
            }

            require(sampleRate > 0 && channels > 0 && bitsPerSample > 0) { "Invalid WAV: missing fmt" }
            require(dataOff >= 0 && dataSize >= 0) { "Invalid WAV: missing data" }

            val end = min(dataOff + dataSize, wav.size)
            val pcm = wav.copyOfRange(dataOff, end)
            return WavInfo(sampleRate, channels, bitsPerSample, pcm)
        }

        private fun stereoToMono16(stereo: ByteArray): ByteArray {
            val out = ByteArray(stereo.size / 2)
            var i = 0
            var o = 0
            while (i + 3 < stereo.size) {
                val l = ByteBuffer.wrap(stereo, i, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val r = ByteBuffer.wrap(stereo, i + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val m = ((l + r) / 2).toShort()
                ByteBuffer.wrap(out, o, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(m)
                i += 4
                o += 2
            }
            return out
        }

        private fun resample16(pcm16: ByteArray, fromRate: Int, toRate: Int): ByteArray {
            val inSamples = pcm16.size / 2
            val outSamples = (inSamples.toLong() * toRate / fromRate).toInt()
            val out = ByteArray(outSamples * 2)

            fun sampleAt(idx: Int): Int {
                val i = (idx.coerceIn(0, inSamples - 1)) * 2
                return ByteBuffer.wrap(pcm16, i, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            }

            for (i in 0 until outSamples) {
                val srcPos = i.toDouble() * fromRate / toRate
                val p0 = srcPos.toInt()
                val t = srcPos - p0
                val s0 = sampleAt(p0)
                val s1 = sampleAt(p0 + 1)
                val v = (s0 + (s1 - s0) * t).toInt().toShort()
                ByteBuffer.wrap(out, i * 2, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(v)
            }
            return out
        }
    }
}
