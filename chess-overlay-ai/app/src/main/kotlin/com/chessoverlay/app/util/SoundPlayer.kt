package com.chessoverlay.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.chessoverlay.app.R
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Position
import com.chessoverlay.data.AppSettings
import kotlinx.coroutines.flow.StateFlow

enum class SoundKind { MOVE, CAPTURE, CHECK, END }

/** Effets sonores courts (générés pour l'application, aucun fichier tiers). */
class SoundPlayer(context: Context, private val settings: StateFlow<AppSettings>) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()
    private val ids = mapOf(
        SoundKind.MOVE to pool.load(context, R.raw.move, 1),
        SoundKind.CAPTURE to pool.load(context, R.raw.capture, 1),
        SoundKind.CHECK to pool.load(context, R.raw.check, 1),
        SoundKind.END to pool.load(context, R.raw.end, 1),
    )

    fun play(kind: SoundKind) {
        if (!settings.value.sounds) return
        ids[kind]?.let { pool.play(it, 0.8f, 0.8f, 1, 0, 1f) }
    }

    /** Joue le son adapté au coup [move] joué depuis [before]. */
    fun playFor(before: Position, move: Move) {
        val after = before.playOrNull(move) ?: return
        when {
            after.isCheckmate || after.isStalemate -> play(SoundKind.END)
            after.isCheck -> play(SoundKind.CHECK)
            before.isCapture(move) -> play(SoundKind.CAPTURE)
            else -> play(SoundKind.MOVE)
        }
    }
}
