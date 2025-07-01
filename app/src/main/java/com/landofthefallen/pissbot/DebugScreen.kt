package com.landofthefallen.pissbot

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.landofthefallen.pissbot.data.TestDatabase
import com.landofthefallen.pissbot.data.TestHistory
import kotlinx.coroutines.launch
import java.util.*
import android.app.NotificationManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateToHome: () -> Unit,
    context: Context
) {
    var scheduledTests by remember { mutableStateOf<List<TestHistory>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // Load scheduled tests when the screen is first displayed
    LaunchedEffect(Unit) {
        scope.launch {
            val database = TestDatabase.getDatabase(context)
            scheduledTests = database.testHistoryDao().getAllTests()
                .filter { it.isScheduled && it.response == null }
                .sortedByDescending { it.timestamp }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Debug Controls",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )

            // Test Simulation Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFD33E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Test Simulation",
                        color = Color.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { simulateResponse(context, true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color(0xFFFFD33E)
                        )
                    ) {
                        Text("Simulate Required Test Response")
                    }
                    Button(
                        onClick = { simulateResponse(context, false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color(0xFFFFD33E)
                        )
                    ) {
                        Text("Simulate Not Required Test Response")
                    }
                }
            }

            // Scheduled Tests Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFD33E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Scheduled Tests",
                        color = Color.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (scheduledTests.isEmpty()) {
                        Text(
                            "No scheduled tests found",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(min = 10.dp, max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(scheduledTests) { test ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Black
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Scheduled: ${dateFormat.format(test.timestamp)}",
                                            color = Color(0xFFFFD33E)
                                        )
                                        Text(
                                            "Status: ${if (test.response != null) "Completed" else "Pending"}",
                                            color = Color(0xFFFFD33E)
                                        )
                                        if (test.response != null) {
                                            Text(
                                                "Response: ${test.response}",
                                                color = Color(0xFFFFD33E)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val database = TestDatabase.getDatabase(context)
                                scheduledTests = database.testHistoryDao().getAllTests()
                                    .filter { it.isScheduled && it.response == null }
                                    .sortedByDescending { it.timestamp }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color(0xFFFFD33E)
                        )
                    ) {
                        Text("Refresh Scheduled Tests")
                    }
                }
            }
        }
    }
}

private fun simulateResponse(context: Context, isRequired: Boolean) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    scope.launch {
        val database = TestDatabase.getDatabase(context)
        
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
                .setContentText(if (isRequired) "You are required to test" else "You are not required to test")
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // Always high priority for Android 13+
                .setAutoCancel(false)
                .setOngoing(true)
                .setColor(if (isRequired) android.graphics.Color.RED else android.graphics.Color.GREEN)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(if (isRequired) "You are required to test" else "You are not required to test")
                    .setBigContentTitle("Test Response"))
                .setFullScreenIntent(null, true)
                .setDeleteIntent(dismissPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(0)
                .setOnlyAlertOnce(true)
                .addAction(0, "Dismiss", dismissPendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            
            // Check if we have notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    throw SecurityException("Notification permission not granted")
                }
            }

            // Show notification
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())

            // Create a new test entry for the simulation
            val newTest = TestHistory(
                timestamp = Date(),
                message = "TEST",
                isScheduled = false,
                response = if (isRequired) "REQUIRED" else "NOT_REQUIRED",
                isRequired = isRequired,
                showedNotification = true
            )
            
            database.testHistoryDao().insertTest(newTest)
        } catch (e: Exception) {
            // If notification fails, record the failure reason
            val newTest = TestHistory(
                timestamp = Date(),
                message = "TEST",
                isScheduled = false,
                response = if (isRequired) "REQUIRED" else "NOT_REQUIRED",
                isRequired = isRequired,
                showedNotification = false,
                notificationFailReason = e.message ?: "Unknown error showing notification"
            )
            
            database.testHistoryDao().insertTest(newTest)
        }
    }
} 