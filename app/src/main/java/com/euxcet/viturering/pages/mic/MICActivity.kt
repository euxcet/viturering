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

@AndroidEntryPoint
class MICActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MICActivity"
    }

    @Inject
    lateinit var ringManager: RingManager

    private var inputView: EditText? = null
    private var voiceStatusView: TextView? = null

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
        XfVoiceUtil.getInstance().setVoiceRecognize(object: XfVoiceUtil.RecognizeListener {
            override fun onRecognize(words: MutableList<String>?) {
                runOnUiThread {
                    words?.firstOrNull()?.let {
                        inputView?.setText(it)
                        voiceStatusView?.text = "正在识别..."
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
        XfVoiceUtil.getInstance().setVoiceRecognize(null)
    }

    private fun openVoice() {
        try {
            if (XfVoiceUtil.getInstance().startListening()) {
                ringManager.closeIMU()
                ringManager.openMic()
                runOnUiThread {
                    voiceStatusView?.text = "正在识别..."
                }
            } else {
                Log.e(TAG, "开启语音识别失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "开启语音识别错误: $e")
        }
    }

    private fun closeVoice() {
        try {
            ringManager.closeMic()
            XfVoiceUtil.getInstance().stopListening()
            ringManager.openIMU()
            runOnUiThread {
                voiceStatusView?.text = "语音识别结束"
            }
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
                Log.e(TAG, "Mic: ${it.data}")
                try {
                    val data = it.data.toByteArray()
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
}