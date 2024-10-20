package com.euxcet.viturering.ui.page.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.euxcet.viturering.ring.RingManager
import com.euxcet.viturering.user.User
import com.euxcet.viturering.user.bean.TaskBean
import com.hcifuture.producer.recorder.Recorder
import com.hcifuture.producer.recorder.RecorderProvider
import com.hcifuture.producer.sensor.NuixSensorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recorderProvider: RecorderProvider,
    ringManager: RingManager,
    private val user: User,
) : ViewModel() {

    private val scope = CoroutineScope(Dispatchers.Default)
    var viewStates by mutableStateOf(HomeViewState())
        private set
    private var recorder: Recorder? = null

    init {
        viewStates = viewStates.copy(tasks = user.bean.tasks)
        scope.launch {
            ringManager.registerListener {
                onStateCallback {
                    viewStates = viewStates.copy(ringState = it)
                }
            }
        }
    }

    fun dispatch(action: HomeViewAction) {
        when (action) {
            HomeViewAction.ClickRecordButton -> clickRecordButton()
        }
    }

    private fun clickRecordButton() {
        // TODO: validate
        if (!viewStates.isRecording) {
            scope.launch {
                recorder = recorderProvider.createImuRecorder()
                recorder?.start(user.bean.userID)
            }
        } else {
            scope.launch {
                recorder?.stop()
            }
        }
        viewStates = viewStates.copy(isRecording = !viewStates.isRecording)
    }
}

data class HomeViewState (
    val user: String = "User",
    val ringState: NuixSensorState = NuixSensorState.DISCONNECTED,
    val tasks: List<TaskBean> = listOf(),
    val isRecording: Boolean = false,
) {
    val stateColor: Color
        get() = if (ringState == NuixSensorState.CONNECTED) Color.Green else Color.Red
    val stateText: String
        get() = if (ringState == NuixSensorState.CONNECTED) "已连接" else "未连接"
}

sealed class HomeViewAction {
    data object ClickRecordButton: HomeViewAction()
}