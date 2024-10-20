package com.euxcet.viturering.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.euxcet.viturering.R
import com.euxcet.viturering.user.bean.TaskBean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskIndicator(
    task: TaskBean,
    index: Int,
    currentTime: Date,
    isTaskCompleted: Boolean,
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val taskStartTime = timeFormat.parse(task.startTime)
    val isPast = currentTime.after(timeFormat.parse(task.endTime))
    val isCurrent = currentTime.after(taskStartTime) && currentTime.before(timeFormat.parse(task.endTime))

    val backgroundColor = when {
        isPast -> Color(0xFFADD8E6)
        isCurrent -> Color(0xFF00BFFF)
        else -> Color.LightGray
    }
    val textColor = if ((!isTaskCompleted) && isCurrent) Color.Black else Color.Gray
    val iconPainter = if (isTaskCompleted) painterResource(id = R.drawable.ic_check) else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
                .background(backgroundColor, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = task.startTime.substring(0, 5),
            color = textColor,
            fontSize = 14.sp
        )

        Text(
            text = task.taskName,
            color = textColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
