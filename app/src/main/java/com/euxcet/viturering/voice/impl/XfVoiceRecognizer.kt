package com.euxcet.viturering.voice.impl

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.euxcet.viturering.MainApplication
import com.euxcet.viturering.voice.RecognizeListener
import com.euxcet.viturering.voice.Result
import com.euxcet.viturering.voice.VoiceConstants
import com.euxcet.viturering.voice.VoiceRecognizer
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.SpeechUtility
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class XfVoiceRecognizer private constructor(private val context: Context) : VoiceRecognizer {

    companion object {
        const val TAG = "XfVoiceRecognizer"

        val instance by lazy {
            XfVoiceRecognizer(MainApplication.instance)
        }
    }

    /**
     * 无UI的语音识别模块参数
     */
    private var mIat: SpeechRecognizer? = null
    private var mInitCode = ErrorCode.SUCCESS
    private var mRecognizeListener: RecognizeListener? = null

    // 用HashMap存储听写结果
    private val mIatResults: HashMap<String, List<String>> = LinkedHashMap()

    override fun initRecognize(
        useWriteAudio: Boolean,
        hasPunctuation: Boolean,
        sampleRate: Int,
        params: Map<String, Any>?
    ): Result {
        // 请勿在“=”与appid之间添加任何空字符或者转义符
        SpeechUtility.createUtility(
            context,
            SpeechConstant.APPID + "=" + VoiceConstants.XUNFEI_APP_ID
        )
        mIat = SpeechRecognizer.getRecognizer()
        if (mIat == null) {
            mIat = SpeechRecognizer.createRecognizer(context) { code -> mInitCode = code }
        }

        if (mIat == null) {
            // 初始化失败
            return Result(success = false, message = "初始化失败")
        }
        mIat?.apply {
            //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
            setParameter(SpeechConstant.CLOUD_GRAMMAR, null)
            setParameter(SpeechConstant.SUBJECT, null)
            //设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
            setParameter(SpeechConstant.RESULT_TYPE, "json")
            //此处engineType为“cloud”
            setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            //设置语音输入语言，zh_cn为简体中文
            setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            //设置结果返回语言
            setParameter(SpeechConstant.ACCENT, "mandarin") //普通话

//        mIat.setParameter(SpeechConstant.ACCENT, "gansunese");  //甘肃方言
//        mIat.setParameter(SpeechConstant.ACCENT, "cn_cantonese");  //普通话 + 广东方言
            // 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
            // 取值范围{1000～10000}
            setParameter(SpeechConstant.VAD_BOS, "10000")
            //设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
            // 自动停止录音，范围{0~10000}
            setParameter(SpeechConstant.VAD_EOS, "10000")
            //设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
            setParameter(SpeechConstant.ASR_PTT, if (hasPunctuation) "1" else "0")
            //设置语音录入的音量,仅支持"8000"(网络需求)和"16000"
            setParameter(SpeechConstant.SAMPLE_RATE, sampleRate.toString())
            //设置返回的候选结果，根据可能性，取值[1,5],需要在讯飞开通功能，不开通只返回一个结果
            setParameter(SpeechConstant.ASR_WBEST, "1")
            setParameter(SpeechConstant.ASR_NBEST, "1")
            setParameter("dwa", "wpgs") // 开启动态纠正功能
            if (useWriteAudio) {
                setParameter(SpeechConstant.AUDIO_SOURCE, "-1")
                setParameter(SpeechConstant.AUDIO_FORMAT_AUE, "raw")
            }
        }
        return Result(success = true)
    }

    override fun startListen(): Result {
        //开始识别，并设置监听器
        val iat = mIat ?: return Result(success = false, message = "未初始化")
        val errorCode = iat.startListening(xfRecognizeListener)
        if (errorCode == ErrorCode.SUCCESS) {
            Log.e("TEST_VOICE", "xunfei start listening")
            mIatResults.clear()
            return Result(success = true)
        }
        return Result(success = false, message = "启动失败", code = errorCode)
    }

    override fun stopListen() {
        mIat?.stopListening()
    }

    override fun writeAudio(bytes: ByteArray): Int {
        return mIat?.writeAudio(bytes, 0, bytes.size) ?: -1
    }

    override fun isListening(): Boolean {
        return mIat?.isListening ?: false
    }

    override fun setRecognizeListener(listener: RecognizeListener) {
        mRecognizeListener = listener
    }

    /**
     * 听写监听器。
     */
    private val xfRecognizeListener: RecognizerListener = object : RecognizerListener {
        override fun onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            // showTip("开始说话");
            mRecognizeListener?.onVoiceBegin()
        }

        override fun onError(error: SpeechError) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。

            //showTip(error.getPlainDescription(true));
            stopListen()
            val speakText = error.getPlainDescription(true)
            Log.e(TAG, "onError: ${error.errorCode}, ${speakText}")
            var errorString = "语音异常"
            if (speakText.contains("网络")) {
                errorString = "网络连接异常"
            } else if (speakText.contains("说话")) {
                errorString = "未听清内容"
            } else if (speakText.contains("启动录音失败")) {
                errorString = "没有麦克风权限"
            } else {
                errorString = "其他异常:$speakText"
            }
            mRecognizeListener?.onError(error.errorCode, errorString)
        }

        override fun onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            //showTip("结束说话");
//            Utility.enqueueSpeak("语音输入已结束");
            mRecognizeListener?.onVoiceEnd()
        }

        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            handleRplResult(results, isLast)
        }

        override fun onVolumeChanged(volume: Int, data: ByteArray) {
            mRecognizeListener?.onVolumeChanged(volume, data)
        }

        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    }

    private fun handleRplResult(results: RecognizerResult, isLast: Boolean) {
        val text = results.resultString
        val resultTextBuilder = StringBuilder()
        var sn: String? = null
        var pgs: String? = null
        var rg: String? = null
        var words: JSONArray? = null
        // 读取json结果中的sn字段
        try {
            val resultJson = JSONObject(results.resultString)
            sn = resultJson.optString("sn")
            pgs = resultJson.optString("pgs")
            rg = resultJson.optString("rg")
            words = resultJson.getJSONArray("ws")
            for (i in 0 until words.length()) {
                val items = words.getJSONObject(i).getJSONArray("cw")

                /** 转写结果词，默认使用第一个结果  */
                val obj = items.getJSONObject(0)
                resultTextBuilder.append(obj.getString("w"))
                /** 尝试默认使用最长的结果  */
                /*String longestWord = "";
                for (int j = 0; j < items.length(); j++) {
                    String curWord = items.getJSONObject(j).getString("w");
                    if (curWord.length() > longestWord.length()) {
                        longestWord = curWord;
                    }
                }
                resultTextBuilder.append(longestWord);*/
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        //如果pgs是rpl就在已有的结果中删除掉要覆盖的sn部分
        if (pgs == "rpl") {
            val strings = rg!!.replace("[", "").replace("]", "").split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val begin = strings[0].toInt()
            val end = strings[1].toInt()
            for (i in begin..end) {
                mIatResults.remove(i.toString() + "")
            }
        }
        if (sn != null) {
            mIatResults[sn] = mutableListOf(text, resultTextBuilder.toString())
        }
        val resultBuffer = StringBuilder()
        for (values in mIatResults.values) {
            resultBuffer.append(if (values.size > 1) values[1] else "")
        }
        Log.e("TEST_VOICE", "xunfei result string:" + results.resultString)
        Log.e("TEST_VOICE", "rp result:$resultBuffer")
        mRecognizeListener?.onRecognize(mutableListOf(resultBuffer.toString()))
    }

    override fun destroy() {
        mIat?.destroy()
        SpeechUtility.getUtility()?.destroy()
    }
}