package com.euxcet.viturering.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.util.Log
import com.euxcet.viturering.MainActivity
import com.euxcet.viturering.R

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("taskName") ?: "任务"
        val taskID = intent.getStringExtra("taskID") ?: "未知任务"
        val notifTime = intent.getLongExtra("notifTime", 0L)
        val currentDay = intent.getIntExtra("currentDay", 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建点击通知后打开的Intent
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("taskID", taskID)  // 可以传递额外的数据给Activity
        }

        // 使用 currentDay、taskID 和 notifTime 生成唯一的 requestCode
        val requestCode = (currentDay.toString() + taskID + notifTime.toString()).hashCode()

        // 创建PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,  // 使用唯一的 requestCode
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建通知
        val notification = NotificationCompat.Builder(context, "TASK_CHANNEL_ID")
            .setSmallIcon(R.drawable.ic_notification)  // 确保你有这个图标资源
            .setContentTitle("任务提醒")
            .setContentText("$taskName")
            .setPriority(NotificationCompat.PRIORITY_MAX)  // 设置通知优先级为高
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)  // 设置PendingIntent，点击通知时触发
            .build()

        // 发送通知
        notificationManager.notify(requestCode, notification)  // 使用相同的 requestCode

        Log.d("NotificationReceiver", "Notification sent for task: $taskName at $taskID with requestCode: $requestCode")
    }
}
