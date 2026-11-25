package com.chessoverlay.app.engine

import android.content.Context
import com.chessoverlay.data.AppSettings
import com.chessoverlay.engine.EngineOptions
import com.chessoverlay.engine.StockfishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fournit les instances de Stockfish exécutées localement sur le téléphone.
 *
 * Le binaire est livré dans l'APK sous le nom libstockfish.so : Android l'extrait dans le dossier
 * des bibliothèques natives de l'application, seul emplacement où l'exécution d'un programme est
 * autorisée depuis Android 10.
 */
class EngineProvider(context: Context, settings: StateFlow<AppSettings>, scope: CoroutineScope) {

    val binary = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
    val isAvailable: Boolean get() = binary.exists()

    /** Moteur d'analyse, toujours à pleine puissance. */
    val analysisEngine: StockfishEngine by lazy { StockfishEngine(listOf(binary.absolutePath)) }

    private var play: StockfishEngine? = null

    /** Moteur adversaire (force réglable), séparé pour ne pas perturber l'analyse. */
    @Synchronized
    fun playEngine(): StockfishEngine = play ?: StockfishEngine(listOf(binary.absolutePath)).also { play = it }

    @Synchronized
    fun releasePlayEngine() {
        play?.close()
        play = null
    }

    init {
        scope.launch {
            settings.map { EngineOptions(threads = it.effectiveThreads, hashMb = it.hashMb) }
                .distinctUntilChanged()
                .collect { opts -> runCatching { analysisEngine.configure(opts) } }
        }
    }

    /** Nombre de cœurs disponibles (8 sur le Snapdragon 8 Gen 2 du S23 Ultra). */
    val cpuCores: Int = Runtime.getRuntime().availableProcessors()
}
