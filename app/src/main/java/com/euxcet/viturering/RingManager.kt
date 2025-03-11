package com.euxcet.viturering

import android.util.Log
import com.hcifuture.producer.common.network.bean.CharacterResult
import com.hcifuture.producer.detector.GestureDetector
import com.hcifuture.producer.detector.OrientationDetector
import com.hcifuture.producer.detector.TouchState
import com.hcifuture.producer.detector.WordDetector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.data.RingV2AudioData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV1.RingV1
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2Spec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingManager @Inject constructor(
    private val nuixSensorManager: NuixSensorManager,
    private val gestureDetector: GestureDetector,
    private val orientationDetector: OrientationDetector,
    private val wordDetector: WordDetector,
) {
    inner class ListenerBuilder {
        internal var touchCallback: ((RingTouchData) -> Unit)? = null
        internal var moveCallback: ((Pair<Float, Float>) -> Unit)? = null
        internal var gestureCallback: ((String) -> Unit)? = null
        internal var stateCallback: ((NuixSensorState) -> Unit)? = null
        internal var connectCallback: ((List<NuixSensor>) -> Unit)? = null
        internal var planeEventCallback: ((TouchState) -> Unit)? = null
        internal var planeMoveCallback: ((Pair<Float, Float>) -> Unit)? = null
        internal var planeCharacterCallback: ((CharacterResult) -> Unit)? = null
        internal var micDataCallback: ((RingV2AudioData) -> Unit)? = null

        fun onTouchCallback(callback: ((RingTouchData) -> Unit)) {
            touchCallback = callback
        }

        fun onMoveCallback(callback: ((Pair<Float, Float>) -> Unit)) {
            moveCallback = callback
        }

        fun onGestureCallback(callback: ((String) -> Unit)) {
            gestureCallback = callback
        }

        fun onStateCallback(callback: ((NuixSensorState) -> Unit)) {
            stateCallback = callback
        }

        fun onConnectCallback(callback: ((List<NuixSensor>) -> Unit)) {
            connectCallback = callback
        }

        fun onPlaneEventCallback(callback: ((TouchState) -> Unit)) {
            planeEventCallback = callback
        }

        fun onPlaneMoveCallback(callback: ((Pair<Float, Float>) -> Unit)) {
            planeMoveCallback = callback
        }

        fun onPlaneCharacterCallback(callback: ((CharacterResult) -> Unit)) {
            planeCharacterCallback = callback
        }

        fun onMicDataCallback(callback: ((RingV2AudioData) -> Unit)) {
            micDataCallback = callback
        }
    }

    private var connected = false
    private lateinit var listener: ListenerBuilder
    var selectedRingName: String? = null

    fun registerListener(builder: ListenerBuilder.() -> Unit) {
        listener = ListenerBuilder().also(builder)
    }

    fun deselect() {
        nuixSensorManager.defaultRing.disconnect()
        selectedRingName = ""
    }

    fun selectRing(ring: NuixSensor) {
        if (selectedRingName != ring.name) {
            nuixSensorManager.defaultRing.disconnect()
        }
        selectedRingName = ring.name
    }

    fun isActive(ring: NuixSensor): Boolean {
        return ring.name == selectedRingName
    }

    fun rings(): List<NuixSensor> {
        return nuixSensorManager.rings()
    }

    fun ringV1s(): List<RingV1> {
        return nuixSensorManager.ringV1s()
    }

    fun ringV2s(): List<RingV2> {
        return nuixSensorManager.ringV2s()
    }

    fun calibrate() {
        if (nuixSensorManager.defaultRing.target is RingV2) {
            (nuixSensorManager.defaultRing.target as RingV2).calibrate()
        }
    }

    fun disconnect() {
        nuixSensorManager.defaultRing.disconnect()
    }

    fun openIMU() {
        CoroutineScope(Dispatchers.Default).launch {
            if (nuixSensorManager.defaultRing.target is RingV2) {
                (nuixSensorManager.defaultRing.target as RingV2).openIMU()
            }
        }
    }

    fun closeIMU() {
        CoroutineScope(Dispatchers.Default).launch {
            if (nuixSensorManager.defaultRing.target is RingV2) {
                (nuixSensorManager.defaultRing.target as RingV2).closeIMU()
            }
        }
    }

    fun openMic() {
        CoroutineScope(Dispatchers.Default).launch {
            if (nuixSensorManager.defaultRing.target is RingV2) {
                (nuixSensorManager.defaultRing.target as RingV2).openMic()
            }
        }
    }

    fun closeMic() {
        CoroutineScope(Dispatchers.Default).launch {
            if (nuixSensorManager.defaultRing.target is RingV2) {
                (nuixSensorManager.defaultRing.target as RingV2).closeMic()
            }
        }
    }

    fun connect() {
        if (connected) {
            if (::listener.isInitialized) {
                listener.connectCallback?.invoke(nuixSensorManager.rings())
            }
            return
        }
        connected = true
        // Connect
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (!nuixSensorManager.defaultRing.disconnectable()) {
                    for (ring in nuixSensorManager.rings()) {
                        if (selectedRingName == null) {
                            selectedRingName = ring.name
                        }
                        ring.disconnect()
                    }
                    nuixSensorManager.scanAll(timeout = 3000L)
                    if (::listener.isInitialized) {
                        listener.connectCallback?.invoke(nuixSensorManager.rings())
                    }

                    for (ring in nuixSensorManager.rings()) {
                        if (ring.name == selectedRingName) {
                            ring.connect()
                            nuixSensorManager.defaultRing.switchTarget(ring)
                            while (ring.status == NuixSensorState.CONNECTING) {
                                delay(500)
                            }
                            break
                        }
                    }
                    delay(2000)
                } else {
                    delay(6000)
                }
            }
        }

        // Touch
        CoroutineScope(Dispatchers.Default).launch {
            val ring = nuixSensorManager.defaultRing
            ring.getProxyFlow<RingTouchData>(
                RingSpec.touchEventFlowName(ring)
            )?.collect {
                if (::listener.isInitialized) {
                    listener.touchCallback?.invoke(it)
                }
            }
        }

        // Move cursor
        orientationDetector.start()
        CoroutineScope(Dispatchers.Default).launch {
            orientationDetector.eventFlow.collect {
                if (::listener.isInitialized) {
                    listener.moveCallback?.invoke(it)
                }
            }
        }

        // Gesture
        gestureDetector.start()
        CoroutineScope(Dispatchers.Default).launch {
            gestureDetector.eventFlow.collect {
                if (::listener.isInitialized) {
                    listener.gestureCallback?.invoke(it)
                }
            }
        }

        // Status
        CoroutineScope(Dispatchers.Default).launch {
            val ring = nuixSensorManager.defaultRing
            while (true) {
                if (::listener.isInitialized) {
                    listener.stateCallback?.invoke(ring.status)
                }
                delay(1000)
            }
        }

        // mic
        CoroutineScope(Dispatchers.IO).launch {
            val ring = nuixSensorManager.defaultRing
            ring.getProxyFlow<RingV2AudioData>(RingSpec.audioFlowName(ring))?.collect {
                if (::listener.isInitialized) {
                    listener.micDataCallback?.invoke(it)
                }
            }
        }

        // Word
        wordDetector.start()
        CoroutineScope(Dispatchers.Default).launch {
            wordDetector.gestureFlow.collect {
                if (::listener.isInitialized) {
                    listener.planeEventCallback?.invoke(it)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            wordDetector.moveFlow.collect {
                if (::listener.isInitialized) {
                    listener.planeMoveCallback?.invoke(it)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            wordDetector.characterFlow.collect {
                if (::listener.isInitialized) {
                    listener.planeCharacterCallback?.invoke(it)
                }
            }
        }
    }
}