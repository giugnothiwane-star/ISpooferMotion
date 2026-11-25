package com.chessoverlay.ui.board

import androidx.compose.ui.graphics.Color
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.ui.R

/** Jeux de pièces vectoriels (licences libres compatibles GPL, voir l'écran « À propos »). */
enum class PieceSet(val id: String, val label: String, val credit: String) {
    CBURNETT("cburnett", "Classique (Cburnett)", "Colin M.L. Burnett — GPLv2+"),
    MERIDA("merida", "Merida", "Armando Hernandez Marroquin — GPLv2+"),
    CHESSNUT("chessnut", "Chessnut", "Alexis Luengas — Apache 2.0");

    fun drawable(piece: Piece): Int = when (this) {
        CBURNETT -> when (piece.color) {
            PieceColor.WHITE -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_cburnett_wk
                PieceType.QUEEN -> R.drawable.piece_cburnett_wq
                PieceType.ROOK -> R.drawable.piece_cburnett_wr
                PieceType.BISHOP -> R.drawable.piece_cburnett_wb
                PieceType.KNIGHT -> R.drawable.piece_cburnett_wn
                PieceType.PAWN -> R.drawable.piece_cburnett_wp
            }
            PieceColor.BLACK -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_cburnett_bk
                PieceType.QUEEN -> R.drawable.piece_cburnett_bq
                PieceType.ROOK -> R.drawable.piece_cburnett_br
                PieceType.BISHOP -> R.drawable.piece_cburnett_bb
                PieceType.KNIGHT -> R.drawable.piece_cburnett_bn
                PieceType.PAWN -> R.drawable.piece_cburnett_bp
            }
        }
        MERIDA -> when (piece.color) {
            PieceColor.WHITE -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_merida_wk
                PieceType.QUEEN -> R.drawable.piece_merida_wq
                PieceType.ROOK -> R.drawable.piece_merida_wr
                PieceType.BISHOP -> R.drawable.piece_merida_wb
                PieceType.KNIGHT -> R.drawable.piece_merida_wn
                PieceType.PAWN -> R.drawable.piece_merida_wp
            }
            PieceColor.BLACK -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_merida_bk
                PieceType.QUEEN -> R.drawable.piece_merida_bq
                PieceType.ROOK -> R.drawable.piece_merida_br
                PieceType.BISHOP -> R.drawable.piece_merida_bb
                PieceType.KNIGHT -> R.drawable.piece_merida_bn
                PieceType.PAWN -> R.drawable.piece_merida_bp
            }
        }
        CHESSNUT -> when (piece.color) {
            PieceColor.WHITE -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_chessnut_wk
                PieceType.QUEEN -> R.drawable.piece_chessnut_wq
                PieceType.ROOK -> R.drawable.piece_chessnut_wr
                PieceType.BISHOP -> R.drawable.piece_chessnut_wb
                PieceType.KNIGHT -> R.drawable.piece_chessnut_wn
                PieceType.PAWN -> R.drawable.piece_chessnut_wp
            }
            PieceColor.BLACK -> when (piece.type) {
                PieceType.KING -> R.drawable.piece_chessnut_bk
                PieceType.QUEEN -> R.drawable.piece_chessnut_bq
                PieceType.ROOK -> R.drawable.piece_chessnut_br
                PieceType.BISHOP -> R.drawable.piece_chessnut_bb
                PieceType.KNIGHT -> R.drawable.piece_chessnut_bn
                PieceType.PAWN -> R.drawable.piece_chessnut_bp
            }
        }
    }

    companion object {
        fun fromId(id: String): PieceSet = entries.firstOrNull { it.id == id } ?: CBURNETT
    }
}

/** Thèmes de couleurs de l'échiquier. */
enum class BoardTheme(val id: String, val label: String, val light: Color, val dark: Color, val coordLight: Color, val coordDark: Color) {
    GRAPHITE("graphite", "Graphite néon", Color(0xFF9A93B8), Color(0xFF4B4566), Color(0xFF4B4566), Color(0xFFB9B3D6)),
    AMETHYST("amethyst", "Améthyste", Color(0xFFE4DCF5), Color(0xFF8A6CC4), Color(0xFF8A6CC4), Color(0xFFE4DCF5)),
    GLACIER("glacier", "Glacier cyan", Color(0xFFDDEBF1), Color(0xFF4F8CA6), Color(0xFF4F8CA6), Color(0xFFDDEBF1)),
    WOOD("wood", "Bois classique", Color(0xFFF0D9B5), Color(0xFFB58863), Color(0xFFB58863), Color(0xFFF0D9B5)),
    TOURNAMENT("tournament", "Tournoi vert", Color(0xFFEBECD0), Color(0xFF739552), Color(0xFF739552), Color(0xFFEBECD0)),
    MARBLE("marble", "Marbre", Color(0xFFE8E8EA), Color(0xFF8D8F98), Color(0xFF8D8F98), Color(0xFFE8E8EA));

    companion object {
        fun fromId(id: String): BoardTheme = entries.firstOrNull { it.id == id } ?: GRAPHITE
    }
}
