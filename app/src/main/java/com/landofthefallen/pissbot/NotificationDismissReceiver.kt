package com.landofthefallen.pissbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import android.util.Log

class NotificationDismissReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationDismissReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.landofthefallen.pissbot.DISMISS_NOTIFICATION") {
            val notificationId = intent.getIntExtra("notification_id", -1)
            if (notificationId != -1) {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(notificationId)
                Log.d(TAG, "Notification $notificationId dismissed")
            }
        }
    }
} 