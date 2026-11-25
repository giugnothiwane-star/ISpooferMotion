package com.chessoverlay.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de diagnostic local (jamais envoyé nulle part) : chaque erreur de capture, de reconnaissance
 * ou du moteur y est consignée avec son étape et sa cause technique. L'utilisateur peut le copier depuis
 * les réglages pour le transmettre lors d'un signalement de problème.
 */
object DiagnosticLog {
    private const val MAX_BYTES = 64 * 1024
    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostic.log")
    }

    /** Consigne une erreur et renvoie un résumé technique court (classe + message de la cause première). */
    fun record(stage: String, error: Throwable): String {
        var root: Throwable = error
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val summary = "${root.javaClass.simpleName}: ${root.message ?: "(sans message)"}"
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        append("[$stage] $summary\n${sw.toString().lines().take(14).joinToString("\n")}")
        return summary
    }

    fun note(stage: String, message: String) = append("[$stage] $message")

    @Synchronized
    private fun append(text: String) {
        val f = file ?: return
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
            f.appendText("$stamp $text\n")
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText(keep)
            }
        }
    }

    fun read(): String {
        val header = "Chess Overlay AI — ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n"
        return header + (file?.takeIf { it.exists() }?.readText()?.ifBlank { null } ?: "Aucune erreur enregistrée.")
    }

    fun clear() { runCatching { file?.delete() } }
}
