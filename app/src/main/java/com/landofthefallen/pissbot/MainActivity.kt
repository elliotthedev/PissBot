package com.landofthefallen.pissbot

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.landofthefallen.pissbot.data.TestDatabase
import com.landofthefallen.pissbot.data.TestHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        const val TEST_ALARM_REQUEST_CODE = 123

        suspend fun checkAndHandleMissedTests(context: Context) {
            try {
                val database = TestDatabase.getDatabase(context)
                val allTests = database.testHistoryDao().getAllTests()
                
                // Get today's date at midnight for comparison
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                
                // Find any scheduled tests from today that haven't been completed
                val missedTests = allTests.filter { test ->
                    test.isScheduled && 
                    test.response == null && 
                    test.timestamp.after(today)
                }
                
                if (missedTests.isNotEmpty()) {
                    Log.d(TAG, "Found ${missedTests.size} missed tests, sending test directly")
                    
                    // Get the most recent missed test
                    val mostRecentMissedTest = missedTests.maxByOrNull { it.timestamp }
                    
                    // Instead of scheduling an alarm, send the test directly
                    val phoneNumber = "7406219033"
                    val testMessage = "TEST"
                    
                    // Check SMS permission first
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "SMS permission not granted - cannot send test after reboot")
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
                            phoneNumber,
                            null,
                            testMessage,
                            null,
                            null
                        )
                        
                        Log.d(TAG, "Test sent directly after reboot for scheduled test from ${mostRecentMissedTest?.timestamp}")
                        
                        // Update the existing test record instead of creating a new one
                        if (mostRecentMissedTest != null) {
                            database.testHistoryDao().updateTest(
                                mostRecentMissedTest.copy(
                                    // Keep the original timestamp but mark as executed
                                    notificationFailReason = null
                                )
                            )
                            Log.d(TAG, "Updated existing scheduled test ID: ${mostRecentMissedTest.id}")
                        } else {
                            // Fallback: create a new test if somehow we couldn't find the original
                            val test = TestHistory(
                                timestamp = Date(),
                                message = testMessage,
                                response = null,
                                isRequired = null,
                                isScheduled = true,
                                showedNotification = false
                            )
                            database.testHistoryDao().insertTest(test)
                            Log.d(TAG, "Created new test record as fallback")
                        }
                        
                        // Show toast on main thread
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Scheduled test sent after reboot", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send test after reboot: ${e.message}", e)
                        
                        // Update the test record with the failure reason
                        if (mostRecentMissedTest != null) {
                            database.testHistoryDao().updateTest(
                                mostRecentMissedTest.copy(
                                    notificationFailReason = e.message ?: "Unknown error"
                                )
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "No missed tests found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for missed tests: ${e.message}", e)
            }
        }
    }

    private val phoneNumber = "7406219033"
    private val testMessage = "TEST"
    private val channelId = "test_notification_channel"
    private lateinit var database: TestDatabase
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for SMS functionality", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TestDatabase.getDatabase(this)
        createNotificationChannel()
        
        // Handle widget button click
        if (intent?.action == "com.landofthefallen.pissbot.SEND_TEST") {
            sendTestSMS()
        }
        
        setContent {
            MaterialTheme {
                var showHistory by remember { mutableStateOf(false) }
                var showDebug by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }
                var refreshTrigger by remember { mutableStateOf(0) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Test History", color = Color.Black) },
                        text = { Text("Are you sure you want to delete all test history?", color = Color.Black) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val database = TestDatabase.getDatabase(context)
                                        database.testHistoryDao().deleteAllTests()
                                        refreshTrigger++
                                        showDeleteDialog = false
                                    }
                                }
                            ) {
                                Text("Delete", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel", color = Color.Black)
                            }
                        },
                        containerColor = Color(0xFFFFD33E)
                    )
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.fillMaxHeight(),
                            drawerContainerColor = Color(0xFFFFD33E)
                        ) {
                            Text(
                                "Menu",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.Black
                            )
                            HorizontalDivider(color = Color.Black)
                            NavigationDrawerItem(
                                label = { Text("Home", color = Color(0xFFFFD33E)) },
                                selected = !showHistory && !showDebug,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        showHistory = false
                                        showDebug = false
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color.Black,
                                    unselectedContainerColor = Color.Black
                                )
                            )
                            NavigationDrawerItem(
                                label = { Text("Test History", color = Color(0xFFFFD33E)) },
                                selected = showHistory,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        showHistory = true
                                        showDebug = false
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color.Black,
                                    unselectedContainerColor = Color.Black
                                )
                            )
                            NavigationDrawerItem(
                                label = { Text("Debug", color = Color(0xFFFFD33E)) },
                                selected = showDebug,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        showHistory = false
                                        showDebug = true
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color.Black,
                                    unselectedContainerColor = Color.Black
                                )
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(if (showDebug) "Debug" else if (showHistory) "Test History" else "PissBot", color = Color.Black) },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Menu",
                                            tint = Color.Black
                                        )
                                    }
                                },
                                actions = {
                                    if (showHistory) {
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete History",
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    titleContentColor = Color.Black,
                                    containerColor = Color(0xFFFFD33E)
                                )
                            )
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(paddingValues)
                        ) {
                            when {
                                showDebug -> DebugScreen(
                                    onNavigateToHome = { showDebug = false },
                                    context = context
                                )
                                showHistory -> TestHistoryScreen(
                                    onNavigateToHome = { showHistory = false },
                                    context = context,
                                    refreshTrigger = refreshTrigger
                                )
                                else -> MainScreen(
                                    onSendTest = { sendTestSMS() },
                                    onScheduleTest = { showTimePickerDialog() }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        checkAndRequestPermissions()
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                scheduleDailyTest(hourOfDay, minute)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Test Notifications"
            val descriptionText = "Notifications for test responses"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("MainActivity", "Notification channel created successfully")
        }
    }

    private fun sendTestSMS() {
        if (checkPermissions()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    testMessage,
                    null,
                    null
                )

                // Record the test in the database
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                scope.launch {
                    val test = TestHistory(
                        timestamp = Date(),
                        message = testMessage,
                        response = null,
                        isRequired = null,
                        isScheduled = false,
                        showedNotification = false  // No notification shown yet
                    )
                    database.testHistoryDao().insertTest(test)
                }

                Toast.makeText(this, "Test message sent", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Record failed attempt
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                scope.launch {
                    val test = TestHistory(
                        timestamp = Date(),
                        message = testMessage,
                        response = null,
                        isRequired = null,
                        isScheduled = false,
                        showedNotification = false,
                        notificationFailReason = e.message ?: "Failed to send test message"
                    )
                    database.testHistoryDao().insertTest(test)
                }
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            requestPermissions()
        }
    }

    private fun scheduleDailyTest(hour: Int, minute: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TestAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check exact alarm permission for Android 12+
                if (alarmManager.canScheduleExactAlarms()) {
                    // Set exact alarm for Android 12+
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d("MainActivity", "Scheduled exact alarm for ${calendar.time}")
                } else {
                    // No exact alarm permission, use inexact repeating
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                    Log.d("MainActivity", "Scheduled inexact repeating alarm for ${calendar.time}")
                }
            } else {
                // For older Android versions, use setExactAndAllowWhileIdle
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Log.d("MainActivity", "Scheduled exact alarm for ${calendar.time}")
            }

            // Create a database entry for the scheduled test
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                val test = TestHistory(
                    timestamp = calendar.time,
                    message = "TEST",
                    response = null,
                    isRequired = null,
                    isScheduled = true,
                    showedNotification = false
                )
                database.testHistoryDao().insertTest(test)
            }

            Toast.makeText(this, "Daily test scheduled for $hour:$minute", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to schedule alarm: ${e.message}", e)
            Toast.makeText(this, "Failed to schedule test: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.SEND_SMS)
        }

        ActivityCompat.requestPermissions(this, permissions, 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSendTest: () -> Unit,
    onScheduleTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onSendTest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD33E),
                contentColor = Color.Black
            )
        ) {
            Text("Send Test Now")
        }

        Button(
            onClick = onScheduleTest,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFD33E),
                contentColor = Color.Black
            )
        ) {
            Text("Schedule Daily Test")
        }
    }
}