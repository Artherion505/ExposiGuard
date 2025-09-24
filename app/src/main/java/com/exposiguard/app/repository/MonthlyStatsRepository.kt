package com.exposiguard.app.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DailyTotal(
    val date: String, // yyyy-MM-dd
    val sarLike: Double, // W/kg
    val wifiNorm: Double,
    val sar: Double,
    val bt: Double
)

class MonthlyStatsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("monthly_stats", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun monthKey(cal: Calendar = Calendar.getInstance()): String {
        val fmt = SimpleDateFormat("yyyyMM", Locale.US)
        return "totals_" + fmt.format(cal.time)
    }

    private fun dayKey(cal: Calendar): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(cal.time)
    }

    fun saveDailyTotal(total: DailyTotal, cal: Calendar = Calendar.getInstance()) {
        val key = monthKey(cal)
        val map = getMonthMap(cal).toMutableMap()
        map[total.date] = total
        val json = gson.toJson(map)
        prefs.edit().putString(key, json).apply()
    }

    fun getMonthTotals(cal: Calendar = Calendar.getInstance()): List<DailyTotal> {
        return getMonthMap(cal).values.sortedBy { it.date }
    }

    private fun getMonthMap(cal: Calendar): Map<String, DailyTotal> {
        val key = monthKey(cal)
        val json = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, DailyTotal>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyMap() }
    }

    fun todayKey(): String {
        return dayKey(Calendar.getInstance())
    }
}
