package com.landofthefallen.pissbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.landofthefallen.pissbot.data.TestDatabase
import com.landofthefallen.pissbot.data.TestHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHistoryScreen(
    onNavigateToHome: () -> Unit,
    context: android.content.Context,
    refreshTrigger: Int = 0
) {
    var tests by remember { mutableStateOf<List<TestHistory>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Load tests whenever refreshTrigger changes
    LaunchedEffect(refreshTrigger) {
        scope.launch {
            val database = TestDatabase.getDatabase(context)
            tests = database.testHistoryDao().getAllTests()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (tests.isEmpty()) {
            Text(
                text = "No test history available",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(tests) { test ->
                    TestHistoryItem(test = test)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHistoryItem(test: TestHistory) {
    val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFD33E)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = dateFormat.format(test.timestamp),
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            test.isRequired?.let { isRequired ->
                Text(
                    text = "Response: ${if (isRequired) "Required" else "Not Required"}",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (test.isScheduled) "Scheduled" else "Manual",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notification: ${if (test.showedNotification) "Shown" else "Not Shown"}",
                color = Color.Black,
                style = MaterialTheme.typography.bodySmall
            )
            test.notificationFailReason?.let { reason ->
                Text(
                    text = "Reason: $reason",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
} 