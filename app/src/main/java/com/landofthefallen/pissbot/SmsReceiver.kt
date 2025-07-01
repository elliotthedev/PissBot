package com.landofthefallen.pissbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.landofthefallen.pissbot.data.TestDatabase
import com.landofthefallen.pissbot.data.TestHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.os.Build
import android.app.PendingIntent

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                try {
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bundle.getString("format", null)
                    } else {
                        @Suppress("DEPRECATION")
                        bundle.getString("format")
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    val pdus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bundle.getSerializable("pdus", Array<ByteArray>::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        (bundle.get("pdus") as Array<*>).map { it as ByteArray }.toTypedArray()
                    }
                    
                    if (pdus != null) {
                        for (pdu in pdus) {
                            val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                SmsMessage.createFromPdu(pdu, format)
                            } else {
                                @Suppress("DEPRECATION")
                                SmsMessage.createFromPdu(pdu)
                            }
                            
                            val sender = message.originatingAddress
                            val messageBody = message.messageBody

                            Log.d(TAG, "Received SMS from: $sender with body: $messageBody")

                            // Normalize the sender number by removing any non-digit characters
                            val normalizedSender = sender?.replace(Regex("[^0-9]"), "")
                            Log.d(TAG, "Normalized sender number: $normalizedSender")
                            
                            // Check both with and without the "1" prefix
                            if (normalizedSender == "7406219033" || normalizedSender == "17406219033") {
                                Log.d(TAG, "Sender number matched, calling processMessage")
                                processMessage(context, messageBody)
                                // Suppress the SMS from being delivered to other apps
                                abortBroadcast()
                                Log.d(TAG, "SMS broadcast aborted")
                            } else {
                                Log.d(TAG, "Sender number did not match. Expected: 7406219033 or 17406219033, Got: $normalizedSender")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS: ${e.message}", e)
                    e.printStackTrace()
                }
            }
        }
    }

    private fun processMessage(context: Context, messageBody: String) {
        Log.d(TAG, "Starting processMessage with body: $messageBody")
        
        val isRequired = when {
            messageBody.contains("NOT required to test", ignoreCase = true) -> false
            messageBody.contains("required to test", ignoreCase = true) -> true
            else -> null
        }

        Log.d(TAG, "Processing message: $messageBody, isRequired: $isRequired")

        try {
            // Create dismiss intent
            val dismissIntent = Intent(context, NotificationDismissActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_id", System.currentTimeMillis().toInt())
            }
            val dismissPendingIntent = PendingIntent.getActivity(
                context,
                0,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create notification
            val notificationBuilder = NotificationCompat.Builder(context, "test_notification_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Test Response")
                .setContentText(if (isRequired == true) "You are required to test" else "You are not required to test")
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // Always high priority for Android 13+
                .setAutoCancel(false)
                .setOngoing(true)
                .setColor(if (isRequired == true) android.graphics.Color.RED else android.graphics.Color.GREEN)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(if (isRequired == true) "You are required to test" else "You are not required to test")
                    .setBigContentTitle("Test Response"))
                .setFullScreenIntent(null, true)
                .setDeleteIntent(dismissPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(0)
                .setOnlyAlertOnce(true)
                .addAction(0, "Dismiss", dismissPendingIntent)

            Log.d(TAG, "Notification builder created")

            val notificationManager = NotificationManagerCompat.from(context)
            
            // Check if we have notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Notification permission check: $hasPermission")
                if (!hasPermission) {
                    Log.e(TAG, "Notification permission not granted")
                    throw SecurityException("Notification permission not granted")
                }
            }

            // Try to show notification
            try {
                val notificationId = System.currentTimeMillis().toInt()
                Log.d(TAG, "Attempting to show notification with ID: $notificationId")
                notificationManager.notify(notificationId, notificationBuilder.build())
                Log.d(TAG, "Notification sent successfully")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while showing notification: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "General exception while showing notification: ${e.message}", e)
                throw e
            }

            // Update database with success
            CoroutineScope(Dispatchers.IO).launch {
                val database = TestDatabase.getDatabase(context)
                val allTests = database.testHistoryDao().getAllTests()
                
                // First look for pending scheduled tests (most recent first)
                val scheduledTests = allTests.filter { 
                    it.isScheduled && it.response == null 
                }.sortedByDescending { it.timestamp }
                
                if (scheduledTests.isNotEmpty()) {
                    // Update the most recent scheduled test that's still pending
                    val testToUpdate = scheduledTests.first()
                    database.testHistoryDao().updateTest(
                        testToUpdate.copy(
                            response = messageBody,
                            isRequired = isRequired,
                            showedNotification = true
                        )
                    )
                    Log.d(TAG, "Updated existing scheduled test ID: ${testToUpdate.id} with response")
                } else {
                    // If no scheduled test found, create a new manual test entry
                    val newTest = TestHistory(
                        timestamp = Date(),
                        message = "TEST",
                        response = messageBody,
                        isRequired = isRequired,
                        isScheduled = false,
                        showedNotification = true
                    )
                    database.testHistoryDao().insertTest(newTest)
                    Log.d(TAG, "No pending scheduled test found, created new test entry")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processMessage: ${e.message}", e)
            // If notification fails, update the database with the failure
            CoroutineScope(Dispatchers.IO).launch {
                val database = TestDatabase.getDatabase(context)
                val allTests = database.testHistoryDao().getAllTests()
                
                // First look for pending scheduled tests (most recent first)
                val scheduledTests = allTests.filter { 
                    it.isScheduled && it.response == null 
                }.sortedByDescending { it.timestamp }
                
                if (scheduledTests.isNotEmpty()) {
                    // Update the most recent scheduled test that's still pending
                    val testToUpdate = scheduledTests.first()
                    database.testHistoryDao().updateTest(
                        testToUpdate.copy(
                            response = messageBody,
                            isRequired = isRequired,
                            showedNotification = false,
                            notificationFailReason = e.message ?: "Unknown error showing notification"
                        )
                    )
                    Log.d(TAG, "Updated existing scheduled test ID: ${testToUpdate.id} with response (notification failed)")
                } else {
                    // If no scheduled test found, create a new test entry
                    val newTest = TestHistory(
                        timestamp = Date(),
                        message = "TEST",
                        response = messageBody,
                        isRequired = isRequired,
                        isScheduled = false,
                        showedNotification = false,
                        notificationFailReason = e.message ?: "Unknown error showing notification"
                    )
                    database.testHistoryDao().insertTest(newTest)
                    Log.d(TAG, "No pending scheduled test found, created new test entry (notification failed)")
                }
            }
        }
    }
} 