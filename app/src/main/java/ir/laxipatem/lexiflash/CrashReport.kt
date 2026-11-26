package ir.laxipatem.lexiflash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** Writes the most recent uncaught crash to private app storage for user sharing. */
object CrashReport {
    const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = StringWriter().also { writer ->
                    PrintWriter(writer).use { throwable.printStackTrace(it) }
                }.toString()
                val report = buildString {
                    appendLine("LexiFlash uncaught crash report")
                    appendLine("time: ${Date()}")
                    appendLine("thread: ${thread.name} (id=${thread.id})")
                    appendLine("manufacturer: ${Build.MANUFACTURER}")
                    appendLine("model: ${Build.MODEL}")
                    appendLine("sdk: ${Build.VERSION.SDK_INT}")
                    appendLine("abis: ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("flavor: ${BuildConfig.FLAVOR}")
                    appendLine("versionName: ${BuildConfig.VERSION_NAME}")
                    appendLine()
                    append(stackTrace)
                }
                File(appContext.filesDir, FILE_NAME).writeText(report, Charsets.UTF_8)
            } catch (_: Throwable) {
                // Crash reporting must never replace the original uncaught failure.
            }

            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {
                // Do not throw from the crash handler itself.
            }
        }
    }

    fun read(context: Context): String? = try {
        val appContext = context.applicationContext
        val crashFile = File(appContext.filesDir, FILE_NAME)
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
        val exits = historicalProcessExits(appContext)
        if (crashFile.isNullOrBlank() && exits.isEmpty()) {
            null
        } else {
            buildString {
                if (!crashFile.isNullOrBlank()) append(crashFile)
                if (exits.isNotEmpty()) {
                    appendLine()
                    appendLine("Historical process exits (up to five most recent records)")
                    exits.forEachIndexed { index, exit ->
                        appendLine("--- exit ${index + 1} ---")
                        appendLine("reason: ${exit.reason}")
                        appendLine("description: ${exit.description.orEmpty()}")
                        appendLine("status: ${exit.status}")
                        appendLine("importance: ${exit.importance}")
                        appendLine("pss: ${exit.pss}")
                        appendLine("rss: ${exit.rss}")
                        appendLine("timestamp: ${exit.timestamp}")
                    }
                }
            }
        }
    } catch (_: Throwable) {
        null
    }

    /** API 30+ can report exits the Java uncaught handler never sees. */
    private fun historicalProcessExits(context: Context): List<ApplicationExitInfo> {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                emptyList()
            } else {
                context.getSystemService(ActivityManager::class.java)
                    ?.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                    .orEmpty()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
