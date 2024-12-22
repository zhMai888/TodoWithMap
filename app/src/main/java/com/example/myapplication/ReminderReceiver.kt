package com.example.myapplication

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("TASK_NAME")

        // 创建通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, "task_reminder_channel")
            .setSmallIcon(R.drawable.notify) // 设置通知图标
            .setContentTitle("任务提醒: $taskName")
            .setContentText("任务$taskName 的时间到了!!!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // 显示通知
        notificationManager.notify(taskName.hashCode(), notification)
    }
}