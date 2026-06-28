package com.hdnteam.cloudlinevpn.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * In-app logger — captures logs and shows them in the UI.
 */
object AppLogger {

    private const val MAX_LINES = 200
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        add(LogLevel.INFO, tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
        val full = if (throwable != null) "$msg\n${throwable.javaClass.simpleName}: ${throwable.message}" else msg
        add(LogLevel.ERROR, tag, full)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        add(LogLevel.WARN, tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        add(LogLevel.DEBUG, tag, msg)
    }

    private fun add(level: LogLevel, tag: String, msg: String) {
        val entry = LogEntry(
            time  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            level = level,
            tag   = tag,
            msg   = msg
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)   // newest first
        if (current.size > MAX_LINES) current.removeAt(current.size - 1)
        _logs.value = current
    }

    fun clear() { _logs.value = emptyList() }

    fun getAll(): String = _logs.value.joinToString("\n") {
        "[${it.time}] ${it.level.label}/${it.tag}: ${it.msg}"
    }
}

data class LogEntry(
    val time: String,
    val level: LogLevel,
    val tag: String,
    val msg: String
)

enum class LogLevel(val label: String) {
    DEBUG("D"), INFO("I"), WARN("W"), ERROR("E")
}
