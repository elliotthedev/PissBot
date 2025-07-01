package com.landofthefallen.pissbot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.landofthefallen.pissbot.data.TestDatabase
import com.landofthefallen.pissbot.data.TestHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class TestAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TestAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm triggered - attempting to send scheduled test")
        
        // Check SMS permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission not granted - cannot send scheduled test")
            recordFailedTest(context, "SMS permission not granted")
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        try {
            smsManager.sendTextMessage(
                "7406219033",
                null,
                "TEST",
                null,
                null
            )

            Log.d(TAG, "Scheduled test SMS sent successfully")
            
            // First check if there's a pending scheduled test that we can update
            CoroutineScope(Dispatchers.IO).launch {
                val database = TestDatabase.getDatabase(context)
                val allTests = database.testHistoryDao().getAllTests()
                
                // Look for pending scheduled tests from today (most recent first)
                val today = Date()
                val scheduledTests = allTests
                    .filter { 
                        it.isScheduled && 
                        it.response == null && 
                        isSameDay(it.timestamp, today)
                    }
                    .sortedByDescending { it.timestamp }
                
                if (scheduledTests.isNotEmpty()) {
                    // Update the most recent scheduled test from today
                    val testToUpdate = scheduledTests.first()
                    Log.d(TAG, "Found pending scheduled test from today (ID: ${testToUpdate.id}), updating")
                    // We mark it as executed but response will come later
                    database.testHistoryDao().updateTest(
                        testToUpdate.copy(
                            // Message sent successfully, waiting for response
                            notificationFailReason = null
                        )
                    )
                } else {
                    // If no scheduled test found for today, create a new one
                    Log.d(TAG, "No pending scheduled test found for today, creating new record")
                    val test = TestHistory(
                        timestamp = Date(),
                        message = "TEST",
                        response = null,
                        isRequired = null,
                        isScheduled = true,
                        showedNotification = false
                    )
                    database.testHistoryDao().insertTest(test)
                }
                Log.d(TAG, "Scheduled test recorded in database")
            }

            Toast.makeText(context, "Scheduled test message sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send scheduled test: ${e.message}", e)
            recordFailedTest(context, e.message ?: "Unknown error")
            Toast.makeText(context, "Failed to send scheduled test message: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun recordFailedTest(context: Context, errorMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = TestDatabase.getDatabase(context)
                val allTests = database.testHistoryDao().getAllTests()
                
                // Look for pending scheduled tests from today
                val today = Date()
                val scheduledTests = allTests
                    .filter { 
                        it.isScheduled && 
                        it.response == null && 
                        isSameDay(it.timestamp, today)
                    }
                    .sortedByDescending { it.timestamp }
                
                if (scheduledTests.isNotEmpty()) {
                    // Update the most recent scheduled test from today with failure
                    val testToUpdate = scheduledTests.first()
                    database.testHistoryDao().updateTest(
                        testToUpdate.copy(
                            showedNotification = false,
                            notificationFailReason = errorMessage
                        )
                    )
                    Log.d(TAG, "Updated existing scheduled test with failure: $errorMessage")
                } else {
                    // If no scheduled test found, create a new entry
                    val test = TestHistory(
                        timestamp = Date(),
                        message = "TEST",
                        response = null,
                        isRequired = null,
                        isScheduled = true,
                        showedNotification = false,
                        notificationFailReason = errorMessage
                    )
                    database.testHistoryDao().insertTest(test)
                    Log.d(TAG, "Created new failed test record: $errorMessage")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording failed test: ${e.message}", e)
            }
        }
    }
    
    // Helper function to determine if two dates are on the same day
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
        val cal2 = java.util.Calendar.getInstance().apply { time = date2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
} 