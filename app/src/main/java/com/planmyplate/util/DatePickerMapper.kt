package com.planmyplate.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object DatePickerMapper {
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun localCalendarToPickerUtcMidnightMillis(localCalendar: Calendar): Long {
        return Calendar.getInstance(utcTimeZone).apply {
            set(Calendar.YEAR, localCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, localCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, localCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun pickerUtcMillisToLocalCalendar(utcMillis: Long): Calendar {
        val utcCalendar = Calendar.getInstance(utcTimeZone).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun pickerUtcMillisToDateKey(utcMillis: Long): String {
        val utcCalendar = Calendar.getInstance(utcTimeZone).apply {
            timeInMillis = utcMillis
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH) + 1,
            utcCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun localMillisToDateKey(localMillis: Long): String {
        val localCalendar = Calendar.getInstance().apply {
            timeInMillis = localMillis
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            localCalendar.get(Calendar.YEAR),
            localCalendar.get(Calendar.MONTH) + 1,
            localCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun dateKeyToPickerUtcMidnightMillis(dateKey: String): Long {
        val parts = dateKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 1970
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        return Calendar.getInstance(utcTimeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun dateKeyToDisplayLabel(dateKey: String): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
        return if (parsed != null) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(parsed)
        } else {
            dateKey
        }
    }
}