package com.landofthefallen.pissbot.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "test_history")
data class TestHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Date,
    val message: String,
    val response: String?,
    val isRequired: Boolean?,
    val isScheduled: Boolean,
    val showedNotification: Boolean = false,
    val notificationFailReason: String? = null
) 