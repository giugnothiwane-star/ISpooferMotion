package com.chessoverlay.chess

/** Couleur d'une pièce ou du camp au trait. */
enum class PieceColor {
    WHITE, BLACK;

    val opposite: PieceColor get() = if (this == WHITE) BLACK else WHITE

    /** Nom français au féminin ("blanche"/"noire") pour qualifier une pièce. */
    val frenchAdjective: String get() = if (this == WHITE) "blanc" else "noir"
    val frenchAdjectiveFeminine: String get() = if (this == WHITE) "blanche" else "noire"
    val frenchSide: String get() = if (this == WHITE) "Blancs" else "Noirs"
}

/**
 * Type de pièce. [sanLetter] suit la notation internationale (PGN) ; [frenchLetter] la notation
 * algébrique française (R, D, T, F, C). [value] est la valeur matérielle classique en centipions.
 */
enum class PieceType(
    val fenChar: Char,
    val sanLetter: String,
    val frenchLetter: String,
    val frenchName: String,
    val value: Int,
    /** Genre grammatical en français, pour accorder les adjectifs. */
    val feminine: Boolean,
) {
    PAWN('p', "", "", "Pion", 100, false),
    KNIGHT('n', "N", "C", "Cavalier", 320, false),
    BISHOP('b', "B", "F", "Fou", 330, false),
    ROOK('r', "R", "T", "Tour", 500, true),
    QUEEN('q', "Q", "D", "Dame", 900, true),
    KING('k', "K", "R", "Roi", 20000, false);

    companion object {
        fun fromChar(c: Char): PieceType? = entries.firstOrNull { it.fenChar == c.lowercaseChar() }
        fun fromSanLetter(c: Char): PieceType? = when (c) {
            'N' -> KNIGHT; 'B' -> BISHOP; 'R' -> ROOK; 'Q' -> QUEEN; 'K' -> KING; else -> null
        }
        val PROMOTIONS = listOf(QUEEN, ROOK, BISHOP, KNIGHT)
    }
}

/** Une pièce : type + couleur. Les 12 instances sont mises en cache via [Piece.of]. */
data class Piece(val type: PieceType, val color: PieceColor) {
    val fenChar: Char get() = if (color == PieceColor.WHITE) type.fenChar.uppercaseChar() else type.fenChar

    /** Ex. : "Cavalier blanc", "Dame noire". */
    val frenchName: String
        get() {
            val adj = if (type.feminine) color.frenchAdjectiveFeminine else color.frenchAdjective
            return "${type.frenchName} $adj"
        }

    companion object {
        private val cache = Array(12) { i -> Piece(PieceType.entries[i % 6], PieceColor.entries[i / 6]) }
        fun of(type: PieceType, color: PieceColor): Piece = cache[color.ordinal * 6 + type.ordinal]
        fun fromFenChar(c: Char): Piece? {
            val t = PieceType.fromChar(c) ?: return null
            return of(t, if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK)
        }
    }
}

/** Utilitaires sur les cases, représentées par un entier 0..63 (a1 = 0, h1 = 7, a8 = 56, h8 = 63). */
object Sq {
    const val NONE = -1
    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq and 7
    fun rank(sq: Int): Int = sq shr 3
    fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
    fun name(sq: Int): String = if (sq !in 0..63) "-" else "${'a' + file(sq)}${rank(sq) + 1}"
    /** Nom affiché en majuscules dans l'interface ("F3"). */
    fun upperName(sq: Int): String = name(sq).uppercase()
    fun isLight(sq: Int): Boolean = (file(sq) + rank(sq)) % 2 == 1
    fun parse(name: String): Int {
        if (name.length != 2) return NONE
        val f = name[0].lowercaseChar() - 'a'
        val r = name[1] - '1'
        return if (isValid(f, r)) of(f, r) else NONE
    }

    val A1 = 0; val B1 = 1; val C1 = 2; val D1 = 3; val E1 = 4; val F1 = 5; val G1 = 6; val H1 = 7
    val A8 = 56; val B8 = 57; val C8 = 58; val D8 = 59; val E8 = 60; val F8 = 61; val G8 = 62; val H8 = 63
}

/**
 * Un coup, défini par sa case de départ, sa case d'arrivée et la pièce de promotion éventuelle.
 * Le roque est codé comme un déplacement du roi de deux cases (convention UCI : e1g1, e1c1).
 */
data class Move(val from: Int, val to: Int, val promotion: PieceType? = null) {
    val uci: String get() = Sq.name(from) + Sq.name(to) + (promotion?.fenChar?.toString() ?: "")
    override fun toString(): String = uci

    companion object {
        /** Analyse syntaxique d'un coup UCI ("e2e4", "e7e8q") sans vérifier sa légalité. */
        fun fromUci(uci: String): Move? {
            if (uci.length !in 4..5) return null
            val from = Sq.parse(uci.substring(0, 2))
            val to = Sq.parse(uci.substring(2, 4))
            if (from < 0 || to < 0) return null
            val promo = if (uci.length == 5) PieceType.fromChar(uci[4]) ?: return null else null
            if (promo == PieceType.KING || promo == PieceType.PAWN) return null
            return Move(from, to, promo)
        }
    }
}

/** Droits au roque. */
data class CastlingRights(
    val whiteKingSide: Boolean,
    val whiteQueenSide: Boolean,
    val blackKingSide: Boolean,
    val blackQueenSide: Boolean,
) {
    fun toFen(): String {
        val s = buildString {
            if (whiteKingSide) append('K')
            if (whiteQueenSide) append('Q')
            if (blackKingSide) append('k')
            if (blackQueenSide) append('q')
        }
        return s.ifEmpty { "-" }
    }

    fun kingSide(color: PieceColor) = if (color == PieceColor.WHITE) whiteKingSide else blackKingSide
    fun queenSide(color: PieceColor) = if (color == PieceColor.WHITE) whiteQueenSide else blackQueenSide

    companion object {
        val ALL = CastlingRights(true, true, true, true)
        val NONE = CastlingRights(false, false, false, false)
        fun fromFen(s: String): CastlingRights? {
            if (s == "-") return NONE
            if (s.any { it !in "KQkq" }) return null
            return CastlingRights('K' in s, 'Q' in s, 'k' in s, 'q' in s)
        }
    }
}

/** Nature d'un coup, telle qu'affichée à l'utilisateur. */
enum class MoveKind(val frenchLabel: String) {
    NORMAL("Déplacement normal"),
    CAPTURE("Capture"),
    EN_PASSANT("Prise en passant"),
    CASTLE_KINGSIDE("Petit roque"),
    CASTLE_QUEENSIDE("Grand roque"),
    PROMOTION("Promotion"),
    PROMOTION_CAPTURE("Promotion avec capture"),
}

/** Erreur de lecture d'une FEN ou d'un PGN, avec un message en français. */
class ChessFormatException(message: String) : IllegalArgumentException(message)
