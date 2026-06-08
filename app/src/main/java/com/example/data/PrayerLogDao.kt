package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerLogDao {
    @Query("SELECT * FROM prayer_logs WHERE date = :date")
    fun getLogForDate(date: String): Flow<PrayerLog?>

    @Query("SELECT * FROM prayer_logs WHERE date = :date")
    suspend fun getLogForDateOnce(date: String): PrayerLog?

    @Query("SELECT * FROM prayer_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<PrayerLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLog(log: PrayerLog)
}
