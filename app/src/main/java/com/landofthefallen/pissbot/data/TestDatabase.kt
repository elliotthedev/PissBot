package com.landofthefallen.pissbot.data

import android.content.Context
import androidx.room.*
import java.util.Date
import kotlinx.coroutines.flow.Flow

@Dao
interface TestHistoryDao {
    @Query("SELECT * FROM test_history ORDER BY timestamp DESC")
    suspend fun getAllTests(): List<TestHistory>

    @Insert
    suspend fun insertTest(test: TestHistory)

    @Update
    suspend fun updateTest(test: TestHistory)

    @Query("DELETE FROM test_history")
    suspend fun deleteAllTests()
}

@Database(entities = [TestHistory::class], version = 2)
@TypeConverters(Converters::class)
abstract class TestDatabase : RoomDatabase() {
    abstract fun testHistoryDao(): TestHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: TestDatabase? = null

        fun getDatabase(context: Context): TestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TestDatabase::class.java,
                    "test_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 