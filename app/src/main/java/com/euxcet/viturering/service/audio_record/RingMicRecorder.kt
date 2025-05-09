package com.euxcet.viturering.service.audio_record

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.euxcet.viturering.MainApplication
import com.euxcet.viturering.voice.RecognizeListener
import com.euxcet.viturering.voice.VoiceRecognizer
import com.euxcet.viturering.voice.impl.XfVoiceRecognizer
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.data.RingV2AudioData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2Spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt


class RingMicRecorder(
    private val context: Context
) {
    companion object {
        private const val TAG = "RingMicRecorder"
        /**
         * Size of buffer used for transfer, by default
         */
        private const val TRANSFER_BUFFER_SIZE = 10 * 1024
    }

    val dataDir by lazy {
        var dir = context.getExternalFilesDir("ring_audio")
        if (dir == null) {
            dir = File(context.filesDir, "ring_audio")
        }
        if (!dir!!.exists()) {
            dir!!.mkdirs()
        }
        dir!!
    }

    private val nuixSensorManager: NuixSensorManager by lazy {
        (context as MainApplication).nuixSensorManager
    }

    private val voiceRecognizer: VoiceRecognizer by lazy {
        XfVoiceRecognizer.instance
    }

    private val scope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private var pcmFile: File? = null

    private var voiceRecognizeListener: ((String) -> Unit)? = null

    private var voiceVolumeChangeListener: ((Int, ByteArray?) -> Unit)? = null

    private var voiceRecognizeEndListener: ((Int?) -> Unit)? = null

    private var voiceBeginListener: (() -> Unit)? = null

    private var isRecording = AtomicBoolean(false)
    fun isRecording(): Boolean {
        return isRecording.get()
    }

    private fun prepareVoiceRecognize() {
        Log.e("RingV2", "prepare voice recognize")
        if (voiceRecognizer.isListening()) {
            voiceRecognizer.stopListen()
        }
        voiceRecognizer.initRecognize(
            useWriteAudio = true,
            hasPunctuation = true,
            sampleRate = 8000
        )
        voiceRecognizer.setRecognizeListener(object: RecognizeListener {
            override fun onVoiceBegin() {
                Log.e("RingV2", "recognize begin")
                voiceBeginListener?.invoke()
            }

            override fun onRecognize(words: List<String?>?) {
                Log.e("RingV2", "onRecognize: ${words?.get(0)}")
                if (!words.isNullOrEmpty()) {
                    voiceRecognizeListener?.invoke(words[0] ?:"")
                }
            }

            override fun onError(code: Int, msg: String?) {
                Log.e("RingV2", "recognize error, code: $code, msg: $msg")
                stop()
                voiceRecognizeEndListener?.invoke(code)
            }

            /**
             * 语音输入结束，当主动结束监听时不会调用此方法
             */
            override fun onVoiceEnd() {
                Log.e("RingV2", "recognize end")
                stop()
                voiceRecognizeEndListener?.invoke(null)
            };

            override fun onVolumeChanged(volume: Int, data: ByteArray?) {
//                data?.let {
//                    val md5Str = BigInteger(1, md.digest(it)).toString(16).padStart(32, '0')
//                    val pair = dataTimeMap[md5Str]
//                    pair?.apply {
//                        Log.e("RingV2", "onVolumeChanged: $volume, data: ${md5Str}, seq: ${this.first}, time interval: ${System.currentTimeMillis() - this.second} ms")
//                    }
//                }
                voiceVolumeChangeListener?.invoke(volume, data)
            }
        })
    }

    fun setVoiceRecognizeListener(listener: ((String) -> Unit)?) {
        voiceRecognizeListener = listener
    }

    fun setVoiceVolumeChangeListener(listener: ((Int, ByteArray?) -> Unit)?) {
        voiceVolumeChangeListener = listener
    }

    fun setVoiceRecognizeEndListener(listener: ((Int?) -> Unit)?) {
        voiceRecognizeEndListener = listener
    }

    fun setRecognizeBeginListener(listener: (() -> Unit)?) {
        voiceBeginListener = listener
    }

    var recordJob: Job? = null
    val md = MessageDigest.getInstance("MD5")
    val dataTimeMap: MutableMap<String, Pair<Int, Long>> = mutableMapOf()

    fun start() {
        if (!isRecording.compareAndSet(false, true)) {
            return
        }
        Log.e("RingV2", "start record")
//        if (nuixSensorManager.defaultRingV2.target == null) {
//            nuixSensorManager.refreshDefaultSensors()
//        }
        recordJob?.cancel()
        (nuixSensorManager.defaultRingV2.target as RingV2?)?.let { ring ->
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
            pcmFile = File(dataDir, "ring_audio.pcm")
            if (pcmFile?.exists() == true) {
                pcmFile?.delete()
            }
            scope.launch {
                recordJob = CoroutineScope(Dispatchers.IO).launch {
                    prepareVoiceRecognize()
                    voiceRecognizer.startListen()
                    dataTimeMap.clear()
                    ring.getFlow<RingV2AudioData>(RingSpec.audioFlowName(ring))?.collect {
                       val data = it.data.toByteArray()
                       val writeResult = voiceRecognizer.writeAudio(data)
                       // val timeInterval = System.currentTimeMillis() - it.timestamp
                       // val md5Str = BigInteger(1, md.digest(data)).toString(16).padStart(32, '0')
                       // dataTimeMap[md5Str] = Pair<Int, Long>(it.sequenceId, System.currentTimeMillis())
                       //Log.e("RingV2Data", "write audio data, seq: ${it.sequenceId}, len: ${it.length}, result: $writeResult")
//                       if (writeResult != ErrorCode.SUCCESS) {
//                           Log.e(TAG, "write audio data fail")
//                       }
                       pcmFile?.appendBytes(data)
                   }
                }.apply {
                    invokeOnCompletion {
                        voiceRecognizer.stopListen()
                    }
                }
                ring.openMic()
            }
        }
    }

    fun stop() {
        if (!isRecording.compareAndSet(true, false)) {
            return
        }
        Log.e("RingV2", "stop record")
        recordJob?.cancel()
        (nuixSensorManager.defaultRingV2.target as RingV2?)?.let { ring ->
            scope.launch {
//                ring.writeNonLinearLED(
//                    shortArrayOf(10000, 10000, 10000, 10000),
//                    enableRed = false, enableBlue = false, enableGreen = true,
//                    cycleCnt = 20, playCnt = 3, playMode = RingV2Spec.LED_PLAY_MODE.LOOP
//                )
                ring.closeMic()
                pcmFile?.let { pcm ->
                    val timeSuffix = timeFormat(LocalDateTime.now())
                    val wavFile = File(dataDir, "ring_audio_${timeSuffix}.wav")
                    try {
                        PCMToWAV(pcm, wavFile, 1, 8000, 8000, 16)
                    } catch (e: Exception) {
                        Log.e(TAG, "PCMToWAV error: ${e.message}")
                    }
                }
            }
        }
    }

    fun getPCMFile(): File? {
        return pcmFile
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


    fun startTestAudioRecord() {
        startRecording()
    }

    fun stopTestAudioRecord() {
        stopRecording()
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (!isRecording.compareAndSet(false, true)) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        ).apply {
            this.startRecording()
            recordingThread = Thread({ writeAudioDataToFile() }, "AudioRecorder Thread").apply {
                start()
            }
        }
    }

    private fun writeAudioDataToFile() {
        val audioData = ByteArray(BUFFER_SIZE)
        var os: FileOutputStream? = null
        try {
            pcmFile = File(dataDir, "ring_audio.pcm")
            if (pcmFile?.exists() == true) {
                pcmFile?.delete()
            }
            os = FileOutputStream(pcmFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (os != null) {
            while (isRecording.get()) {
                val read: Int = audioRecord!!.read(audioData, 0, BUFFER_SIZE)
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(audioData)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            try {
                os.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val timeSuffix = timeFormat(LocalDateTime.now())
            val wavFile = File(dataDir, "ring_audio_${timeSuffix}.wav")
            PCMToWAV(pcmFile!!, wavFile, 1, SAMPLE_RATE, SAMPLE_RATE, 16)
        }
    }

    private fun stopRecording() {
        if (!isRecording.compareAndSet(true, false)) {
            return
        }
        if (audioRecord != null) {
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            recordingThread = null
        }
    }
}