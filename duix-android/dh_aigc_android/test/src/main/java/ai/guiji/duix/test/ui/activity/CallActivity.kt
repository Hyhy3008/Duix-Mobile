package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.loader.ModelInfo
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.test.R
import ai.guiji.duix.test.databinding.ActivityCallBinding
import ai.guiji.duix.test.tts.TtsToPcm
import ai.guiji.duix.test.ui.adapter.MotionAdapter
import ai.guiji.duix.test.ui.dialog.AudioRecordDialog
import ai.guiji.duix.test.util.StringUtils
import android.Manifest
import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.min

class CallActivity : BaseActivity() {

    companion object {
        const val GL_CONTEXT_VERSION = 2
        private const val TAG = "CallActivity"

        private const val CEREBRAS_API_KEY = "csk-dwtjyxt4yrvdxf2d28fk3x8whdkdtf526njm925enm3pt32w"
        private const val CEREBRAS_ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"
        private const val CEREBRAS_MODEL = "llama-3.3-70b"
    }

    private lateinit var binding: ActivityCallBinding
    private var modelUrl = ""
    private var debug = false
    private var mMessage = ""

    private var duix: DUIX? = null
    private var mDUIXRender: DUIXRenderer? = null
    private var mModelInfo: ModelInfo? = null

    private val http = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private lateinit var ttsToPcm: TtsToPcm
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @SuppressLint("SetTextI18n")
    private fun applyMessage(msg: String) {
        if (debug) {
            runOnUiThread {
                binding.tvDebug.visibility = View.VISIBLE
                if (mMessage.length > 10000) mMessage = ""
                mMessage = "${StringUtils.dateToStringMS4()} $msg\n$mMessage"
                binding.tvDebug.text = mMessage
            }
        }
        Log.i(TAG, msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsToPcm = TtsToPcm(this)

        modelUrl = intent.getStringExtra("modelUrl") ?: ""
        debug = intent.getBooleanExtra("debug", false)

        Glide.with(mContext).load("file:///android_asset/bg/bg1.png").into(binding.ivBg)

        binding.glTextureView.setEGLContextClientVersion(GL_CONTEXT_VERSION)
        binding.glTextureView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.glTextureView.isOpaque = false

        binding.switchMute.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            duix?.setVolume(if (isChecked) 0.0F else 1.0F)
        }

        binding.btnRecord.setOnClickListener { requestPermission(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }

        binding.btnPlayPCM.setOnClickListener { applyMessage("start play pcm"); playPCMStream() }
        binding.btnPlayWAV.setOnClickListener { applyMessage("start play wav"); playWAVFile() }
        binding.btnRandomMotion.setOnClickListener { applyMessage("start random motion"); duix?.startRandomMotion(true) }
        binding.btnStopPlay.setOnClickListener { duix?.stopAudio() }

        // CHAT: text -> LLM -> TTS -> push PCM
        binding.btnSendChat.setOnClickListener {
            val prompt = binding.etChat.text?.toString()?.trim().orEmpty()
            if (prompt.isEmpty()) {
                Toast.makeText(mContext, "Bạn chưa nhập text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (duix?.isReady() != true) {
                Toast.makeText(mContext, "DUIX chưa sẵn sàng", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSendChat.isEnabled = false
            applyMessage("USER: $prompt")

            uiScope.launch {
                try {
                    val reply = withContext(Dispatchers.IO) { callCerebras(prompt) }
                    applyMessage("LLM: $reply")

                    duix?.stopAudio()

                    val pcm16k = withContext(Dispatchers.IO) { ttsToPcm.synthesizePcm16k(reply) }
                    withContext(Dispatchers.IO) { pushPcmRealtime(pcm16k) }

                } catch (e: Exception) {
                    applyMessage("ERROR: ${e.message}")
                    Toast.makeText(mContext, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.btnSendChat.isEnabled = true
                }
            }
        }

        mDUIXRender = DUIXRenderer(mContext, binding.glTextureView)
        binding.glTextureView.setRenderer(mDUIXRender)
        binding.glTextureView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        duix = DUIX(mContext, modelUrl, mDUIXRender) { event, msg, info ->
            when (event) {
                Constant.CALLBACK_EVENT_INIT_READY -> {
                    mModelInfo = info as ModelInfo
                    initOk()
                }
                Constant.CALLBACK_EVENT_INIT_ERROR -> runOnUiThread {
                    applyMessage("init error: $msg")
                    Toast.makeText(mContext, "Initialization exception: $msg", Toast.LENGTH_SHORT).show()
                }
                Constant.CALLBACK_EVENT_AUDIO_PLAY_START -> applyMessage("callback audio play start")
                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> applyMessage("callback audio play end")
                Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR -> applyMessage("callback audio play error: $msg")
                Constant.CALLBACK_EVENT_MOTION_START -> applyMessage("callback motion play start")
                Constant.CALLBACK_EVENT_MOTION_END -> applyMessage("callback motion play end")
            }
        }

        applyMessage("start init")
        duix?.init()
    }

    private fun initOk() {
        applyMessage("init ok")
        runOnUiThread {
            binding.btnRecord.isEnabled = true
            binding.btnPlayPCM.isEnabled = true
            binding.btnPlayWAV.isEnabled = true
            binding.switchMute.isEnabled = true
            binding.btnStopPlay.isEnabled = true

            mModelInfo?.let { modelInfo ->
                if (modelInfo.motionRegions.isNotEmpty()) {
                    val names = ArrayList<String>()
                    for (motion in modelInfo.motionRegions) {
                        if (!TextUtils.isEmpty(motion.name) && motion.name != "unknown") names.add(motion.name)
                    }
                    if (names.isNotEmpty()) {
                        binding.rvMotion.adapter = MotionAdapter(names, object : MotionAdapter.Callback {
                            override fun onClick(name: String, now: Boolean) {
                                applyMessage("start motion [$name]")
                                duix?.startMotion(name, now)
                            }
                        })
                    }
                    binding.btnRandomMotion.visibility = View.VISIBLE
                    binding.tvMotionTips.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun callCerebras(prompt: String): String {
        val body = JSONObject().apply {
            put("model", CEREBRAS_MODEL)
            put("stream", false)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val req = Request.Builder()
            .url(CEREBRAS_ENDPOINT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $CEREBRAS_API_KEY")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Cerebras HTTP ${resp.code}: $text")

            val json = JSONObject(text)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun pushPcmRealtime(pcm: ByteArray) {
        val d = duix ?: return
        val chunk = 320 // 10ms @ 16kHz mono 16-bit

        d.startPush()
        var off = 0
        while (off < pcm.size) {
            val end = min(off + chunk, pcm.size)
            d.pushPcm(pcm.copyOfRange(off, end))
            off = end
            Thread.sleep(10)
        }
        d.stopPush()
    }

    private fun playPCMStream() {
        val thread = Thread {
            duix?.startPush()
            val inputStream = assets.open("pcm/2.pcm")
            val buffer = ByteArray(320)
            var length = 0
            while (inputStream.read(buffer).also { length = it } > 0) {
                duix?.pushPcm(buffer.copyOfRange(0, length))
            }
            duix?.stopPush()
            inputStream.close()
        }
        thread.start()
    }

    private fun playWAVFile() {
        val thread = Thread {
            val wavName = "1.wav"
            val wavFile = File(mContext.externalCacheDir, wavName)
            if (!wavFile.exists()) {
                val inputStream = assets.open("wav/$wavName")
                mContext.externalCacheDir?.mkdirs()
                val out = FileOutputStream(wavFile)
                val buffer = ByteArray(1024)
                var length = 0
                while ((inputStream.read(buffer).also { length = it }) > 0) out.write(buffer, 0, length)
                out.close()
                inputStream.close()
            }
            duix?.playAudio(wavFile.absolutePath)
        }
        thread.start()
    }

    override fun permissionsGet(get: Boolean, code: Int) {
        super.permissionsGet(get, code)
        if (get) showRecordDialog()
        else Toast.makeText(mContext, R.string.need_permission_continue, Toast.LENGTH_SHORT).show()
    }

    private fun showRecordDialog() {
        val dialog = AudioRecordDialog(mContext, object : AudioRecordDialog.Listener {
            override fun onFinish(path: String) {
                val thread = Thread {
                    duix?.startPush()
                    val inputStream = FileInputStream(path)
                    val buffer = ByteArray(320)
                    var length = 0
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        duix?.pushPcm(buffer.copyOfRange(0, length))
                    }
                    duix?.stopPush()
                    inputStream.close()
                }
                thread.start()
            }
        })
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        try { ttsToPcm.release() } catch (_: Exception) {}
        duix?.release()
    }
}
