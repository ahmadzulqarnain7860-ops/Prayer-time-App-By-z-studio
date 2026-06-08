package com.example.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.*

class PrayerTimesCalculator {
    // Muscat Coordinates
    private val latitude = 23.5859
    private val longitude = 58.4059
    private val timezone = 4.0 // Oman is UTC+4

    data class PrayerTimes(
        val fajr: LocalTime,
        val sunrise: LocalTime,
        val dhuhr: LocalTime,
        val asr: LocalTime,
        val maghrib: LocalTime,
        val isha: LocalTime
    )

    fun calculateTimesForDate(date: LocalDate): PrayerTimes {
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth
        
        // Solar transit for noon on target day
        val jdNoon = julianDate(y, m, d) + 0.5 - (longitude / 360.0)
        val (dec, eot) = sunPosition(jdNoon)
        
        // Solar noon local time in hours (0.0 to 24.0)
        val noonLocal = 12.0 + (timezone - longitude / 15.0) - (eot / 60.0)
        
        // Hour angles in degrees
        val hFajr = hourAngle(latitude, dec, -18.0)
        val hSunrise = hourAngle(latitude, dec, -0.833)
        val hSunset = hourAngle(latitude, dec, -0.833)
        val hIsha = hourAngle(latitude, dec, -18.0)
        
        // Asr altitude calculation (Standard Shafi/Imam Maliki method – shadow factor = 1)
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(dec)
        val asrAltRad = atan(1.0 / (1.0 + tan(abs(latRad - decRad))))
        val asrAltDeg = Math.toDegrees(asrAltRad)
        val hAsr = hourAngle(latitude, dec, asrAltDeg)
        
        // Compute hours
        val fajrHour = noonLocal - (hFajr / 15.0)
        val sunriseHour = noonLocal - (hSunrise / 15.0)
        val dhuhrHour = noonLocal // solar transit is direct dhuhr
        val asrHour = noonLocal + (hAsr / 15.0)
        val maghribHour = noonLocal + (hSunset / 15.0)
        val ishaHour = noonLocal + (hIsha / 15.0)
        
        return PrayerTimes(
            fajr = doubleToLocalTime(fajrHour),
            sunrise = doubleToLocalTime(sunriseHour),
            dhuhr = doubleToLocalTime(dhuhrHour),
            asr = doubleToLocalTime(asrHour),
            maghrib = doubleToLocalTime(maghribHour),
            isha = doubleToLocalTime(ishaHour)
        )
    }
    
    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        val e = floor(365.25 * (y + 4716))
        val f = floor(30.6001 * (m + 1))
        return b + day.toDouble() + e + f - 1524.5
    }
    
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        
        // Solar Mean Longitude
        var l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        l0 = fixAngle(l0)
        
        // Solar Mean Anomaly
        var m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        m = fixAngle(m)
        
        // Mean Obliquity of Ecliptic
        val ob = 23.4392911 - (46.8150 / 3600.0) * t - (0.00059 / 3600.0) * t * t + (0.001813 / 3600.0) * t * t * t
        
        // Equation of Center
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(Math.toRadians(m)) +
                (0.019993 - 0.000101 * t) * sin(Math.toRadians(2 * m)) +
                0.000289 * sin(Math.toRadians(3 * m))
                
        val trueL = l0 + c
        
        val omega = 125.04 - 1934.136 * t
        val appL = trueL - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        
        val obCorr = ob + 0.00256 * cos(Math.toRadians(omega))
        
        // Right Ascension
        var ra = Math.toDegrees(atan2(cos(Math.toRadians(obCorr)) * sin(Math.toRadians(appL)), cos(Math.toRadians(appL))))
        ra = fixAngle(ra)
        
        val lQuadrant = floor(appL / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        
        // Solar Declination
        val dec = Math.toDegrees(asin(sin(Math.toRadians(obCorr)) * sin(Math.toRadians(appL))))
        
        // Equation of Time in standard minutes
        val eot = (l0 - ra) * 4.0
        
        return Pair(dec, eot)
    }
    
    private fun hourAngle(lat: Double, dec: Double, alt: Double): Double {
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val altRad = Math.toRadians(alt)
        val cosH = (sin(altRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosH < -1.0) return 180.0 // Constant day
        if (cosH > 1.0) return 0.0 // Constant night
        return Math.toDegrees(acos(cosH))
    }
    
    private fun fixAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }
    
    private fun doubleToLocalTime(hoursValue: Double): LocalTime {
        var hrs = hoursValue
        if (hrs.isNaN() || hrs.isInfinite()) {
            return LocalTime.of(12, 0)
        }
        // bound inside [0, 24)
        hrs = (hrs % 24.0 + 24.0) % 24.0
        val totalMinutes = floor(hrs * 60.0).toInt()
        val m = totalMinutes % 60
        val h = (totalMinutes / 60) % 24
        return LocalTime.of(h, m)
    }
}
