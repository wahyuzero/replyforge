package com.wahyuzero.replyforge

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions, writes them to a crash log file, then
 * delegates to the previous handler (which normally kills the process).
 *
 * Usage: call [install] once in Application.onCreate().
 *
 * Crash logs are stored as individual files in `filesDir/crash_logs/`,
 * capped at MAX_LOG_FILES (oldest purged first). Viewable in About screen.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_DIR = "crash_logs"
    private const val MAX_LOG_FILES = 10
    private const val MAX_STACK_LINES = 50

    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun install(context: Context) {
        if (previousHandler != null) return // already installed
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(context.applicationContext, thread.name, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "CrashHandler installed")
    }

    private fun writeCrashLog(context: Context, threadName: String, throwable: Throwable) {
        try {
            val dir = File(context.filesDir, CRASH_DIR)
            if (!dir.exists()) dir.mkdirs()

            // Purge old logs
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size >= MAX_LOG_FILES) {
                files.take(files.size - MAX_LOG_FILES + 1).forEach { it.delete() }
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val logFile = File(dir, "crash_$timestamp.txt")

            val sw = StringWriter()
            val pw = PrintWriter(sw)

            pw.println("══════════════════════════════════════════")
            pw.println("ReplyForge Crash Report")
            pw.println("══════════════════════════════════════════")
            pw.println("Time: ${Date()}")
            pw.println("Thread: $threadName")
            pw.println()
            pw.println("App Version: ${getAppVersion(context)}")
            pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println()
            pw.println("Stack Trace:")
            pw.println("══════════════════════════════════════════")
            throwable.printStackTrace(pw)
            pw.println("══════════════════════════════════════════")
            pw.flush()
            pw.close()

            logFile.writeText(sw.toString())
        } catch (e: Exception) {
            // If crash logging fails, we must not throw — just log
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    fun getCrashLogs(context: Context): List<CrashLogEntry> {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val content = file.readText()
                val firstLine = content.lineSequence()
                    .dropWhile { !it.startsWith("Time:") }
                    .firstOrNull()
                    ?.removePrefix("Time: ")
                    ?.trim()
                    ?: file.name

                // Extract first meaningful exception line
                val exceptionLine = content.lineSequence()
                    .find { it.contains("Exception") || it.contains("Error") }
                    ?.trim()
                    ?: "Unknown error"

                CrashLogEntry(
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    timeString = firstLine,
                    summary = exceptionLine,
                    fullLog = content
                )
            }
            ?: emptyList()
    }

    fun deleteCrashLog(context: Context, fileName: String): Boolean {
        val file = File(context.filesDir, "$CRASH_DIR/$fileName")
        return file.delete()
    }

    fun clearAllCrashLogs(context: Context): Int {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) return 0
        return dir.listFiles()?.count { it.delete() } ?: 0
    }

    fun hasCrashLogs(context: Context): Boolean {
        val dir = File(context.filesDir, CRASH_DIR)
        return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

data class CrashLogEntry(
    val fileName: String,
    val timestamp: Long,
    val timeString: String,
    val summary: String,
    val fullLog: String
)
