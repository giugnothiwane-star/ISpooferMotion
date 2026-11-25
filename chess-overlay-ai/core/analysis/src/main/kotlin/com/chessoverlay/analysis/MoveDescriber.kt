package com.chessoverlay.analysis

import com.chessoverlay.chess.Move
import com.chessoverlay.chess.MoveKind
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.San
import com.chessoverlay.chess.Sq

/** Description complète et factuelle d'un coup légal. */
data class MoveDescription(
    val move: Move,
    val uci: String,
    /** Notation PGN internationale (Nf3). */
    val san: String,
    /** Notation algébrique française (Cf3). */
    val sanFrench: String,
    val piece: Piece,
    val from: Int,
    val to: Int,
    val kind: MoveKind,
    val captured: Piece?,
    /** Case de la pièce capturée (différente de [to] en cas de prise en passant), ou -1. */
    val capturedSquare: Int,
    val promotion: PieceType?,
    val givesCheck: Boolean,
    val givesMate: Boolean,
) {
    val isCapture: Boolean get() = captured != null

    /** Ex. : « Cavalier blanc : F3 → E5 ». */
    val headline: String get() = "${piece.frenchName} : ${Sq.upperName(from)} → ${Sq.upperName(to)}"

    /** Phrase complète en français décrivant le coup. */
    fun summaryFr(): String {
        val subject = article(piece) + piece.frenchName
        val f = Sq.upperName(from)
        val t = Sq.upperName(to)
        val base = when (kind) {
            MoveKind.CASTLE_KINGSIDE, MoveKind.CASTLE_QUEENSIDE -> {
                val rank = Sq.rank(from)
                val (rf, rt) = if (kind == MoveKind.CASTLE_KINGSIDE) Sq.of(7, rank) to Sq.of(5, rank) else Sq.of(0, rank) to Sq.of(3, rank)
                "$subject effectue le ${kind.frenchLabel.lowercase()} : il va de $f à $t et la Tour passe de ${Sq.upperName(rf)} à ${Sq.upperName(rt)}"
            }
            MoveKind.EN_PASSANT ->
                "$subject prend en passant ${article(captured!!).lowercase()}${captured.frenchName} situé en ${Sq.upperName(capturedSquare)} (il va de $f à $t)"
            MoveKind.PROMOTION ->
                "$subject avance de $f à $t et se promeut en ${promotion!!.frenchName}"
            MoveKind.PROMOTION_CAPTURE ->
                "$subject va de $f à $t en capturant ${article(captured!!).lowercase()}${captured.frenchName} et se promeut en ${promotion!!.frenchName}"
            MoveKind.CAPTURE ->
                "$subject va de $f à $t et capture ${article(captured!!).lowercase()}${captured.frenchName} en $t"
            MoveKind.NORMAL -> "$subject va de $f à $t"
        }
        val suffix = when {
            givesMate -> " : échec et mat !"
            givesCheck -> ", avec échec."
            else -> "."
        }
        return base + suffix
    }

    companion object {
        internal fun article(p: Piece): String = if (p.type.feminine) "La " else "Le "
    }
}

object MoveDescriber {

    fun describe(pos: Position, move: Move): MoveDescription {
        val m = pos.normalize(move) ?: throw IllegalArgumentException("Coup illégal : ${move.uci}")
        val piece = pos.pieceAt(m.from)!!
        val next = pos.play(m)
        val capSq = pos.capturedSquare(m)
        return MoveDescription(
            move = m,
            uci = m.uci,
            san = San.toSan(pos, m, french = false),
            sanFrench = San.toSan(pos, m, french = true),
            piece = piece,
            from = m.from,
            to = m.to,
            kind = pos.moveKind(m),
            captured = if (capSq >= 0) pos.pieceAt(capSq) else null,
            capturedSquare = capSq,
            promotion = m.promotion,
            givesCheck = next.isCheck,
            givesMate = next.isCheckmate,
        )
    }

    /** Décrit une suite de coups UCI jouée depuis [pos] ; s'arrête au premier coup illégal ou illisible. */
    fun describeLine(pos: Position, uciMoves: List<String>): List<MoveDescription> {
        val out = ArrayList<MoveDescription>(uciMoves.size)
        var p = pos
        for (u in uciMoves) {
            val m = Move.fromUci(u)?.let { p.normalize(it) } ?: break
            out += describe(p, m)
            p = p.play(m)
        }
        return out
    }

    /** Variante numérotée en notation française, ex. « 12. Cf3 Cc6 13. Fb5 » ou « 12... Cc6 13. Fb5 ». */
    fun numberedLine(pos: Position, moves: List<MoveDescription>, french: Boolean = true): String {
        val sb = StringBuilder()
        var no = pos.fullmoveNumber
        var white = pos.sideToMove == com.chessoverlay.chess.PieceColor.WHITE
        for ((i, d) in moves.withIndex()) {
            val san = if (french) d.sanFrench else d.san
            if (white) sb.append(no).append(". ").append(san).append(' ')
            else if (i == 0) sb.append(no).append("... ").append(san).append(' ')
            else sb.append(san).append(' ')
            if (!white) no++
            white = !white
        }
        return sb.toString().trim()
    }
}
