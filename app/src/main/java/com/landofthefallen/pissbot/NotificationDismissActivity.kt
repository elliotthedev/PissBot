package com.landofthefallen.pissbot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat

class NotificationDismissActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the notification ID from the intent
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (notificationId != -1) {
            // Cancel the notification
            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.cancel(notificationId)
        }
        
        // Finish the activity
        finish()
    }
} 