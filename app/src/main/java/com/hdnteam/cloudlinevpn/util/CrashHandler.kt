package com.hdnteam.cloudlinevpn.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves crash logs to file for easy debugging without ADB.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null

    fun install(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        try {
            val sw = StringWriter()
            ex.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()
            Log.e(TAG, "CRASH on thread ${thread.name}:\n$trace")

            // Save to file
            context?.let { ctx ->
                val file = File(ctx.filesDir, "last_crash.txt")
                file.writeText("Thread: ${thread.name}\n$trace")
            }
        } catch (_: Exception) {}

        defaultHandler?.uncaughtException(thread, ex)
    }

    fun getLastCrash(ctx: Context): String? {
        val file = File(ctx.filesDir, "last_crash.txt")
        return if (file.exists()) file.readText() else null
    }
}
