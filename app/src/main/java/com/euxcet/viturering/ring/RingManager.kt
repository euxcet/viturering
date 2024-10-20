package com.euxcet.viturering.ring

import com.hcifuture.producer.detector.GestureDetector
import com.hcifuture.producer.detector.OrientationDetector
import com.hcifuture.producer.sensor.NuixSensor
import com.hcifuture.producer.sensor.NuixSensorManager
import com.hcifuture.producer.sensor.NuixSensorState
import com.hcifuture.producer.sensor.data.RingTouchData
import com.hcifuture.producer.sensor.external.ring.RingSpec
import com.hcifuture.producer.sensor.external.ring.ringV1.RingV1
import com.hcifuture.producer.sensor.external.ring.ringV2.RingV2
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
) {
    inner class ListenerBuilder {
        internal var touchCallback: ((RingTouchData) -> Unit)? = null
        internal var moveCallback: ((Pair<Float, Float>) -> Unit)? = null
        internal var gestureCallback: ((String) -> Unit)? = null
        internal var stateCallback: ((NuixSensorState) -> Unit)? = null
        internal var connectCallback: ((List<NuixSensor>) -> Unit)? = null

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
    }

    private var connected = false
    private lateinit var listener: ListenerBuilder
    var selectedRingName: String? = null

    fun registerListener(builder: ListenerBuilder.() -> Unit) {
        listener = ListenerBuilder().also(builder)
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

    fun connect() {
        if (connected) {
            if (::listener.isInitialized) {
                listener.connectCallback?.invoke(nuixSensorManager.rings())
            }
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            for (sensor in nuixSensorManager.internalSensors()) {
                sensor.connect()
            }
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
                                delay(100)
                            }
                            break
                        }
                    }
                    delay(5000)
                } else {
                    delay(10000)
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

        CoroutineScope(Dispatchers.Default).launch {
            val ring = nuixSensorManager.defaultRing
            while (true) {
                if (::listener.isInitialized) {
                    listener.stateCallback?.invoke(ring.status)
                }
                delay(1000)
            }
        }
    }
}