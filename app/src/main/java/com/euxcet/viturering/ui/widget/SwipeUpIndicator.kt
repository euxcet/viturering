package com.euxcet.viturering.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

@Composable
fun SwipeUpIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.size(120.dp)
    ) {
        Canvas(
            modifier = Modifier.size(80.dp)
        ) {
            drawCircle(
                color = Color.Black,
                radius = size.minDimension / 2,
                style = Stroke(
                    width = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier.size(30.dp)
        ) {
            drawLine(
                start = center.copy(y = 0f),
                end = center.copy(y = size.height),
                color = Color.Black,
                strokeWidth = 4.dp.toPx()
            )

            drawPath(
                path = Path().apply {
                    moveTo(center.x - 5.dp.toPx(), 5.dp.toPx())
                    lineTo(center.x, 0f)
                    lineTo(center.x + 5.dp.toPx(), 5.dp.toPx())
                    close()
                },
                color = Color.Black,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}
