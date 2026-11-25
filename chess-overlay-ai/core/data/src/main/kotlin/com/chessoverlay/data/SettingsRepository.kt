package com.chessoverlay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Préférences de l'application, sauvegardées localement (DataStore). */
data class AppSettings(
    // Moteur
    val depth: Int = 18,
    /** Temps de réflexion maximal en millisecondes (0 = limité uniquement par la profondeur). */
    val moveTimeMs: Int = 0,
    val multiPv: Int = 3,
    val threads: Int = 4,
    val hashMb: Int = 128,
    val powerSaving: Boolean = false,
    val autoAnalyze: Boolean = true,
    // Échiquier
    val boardTheme: String = "graphite",
    val pieceStyle: String = "cburnett",
    val showCoordinates: Boolean = true,
    val showArrows: Boolean = true,
    val animations: Boolean = true,
    val frenchNotation: Boolean = true,
    // Menu flottant
    val overlayAlpha: Float = 0.94f,
    val overlayScale: Float = 1.0f,
    // Divers
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val keepHistory: Boolean = true,
    val fairPlayAccepted: Boolean = false,
    val solvedPuzzles: Set<String> = emptySet(),
) {
    /** Paramètres effectivement appliqués au moteur selon le mode économie d'énergie. */
    val effectiveThreads: Int get() = if (powerSaving) minOf(threads, 2) else threads
    val effectiveDepth: Int get() = if (powerSaving) minOf(depth, 16) else depth
}

class SettingsRepository(private val context: Context) {

    private object K {
        val depth = intPreferencesKey("depth")
        val moveTime = intPreferencesKey("move_time_ms")
        val multiPv = intPreferencesKey("multipv")
        val threads = intPreferencesKey("threads")
        val hash = intPreferencesKey("hash_mb")
        val powerSaving = booleanPreferencesKey("power_saving")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val boardTheme = stringPreferencesKey("board_theme")
        val pieceStyle = stringPreferencesKey("piece_style")
        val coords = booleanPreferencesKey("coords")
        val arrows = booleanPreferencesKey("arrows")
        val animations = booleanPreferencesKey("animations")
        val french = booleanPreferencesKey("french_notation")
        val overlayAlpha = floatPreferencesKey("overlay_alpha")
        val overlayScale = floatPreferencesKey("overlay_scale")
        val sounds = booleanPreferencesKey("sounds")
        val haptics = booleanPreferencesKey("haptics")
        val keepHistory = booleanPreferencesKey("keep_history")
        val fairPlay = booleanPreferencesKey("fair_play_accepted")
        val solved = stringSetPreferencesKey("solved_puzzles")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { readFrom(it, AppSettings()) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val old = AppSettings()
            val s = transform(readFrom(p, old))
            p[K.depth] = s.depth.coerceIn(6, 40)
            p[K.moveTime] = s.moveTimeMs.coerceIn(0, 60_000)
            p[K.multiPv] = s.multiPv.coerceIn(1, 5)
            p[K.threads] = s.threads.coerceIn(1, 8)
            p[K.hash] = s.hashMb.coerceIn(16, 1024)
            p[K.powerSaving] = s.powerSaving
            p[K.autoAnalyze] = s.autoAnalyze
            p[K.boardTheme] = s.boardTheme
            p[K.pieceStyle] = s.pieceStyle
            p[K.coords] = s.showCoordinates
            p[K.arrows] = s.showArrows
            p[K.animations] = s.animations
            p[K.french] = s.frenchNotation
            p[K.overlayAlpha] = s.overlayAlpha.coerceIn(0.3f, 1f)
            p[K.overlayScale] = s.overlayScale.coerceIn(0.7f, 1.4f)
            p[K.sounds] = s.sounds
            p[K.haptics] = s.haptics
            p[K.keepHistory] = s.keepHistory
            p[K.fairPlay] = s.fairPlayAccepted
            p[K.solved] = s.solvedPuzzles
        }
    }

    private fun readFrom(p: Preferences, d: AppSettings) = AppSettings(
        depth = p[K.depth] ?: d.depth,
        moveTimeMs = p[K.moveTime] ?: d.moveTimeMs,
        multiPv = p[K.multiPv] ?: d.multiPv,
        threads = p[K.threads] ?: d.threads,
        hashMb = p[K.hash] ?: d.hashMb,
        powerSaving = p[K.powerSaving] ?: d.powerSaving,
        autoAnalyze = p[K.autoAnalyze] ?: d.autoAnalyze,
        boardTheme = p[K.boardTheme] ?: d.boardTheme,
        pieceStyle = p[K.pieceStyle] ?: d.pieceStyle,
        showCoordinates = p[K.coords] ?: d.showCoordinates,
        showArrows = p[K.arrows] ?: d.showArrows,
        animations = p[K.animations] ?: d.animations,
        frenchNotation = p[K.french] ?: d.frenchNotation,
        overlayAlpha = p[K.overlayAlpha] ?: d.overlayAlpha,
        overlayScale = p[K.overlayScale] ?: d.overlayScale,
        sounds = p[K.sounds] ?: d.sounds,
        haptics = p[K.haptics] ?: d.haptics,
        keepHistory = p[K.keepHistory] ?: d.keepHistory,
        fairPlayAccepted = p[K.fairPlay] ?: d.fairPlayAccepted,
        solvedPuzzles = p[K.solved] ?: d.solvedPuzzles,
    )
}
