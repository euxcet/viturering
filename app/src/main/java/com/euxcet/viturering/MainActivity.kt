package com.euxcet.viturering

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.view.children
import com.euxcet.viturering.pages.home.HomeActivity
import com.euxcet.viturering.utils.LanguageUtils
import com.euxcet.viturering.utils.Permission
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.data.RingTouchEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    @Inject
    lateinit var ringManager: RingManager

    private var overlayView: OverlayView? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        Permission.requestPermissions(this, listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.POST_NOTIFICATIONS
        ))
        setContentView(R.layout.main)
        findViewById<TextView>(R.id.toHome).setOnClickListener {
            val intent = Intent(this@MainActivity, HomeActivity::class.java)
            startActivity(intent)
        }
        findViewById<TextView>(R.id.calibrate).setOnClickListener {
            ringManager.calibrate()
            Toast.makeText(this, "校准成功", Toast.LENGTH_SHORT).show()
        }
        CoroutineScope(Dispatchers.Default).launch {
            checkResFiles()
        }
    }

    override fun onResume() {
        super.onResume()
        connectRing()
    }

    private fun createRingButton(ring: NuixSensor): Button {
        val ringLayout = findViewById<LinearLayout>(R.id.ringLayout)
        val button = Button(this@MainActivity)
        button.text = ring.name
        if (ringManager.isActive(ring)) {
            button.setBackgroundColor(Color.rgb(30, 150, 30))
        } else {
            button.setBackgroundColor(Color.rgb(220, 220, 220))
        }
        button.setOnClickListener {
            if (!ringManager.isActive(ring)) {
                for (child in ringLayout.children) {
                    if ((child as Button).text == ring.name) {
                        child.setBackgroundColor(Color.rgb(30, 150, 30))
                    } else {
                        child.setBackgroundColor(Color.rgb(220, 220, 220))
                    }
                }
                ringManager.selectRing(ring)
            } else {
                for (child in ringLayout.children) {
                    child.setBackgroundColor(Color.rgb(220, 220, 220))
                }
                ringManager.deselect()
            }
        }
        return button
    }

    private fun connectRing() {
        val touchView = findViewById<TextView>(R.id.touchView)
        val gestureView = findViewById<TextView>(R.id.gestureView)
        val statusView = findViewById<TextView>(R.id.statusView)
        val ringLayout = findViewById<LinearLayout>(R.id.ringLayout)
        ringManager.registerListener {
            onConnectCallback { // Connect
                runOnUiThread {
                    ringLayout.removeAllViews()
                    for (ring in ringManager.rings()) {
                        ringLayout.addView(createRingButton(ring))
                    }
                }
            }
            onGestureCallback { // Gesture
                runOnUiThread {
                    Log.e("Nuix", "Gesture: $it")
                    val gestureText = "手势: ${LanguageUtils.gestureChinese(it)}"
                    gestureView.text = gestureText
                    when (it) {
                        "pinch" -> {
                            overlayView?.select()
                        }
                        "middle_pinch" -> {
                            overlayView?.switch()
                        }
                        "snap" -> {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            startActivity(intent)
                        }
                        "circle_clockwise" -> {
                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            startActivity(intent)
                        }
                        "touch_ring" -> {
                            overlayView?.reset()
                        }
                    }
                }
            }
            onMoveCallback { // Move
                runOnUiThread {
                    overlayView?.move(it.first, it.second)
                }
            }
            onStateCallback { // State
                runOnUiThread {
                    val statusText = "连接状态: ${LanguageUtils.statusChinese(it)}"
                    statusView.text = statusText
                }
            }
            onTouchCallback { // Touch
                runOnUiThread {
                    val touchText = "触摸: ${(it.data)}"
                    touchView.text = touchText
                    when (it.data) {
                        RingTouchEvent.BOTTOM_BUTTON_CLICK -> {
                            overlayView?.reset()
                        }
                        RingTouchEvent.TAP -> {
                            overlayView?.reset()
                        }
                        else -> {}
                    }
                }
            }
        }
        ringManager.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun checkResFiles() {
        val resFiles = listOf(
            "models",
            "res/car_chunk_close.mp4",
            "res/car_chunk_open.mp4",
            "res/car_screen_down.mp4",
            "res/car_screen_up.mp4",
            "res/car_view_back.mp4",
            "res/car_window_close.mp4",
            "res/car_window_open.mp4",
            "res/demo.mp4",
            "res/game_wave_video.mp4",
            "res/game_background.mp4"
        )
        val externalRootPath = getExternalFilesDir(null)?.absolutePath ?: return
        resFiles.forEach { relativePath ->
            val path =  FileSystems.getDefault().getPath(externalRootPath, relativePath)
            if (relativePath.indexOfLast { it == '.' } == -1) {
                // is directory
                if (!path.exists()) {
                    path.createDirectories()
                }
                val file = path.listDirectoryEntries().firstOrNull { it.isRegularFile() }
                if (file == null) {
                    try {
                        // need copy asset to external storage
                        val assetDirPath = "download/$relativePath"
                        assets.list(assetDirPath)?.forEach {
                            copyAssetFileToExternalStorage("$assetDirPath/$it", path.toFile())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "copy asset file failed", e)
                    }
                }
            } else {
                // is file
                if (!path.exists()) {
                    val assetDirPath = "download/$relativePath"
                    copyAssetFileToExternalStorage(assetDirPath, path.toFile().parentFile!!)
                }
            }
        }
    }

    private fun copyAssetFileToExternalStorage(assetFilePath: String, externalDir: File) {
        try {
            val file = File(externalDir, assetFilePath.substringAfterLast('/'))
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                assets.open(assetFilePath).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                Log.d(TAG, "Copied asset file to external storage: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset file to external storage", e)
        }
    }

}
