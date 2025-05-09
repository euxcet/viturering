package com.euxcet.viturering.pages.mic

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.euxcet.viturering.R
import com.euxcet.viturering.RingManager
import com.euxcet.viturering.utils.GestureThrottle
import com.euxcet.viturering.utils.LanguageUtils
import com.euxcet.viturering.voice.XfVoiceUtil
import com.hcifuture.producer.detector.TouchState
import com.hcifuture.producer.sensor.data.RingTouchEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ADPCMDecoder {
    private val stepSizeTable = listOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    private val indexTable = listOf(
        -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8
    )

    private var pcmIndex = 0
    private var predSample = 0

    fun reset() {
        pcmIndex = 0
        predSample = 0
    }

    fun decode(code: Int): Short {
        val step = stepSizeTable[pcmIndex]
        var diff = step shr 3

        if (code and 4 != 0) {
            diff += step
        }
        if (code and 2 != 0) {
            diff += step shr 1
        }
        if (code and 1 != 0) {
            diff += step shr 2
        }

        if (code and 8 != 0) {
            diff = -diff
        }

        predSample += diff

        predSample = when {
            predSample > 32767 -> 32767
            predSample < -32768 -> -32768
            else -> predSample
        }

        pcmIndex += indexTable[code]
        pcmIndex = when {
            pcmIndex < 0 -> 0
            pcmIndex > 88 -> 88
            else -> pcmIndex
        }

        return predSample.toShort()
    }

    fun processAudio(audio: ByteArray): ByteArray {
        val pdm = mutableListOf<Byte>()
        for (data in audio) {
            val udata = data.toUByte()
            pdm.addAll(shortToBytes(decode(udata.toInt() and 0xF)).toMutableList())
            pdm.addAll(shortToBytes(decode(udata.toInt() shr 4)).toMutableList())
        }
        return pdm.toByteArray()
    }

    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf((value.toInt() and 0xFF).toByte(), ((value.toInt() shr 8) and 0xFF).toByte())
    }
}

@AndroidEntryPoint
class MICActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MICActivity"
        private const val TRANSFER_BUFFER_SIZE = 10 * 1024
    }

    @Inject
    lateinit var ringManager: RingManager

    private var inputView: EditText? = null
    private var voiceStatusView: TextView? = null

    private var t0: Long = 0
    private var count: Long = 0
    private var decoder = ADPCMDecoder()

    val ringDataDir by lazy {
        var dir = getExternalFilesDir("ring_audio")
        if (dir == null) {
            dir = File(filesDir, "ring_audio")
        }
        if (!dir!!.exists()) {
            dir!!.mkdirs()
        }
        dir!!
    }

    private var pcmFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        setContentView(R.layout.activity_micactivity)
        XfVoiceUtil.destroySelf()
        XfVoiceUtil.init(this, 0, true, false)
        inputView = findViewById(R.id.input)
        voiceStatusView = findViewById(R.id.voice_status)
        findViewById<Button>(R.id.open_mic)?.setOnClickListener {
            openVoice()
        }
        findViewById<Button>(R.id.close_mic)?.setOnClickListener {
            closeVoice()
        }
    }

    override fun onStart() {
        super.onStart()
        connectRing()
        ringManager.closeIMU()
        ringManager.openMic()
        XfVoiceUtil.getInstance().setVoiceRecognize(object: XfVoiceUtil.RecognizeListener {
            override fun onRecognize(words: MutableList<String>?) {
                runOnUiThread {
                    words?.firstOrNull()?.let {
                        inputView?.setText(it)
//                        voiceStatusView?.text = "正在识别..."
                    }
                }
            }

            override fun onError(code: Int, msg: String?) {
                Log.e(TAG, "语音识别错误: $code, $msg")
                voiceStatusView?.text = msg
            }

            override fun onVoiceBegin() {
                Log.e(TAG, "语音识别开始")
                runOnUiThread {
                    voiceStatusView?.text = "正在识别..."
                }
            }

            override fun onVoiceEnd() {
                Log.e(TAG, "识别结束")
                runOnUiThread {
                    voiceStatusView?.text = "语音识别结束"
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        closeVoice()
        ringManager.closeMic()
        ringManager.openIMU()
        XfVoiceUtil.getInstance().setVoiceRecognize(null)
    }

    private fun openVoice() {
        try {
            Log.e("Nuix", "open voice")
            decoder.reset()
            if (XfVoiceUtil.getInstance().startListening()) {
                if (!ringDataDir.exists()) {
                    ringDataDir.mkdirs()
                }
//                pcmFile = File(ringDataDir, "ring_audio.pcm")
//                if (pcmFile?.exists() == true) {
//                    pcmFile?.delete()
//                }
                runOnUiThread {
                    voiceStatusView?.text = "正在识别..."
                }
            } else {
                Log.e(TAG, "开启语音识别失败")
            }
            t0 = System.currentTimeMillis()
            count = 0
            Log.e("Nuix", "open voice done")
        } catch (e: Exception) {
            Log.e(TAG, "开启语音识别错误: $e")
        }
    }

    private fun closeVoice() {
        try {
            Log.e("Nuix", "close voice")
            XfVoiceUtil.getInstance().stopListening()
            runOnUiThread {
                voiceStatusView?.text = "语音识别结束"
            }
            Log.e("Nuix", "close voice done")
//            pcmFile?.let { pcm ->
//                val timeSuffix = timeFormat(LocalDateTime.now())
//                val wavFile = File(ringDataDir, "ring_audio_${timeSuffix}.wav")
//                try {
//                    PCMToWAV(pcm, wavFile, 1, 16000, 16000, 16)
//                } catch (e: Exception) {
//                    Log.e(TAG, "PCMToWAV error: ${e.message}")
//                }
//            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭语音识别错误: $e")
        }
    }

    private fun connectRing() {
        ringManager.registerListener {
            onConnectCallback { // Connect
                runOnUiThread {

                }
            }
            onMicDataCallback { // Mic
                count += 1
                if ((count % 200).toInt() == 0) {
                    Log.e("Nuix", "    Mic fps: " + (count / ((System.currentTimeMillis() - t0) / 1000)))
                }
                try {
                    val data = decoder.processAudio(it.data.toByteArray())
//                    pcmFile?.appendBytes(data)
                    val writeResult = XfVoiceUtil.getInstance().writeAudio(data)
                } catch (e: Exception) {
                    Log.e(TAG, "write audio data error: $e")
                }
            }
            onGestureCallback { // Gesture
                runOnUiThread {
                    Log.e("Nuix", "Gesture: $it")
                    val gestureText = "手势: ${LanguageUtils.gestureChinese(it)}"
                    if (GestureThrottle.throttle(it)) {
                        return@runOnUiThread
                    }
                    when (it) {
                        "pinch" -> {
                        }
                        "pinch_down" -> {

                        }
                        "pinch_up" -> {

                        }
                        "snap" -> {
                            finish()
                        }
                        "circle_clockwise" -> {
                            openVoice()
                        }
                        "circle_counterclockwise" -> {
                            closeVoice()
                        }
                        "wave_up" -> {
                            // playVideo("car_screen_down")
                        }
                        "wave_down" -> {

                            // playVideo("car_screen_up")
                        }
                        "push_forward" -> {
                        }
                    }
                }
            }
            onMoveCallback { // Move
                runOnUiThread {

                }
            }
            onStateCallback { // State
            }
            onTouchCallback { // Touch
                runOnUiThread {
                    val touchText = "触摸: ${(it.data)}"
                    // Log.e("Nuix", "Touch: ${it.data}")
                    when (it.data) {
                        RingTouchEvent.HOLD -> {
                        }
                        RingTouchEvent.TAP -> {

                        }
                        else -> {}
                    }
                }
            }
            onPlaneEventCallback {
                runOnUiThread {
                    if (it == TouchState.DOWN) {
                        Log.e("Nuix", "Plane down")
                    } else {
                        Log.e("Nuix", "Plane up")
                    }
                }
            }
            onPlaneMoveCallback {
                runOnUiThread {

                }
            }
        }
        ringManager.connect()
    }


    fun timeFormat(time: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")
        // 使用DateTimeFormatter格式化LocalDateTime对象

        // 使用DateTimeFormatter格式化LocalDateTime对象
        return time.format(formatter)
    }

    /**
     * @param input         raw PCM data
     * limit of file size for wave file: < 2^(2*4) - 36 bytes (~4GB)
     * @param output        file to encode to in wav format
     * @param channelCount  number of channels: 1 for mono, 2 for stereo, etc.
     * @param sampleRate    sample rate of PCM audio
     * @param bitsPerSample bits per sample, i.e. 16 for PCM16
     * @throws IOException in event of an error between input/output files
     * @see [soundfile.sapp.org/doc/WaveFormat](http://soundfile.sapp.org/doc/WaveFormat/)
     */
    @Throws(IOException::class)
    fun PCMToWAV(input: File, output: File?, channelCount: Int, originSampleRate: Int, sampleRate: Int, bitsPerSample: Int) {

        val inputSize = input.length().toInt()
        val sampleFactor : Float = sampleRate.toFloat() / originSampleRate.toFloat()

        FileOutputStream(output).use { encoded ->

            // WAVE RIFF header

            writeToOutput(encoded, "RIFF") // chunk id
            writeToOutput(encoded, 36 + (inputSize.toFloat() * sampleFactor).toInt()) // chunk size
            writeToOutput(encoded, "WAVE") // format

            // SUB CHUNK 1 (FORMAT)

            writeToOutput(encoded, "fmt ") // subchunk 1 id
            writeToOutput(encoded, 16) // subchunk 1 size
            writeToOutput(encoded, 1.toShort()) // audio format (1 = PCM)
            writeToOutput(encoded, channelCount.toShort()) // number of channelCount
            writeToOutput(encoded, sampleRate) // sample rate
            writeToOutput(encoded, sampleRate * channelCount * bitsPerSample / 8) // byte rate
            writeToOutput(encoded, (channelCount * bitsPerSample / 8).toShort()) // block align
            writeToOutput(encoded, bitsPerSample.toShort()) // bits per sample

            // SUB CHUNK 2 (AUDIO DATA)

            if (originSampleRate == sampleRate) {

                writeToOutput(encoded, "data") // subchunk 2 id
                writeToOutput(encoded, inputSize) // subchunk 2 size
                copy(FileInputStream(input), encoded)

            } else {

                // SUB CHUNK 2 (AUDIO DATA) - UPSAMPLE

                writeToOutput(encoded, "data") // subchunk 2 id
                writeToOutput(encoded, (inputSize.toFloat() * sampleFactor).toInt()) // subchunk 2 size

                val inputStream = FileInputStream(input)
                val buffer = ByteArray(2)
                var len: Int = inputStream.read(buffer)

                var overFlow = 0

                while (len != -1) {

                    for (i in 0 until (sampleFactor * 1000F).toInt()) {

                        overFlow += 1

                        if (overFlow >= 1000) {

                            encoded.write(buffer, 0, len)
                            overFlow = 0

                        }

                    }

                    len = inputStream.read(buffer)

                }

                inputStream.close()

            }

        }

    }


    /**
     * Writes string in big endian form to an output stream
     *
     * @param output stream
     * @param data   string
     * @throws IOException
     */

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: String) {

        for (i in 0 until data.length) {
            output.write(data[i].toInt())
        }

    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Int) {
        output.write(data shr 0)
        output.write(data shr 8)
        output.write(data shr 16)
        output.write(data shr 24)
    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Short) {
        output.write(data.toInt() shr 0)
        output.write(data.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream): Long {
        return copy(source, output, TRANSFER_BUFFER_SIZE)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream, bufferSize: Int): Long {
        var read = 0L
        val buffer = ByteArray(bufferSize)
        var n: Int
        while (source.read(buffer).also { n = it } != -1) {
            output.write(buffer, 0, n)
            read += n.toLong()
        }
        return read
    }

}