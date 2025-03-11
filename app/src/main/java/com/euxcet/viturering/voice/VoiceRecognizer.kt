package com.euxcet.viturering.voice

interface VoiceRecognizer {

    fun initRecognize(
        useWriteAudio: Boolean = false,
        hasPunctuation: Boolean = false,
        sampleRate: Int = 16000,
        params: Map<String, Any>? = null
    ): Result

    fun setRecognizeListener(listener: RecognizeListener)

    fun startListen(): Result

    fun stopListen()

    fun writeAudio(bytes: ByteArray): Int

    fun isListening(): Boolean

    fun destroy()
}

data class Result(
    val success: Boolean,
    val code: Int = 0,
    val message: String? = null
)


interface RecognizeListener {
    fun onRecognize(words: List<String?>?)
    fun onError(code: Int, msg: String?) {}
    fun onVoiceBegin() {}
    /**
     * 语音输入结束，当主动结束监听时不会调用此方法
     */
    fun onVoiceEnd() {}
    fun onVolumeChanged(volume: Int, data: ByteArray?) {}
}
