package com.dosecerta.ui

import android.content.Context
import com.dosecerta.R
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Uses the app's language, including when it differs from the device language. */
object UiDateTime {
    fun date(context: Context, timestamp: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0]).format(Date(timestamp))

    fun time(context: Context, timestamp: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))

    fun dateLabel(context: Context, timestamp: Long): String {
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (day) {
            today -> context.getString(R.string.design_today_doses)
            today.plusDays(1) -> context.getString(R.string.design_tomorrow)
            else -> date(context, timestamp)
        }
    }

    fun upcoming(context: Context, timestamp: Long): String =
        context.getString(R.string.design_date_time, dateLabel(context, timestamp), time(context, timestamp))
}
