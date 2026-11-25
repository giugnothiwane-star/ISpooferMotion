package com.chessoverlay.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.chessoverlay.analysis.AnalysisService
import com.chessoverlay.analysis.OpeningBook
import com.chessoverlay.app.engine.EngineProvider
import com.chessoverlay.app.util.SoundPlayer
import com.chessoverlay.app.vision.RecognitionManager
import com.chessoverlay.data.AppSettings
import com.chessoverlay.data.ChessDatabase
import com.chessoverlay.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChessApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Libère le moteur de jeu (le moteur d'analyse reste disponible) si le système manque de mémoire.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.engines.releasePlayEngine()
    }
}

/** Dépendances partagées de l'application (injection manuelle, sans framework). */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = SettingsRepository(context)
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val database: ChessDatabase by lazy { ChessDatabase.create(context) }
    val engines = EngineProvider(context, settings, scope)
    val analysis: AnalysisService by lazy { AnalysisService(engines.analysisEngine) }
    val openings: OpeningBook by lazy { OpeningBook.loadDefault() }
    val recognition = RecognitionManager(context, scope)
    val sounds = SoundPlayer(context, settings)

    init {
        // Préchargement en arrière-plan de la base d'ouvertures.
        scope.launch { runCatching { openings } }
    }
}

val Context.container: AppContainer get() = (applicationContext as ChessApp).container
