package com.chessoverlay.vision

import com.chessoverlay.chess.CastlingRights
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Position

/** Cadre de l'échiquier dans l'image (coordonnées en pixels de l'image d'origine). */
data class BoardRect(val left: Float, val top: Float, val size: Float) {
    val squareSize: Float get() = size / 8f
}

/** Résultat pour une case : pièce reconnue (null = case vide) et confiance entre 0 et 1. */
data class SquareRecognition(val square: Int, val piece: Piece?, val confidence: Float)

/** Erreur de reconnaissance (message destiné à l'utilisateur, en français). */
class RecognitionException(message: String) : Exception(message)

/** Résultat complet de la reconnaissance d'un échiquier sur une image. */
data class RecognitionResult(
    val boardRect: BoardRect,
    val whiteAtBottom: Boolean,
    /** 64 éléments, l'index correspond à la case (a1 = 0, h8 = 63). */
    val squares: List<SquareRecognition>,
    /** Camp au trait déduit du surlignage du dernier coup, ou null si inconnu. */
    val suggestedSideToMove: PieceColor?,
    /** Dernier coup déduit du surlignage (case de départ, case d'arrivée), ou null. */
    val lastMove: Pair<Int, Int>?,
    /** Case de prise en passant déduite du dernier coup, ou Sq.NONE. */
    val suggestedEpSquare: Int,
    val overallConfidence: Float,
    val warnings: List<String>,
    val calibratedFromStartPosition: Boolean,
) {
    fun board(): Array<Piece?> = Array(64) { squares[it].piece }

    fun lowConfidenceSquares(threshold: Float = 0.6f): List<Int> =
        squares.filter { it.confidence < threshold }.map { it.square }

    /**
     * Construit la position. Si [castling] est null, les droits au roque sont déduits du placement
     * (roi et tour sur leurs cases d'origine).
     */
    fun toPosition(sideToMove: PieceColor, castling: CastlingRights? = null): Position {
        val board = board()
        val ep = if (suggestedSideToMove == sideToMove) suggestedEpSquare else -1
        return Position.create(board, sideToMove, castling ?: CastlingRights.ALL, ep)
    }
}
