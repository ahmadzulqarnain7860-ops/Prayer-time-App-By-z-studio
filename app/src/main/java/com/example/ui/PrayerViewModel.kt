package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = PrayerRepository(application, database.prayerLogDao())

    // Selected date state
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Reactive prayer compilation log for the current selected date
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLog: StateFlow<PrayerLog?> = _selectedDate
        .flatMapLatest { date -> repository.getPrayerLogForDate(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Current local Muscat time ticking State
    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime.asStateFlow()

    // Countdown details state
    data class NextPrayerInfo(val name: String, val countdown: String)
    private val _nextPrayerInfo = MutableStateFlow<NextPrayerInfo?>(null)
    val nextPrayerInfo: StateFlow<NextPrayerInfo?> = _nextPrayerInfo.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Schedule custom notifications on starting the application
        repository.refreshAlarms()
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _currentTime.value = LocalTime.now()
                updateCountdown()
                delay(1000)
            }
        }
    }

    private fun updateCountdown() {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val todayTimes = repository.getPrayerTimesForDate(today)

        val prayersTodayList = listOf(
            Pair("Fajr", todayTimes.fajr),
            Pair("Dhuhr", todayTimes.dhuhr),
            Pair("Asr", todayTimes.asr),
            Pair("Maghrib", todayTimes.maghrib),
            Pair("Isha", todayTimes.isha)
        )

        var targetLocalDateTime: LocalDateTime? = null
        var nextPrayerName = ""

        for (p in prayersTodayList) {
            val pDateTime = LocalDateTime.of(today, p.second)
            if (pDateTime.isAfter(now)) {
                targetLocalDateTime = pDateTime
                nextPrayerName = p.first
                break
            }
        }

        // Fallback to tomorrow's Fajr if everything today is already passed
        if (targetLocalDateTime == null) {
            val tomorrow = today.plusDays(1)
            val tomorrowTimes = repository.getPrayerTimesForDate(tomorrow)
            targetLocalDateTime = LocalDateTime.of(tomorrow, tomorrowTimes.fajr)
            nextPrayerName = "Fajr (Tomorrow)"
        }

        val duration = Duration.between(now, targetLocalDateTime)
        val hrs = duration.toHours()
        val mins = (duration.toMinutes() % 60)
        val secs = (duration.toSeconds() % 60)

        _nextPrayerInfo.value = NextPrayerInfo(
            name = nextPrayerName,
            countdown = String.format("%02d : %02d : %02d", hrs, mins, secs)
        )
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun togglePrayerCompletion(type: PrayerType) {
        viewModelScope.launch {
            val dateStr = _selectedDate.value.toString()
            val existingLog = repository.getPrayerLogForDate(_selectedDate.value).first() ?: PrayerLog(date = dateStr)
            
            val updatedLog = when (type) {
                PrayerType.FAJR -> existingLog.copy(fajrCompleted = !existingLog.fajrCompleted)
                PrayerType.DHUHR -> existingLog.copy(dhuhrCompleted = !existingLog.dhuhrCompleted)
                PrayerType.ASR -> existingLog.copy(asrCompleted = !existingLog.asrCompleted)
                PrayerType.MAGHRIB -> existingLog.copy(maghribCompleted = !existingLog.maghribCompleted)
                PrayerType.ISHA -> existingLog.copy(ishaCompleted = !existingLog.ishaCompleted)
                else -> existingLog
            }
            repository.savePrayerLog(updatedLog)
        }
    }

    fun isNotificationEnabled(type: PrayerType): Boolean {
        return repository.isNotificationEnabled(type)
    }

    fun toggleNotification(type: PrayerType, enabled: Boolean) {
        repository.setNotificationEnabled(type, enabled)
    }

    fun getNotificationTone(type: PrayerType): NotificationTone {
        return repository.getNotificationTone(type)
    }

    fun setNotificationTone(type: PrayerType, tone: NotificationTone) {
        repository.setNotificationTone(type, tone)
    }

    fun triggerImmediateTestNotification(type: PrayerType) {
        repository.triggerImmediateTestNotification(type)
    }

    // Selected visual theme state
    private val _selectedTheme = MutableStateFlow<AppTheme>(repository.getSelectedTheme())
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    fun setSelectedTheme(theme: AppTheme) {
        _selectedTheme.value = theme
        repository.setSelectedTheme(theme)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
