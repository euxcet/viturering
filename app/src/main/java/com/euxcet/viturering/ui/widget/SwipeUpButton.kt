package com.euxcet.viturering.ui.widget

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@Composable
fun SwipeButton(
    onSwipeUp: () -> Unit,
    isRecording: Boolean,
) {
    val isCurrentTaskAvailable = true
    var isCompleted = false
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .size(120.dp)
            .pointerInput(isCurrentTaskAvailable && !isCompleted) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (!isCurrentTaskAvailable || isCompleted) {
                            return@detectVerticalDragGestures
                        }
                        if (offsetY < -100f) {
                            onSwipeUp()
                        }
                        offsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!isCurrentTaskAvailable || isCompleted) {
                            return@detectVerticalDragGestures
                        }
                        offsetY += dragAmount
                        offsetY = min(max(offsetY, -150.0f), 0.0f)
                        change.consume()
                    }
                )
            }
            .offset(y = offsetY.dp)
            .background(
                if (isCurrentTaskAvailable) {
                    when {
                        isCompleted -> Color.Gray
                        isRecording -> Color.Red
                        else -> Color.Green
                    }
                } else {
                    Color.Gray
                },
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isCompleted -> "已完成"
                isCurrentTaskAvailable -> if (isRecording) "结束" else "开始"
                else -> "无任务"
            },
            color = Color.White,
            fontSize = 24.sp
        )
    }
}