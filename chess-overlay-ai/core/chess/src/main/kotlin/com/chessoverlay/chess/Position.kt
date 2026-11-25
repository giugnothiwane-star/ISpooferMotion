package com.chessoverlay.chess

/**
 * Position d'échecs immuable, avec génération complète des coups légaux (roques, prise en passant,
 * promotions), détection d'échec, de mat, de pat et de matériel insuffisant.
 */
class Position private constructor(
    private val board: Array<Piece?>,
    val sideToMove: PieceColor,
    val castling: CastlingRights,
    /** Case de prise en passant (case « sautée » par le pion), ou [Sq.NONE]. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(sq: Int): Piece? = board[sq]

    /** Copie du plateau (index 0 = a1). */
    fun boardCopy(): Array<Piece?> = board.copyOf()

    /** Liste (case, pièce) des pièces présentes. */
    fun pieces(): List<Pair<Int, Piece>> = (0 until 64).mapNotNull { sq -> board[sq]?.let { sq to it } }

    fun pieces(color: PieceColor): List<Pair<Int, Piece>> = pieces().filter { it.second.color == color }

    fun kingSquare(color: PieceColor): Int {
        val king = Piece.of(PieceType.KING, color)
        for (sq in 0 until 64) if (board[sq] == king) return sq
        return Sq.NONE
    }

    // ------------------------------------------------------------------ attaques

    /** Vrai si la case [sq] est attaquée par au moins une pièce de couleur [by]. */
    fun isAttacked(sq: Int, by: PieceColor): Boolean = attackersOf(sq, by, stopAtFirst = true).isNotEmpty()

    /** Cases des pièces de couleur [by] qui attaquent [sq]. */
    fun attackers(sq: Int, by: PieceColor): List<Int> = attackersOf(sq, by, stopAtFirst = false)

    private fun attackersOf(sq: Int, by: PieceColor, stopAtFirst: Boolean): List<Int> {
        val result = ArrayList<Int>(4)
        val f = Sq.file(sq)
        val r = Sq.rank(sq)
        // Pions : un pion blanc en (f±1, r-1) attaque (f, r).
        val pr = if (by == PieceColor.WHITE) r - 1 else r + 1
        val pawn = Piece.of(PieceType.PAWN, by)
        for (df in intArrayOf(-1, 1)) {
            val pf = f + df
            if (Sq.isValid(pf, pr) && board[Sq.of(pf, pr)] == pawn) {
                result.add(Sq.of(pf, pr)); if (stopAtFirst) return result
            }
        }
        val knight = Piece.of(PieceType.KNIGHT, by)
        for (d in KNIGHT_DELTAS) {
            val nf = f + d[0]; val nr = r + d[1]
            if (Sq.isValid(nf, nr) && board[Sq.of(nf, nr)] == knight) {
                result.add(Sq.of(nf, nr)); if (stopAtFirst) return result
            }
        }
        val king = Piece.of(PieceType.KING, by)
        for (d in KING_DELTAS) {
            val nf = f + d[0]; val nr = r + d[1]
            if (Sq.isValid(nf, nr) && board[Sq.of(nf, nr)] == king) {
                result.add(Sq.of(nf, nr)); if (stopAtFirst) return result
            }
        }
        for (d in BISHOP_DIRS) {
            val hit = firstPieceOnRay(f, r, d[0], d[1])
            if (hit >= 0) {
                val p = board[hit]!!
                if (p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                    result.add(hit); if (stopAtFirst) return result
                }
            }
        }
        for (d in ROOK_DIRS) {
            val hit = firstPieceOnRay(f, r, d[0], d[1])
            if (hit >= 0) {
                val p = board[hit]!!
                if (p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                    result.add(hit); if (stopAtFirst) return result
                }
            }
        }
        return result
    }

    /** Première case occupée en partant de (f, r) dans la direction (df, dr), ou -1. */
    fun firstPieceOnRay(f: Int, r: Int, df: Int, dr: Int): Int {
        var nf = f + df; var nr = r + dr
        while (Sq.isValid(nf, nr)) {
            val s = Sq.of(nf, nr)
            if (board[s] != null) return s
            nf += df; nr += dr
        }
        return -1
    }

    /** Cases attaquées par la pièce située en [sq] (y compris les cases occupées par n'importe quelle couleur). */
    fun attacksFrom(sq: Int): List<Int> {
        val p = board[sq] ?: return emptyList()
        val f = Sq.file(sq); val r = Sq.rank(sq)
        val out = ArrayList<Int>()
        when (p.type) {
            PieceType.PAWN -> {
                val dr = if (p.color == PieceColor.WHITE) 1 else -1
                for (df in intArrayOf(-1, 1)) if (Sq.isValid(f + df, r + dr)) out.add(Sq.of(f + df, r + dr))
            }
            PieceType.KNIGHT -> for (d in KNIGHT_DELTAS) if (Sq.isValid(f + d[0], r + d[1])) out.add(Sq.of(f + d[0], r + d[1]))
            PieceType.KING -> for (d in KING_DELTAS) if (Sq.isValid(f + d[0], r + d[1])) out.add(Sq.of(f + d[0], r + d[1]))
            else -> {
                val dirs = when (p.type) {
                    PieceType.BISHOP -> BISHOP_DIRS
                    PieceType.ROOK -> ROOK_DIRS
                    else -> QUEEN_DIRS
                }
                for (d in dirs) {
                    var nf = f + d[0]; var nr = r + d[1]
                    while (Sq.isValid(nf, nr)) {
                        val s = Sq.of(nf, nr)
                        out.add(s)
                        if (board[s] != null) break
                        nf += d[0]; nr += d[1]
                    }
                }
            }
        }
        return out
    }

    val isCheck: Boolean
        get() {
            val k = kingSquare(sideToMove)
            return k >= 0 && isAttacked(k, sideToMove.opposite)
        }

    // ------------------------------------------------------------------ coups

    private var legalCache: List<Move>? = null

    /** Tous les coups légaux du camp au trait. */
    fun legalMoves(): List<Move> {
        legalCache?.let { return it }
        val moves = pseudoLegalMoves().filter { isLegalAfter(it) }
        legalCache = moves
        return moves
    }

    fun legalMovesFrom(sq: Int): List<Move> = legalMoves().filter { it.from == sq }

    fun isLegal(move: Move): Boolean = legalMoves().contains(move)

    val isCheckmate: Boolean get() = isCheck && legalMoves().isEmpty()
    val isStalemate: Boolean get() = !isCheck && legalMoves().isEmpty()

    /** Matériel insuffisant pour mater (R vs R, R+F vs R, R+C vs R, R+F vs R+F de même couleur de cases). */
    val isInsufficientMaterial: Boolean
        get() {
            val others = pieces().filter { it.second.type != PieceType.KING }
            if (others.isEmpty()) return true
            if (others.any { it.second.type in setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN) }) return false
            if (others.size == 1) return true // un seul fou ou cavalier
            if (others.all { it.second.type == PieceType.BISHOP }) {
                val colors = others.map { Sq.isLight(it.first) }.toSet()
                return colors.size == 1
            }
            return false
        }

    private fun isLegalAfter(move: Move): Boolean {
        val next = applyUnchecked(move)
        val k = next.kingSquare(sideToMove)
        return k >= 0 && !next.isAttacked(k, sideToMove.opposite)
    }

    private fun pseudoLegalMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        val us = sideToMove
        for (sq in 0 until 64) {
            val p = board[sq] ?: continue
            if (p.color != us) continue
            val f = Sq.file(sq); val r = Sq.rank(sq)
            when (p.type) {
                PieceType.PAWN -> genPawn(sq, f, r, us, moves)
                PieceType.KNIGHT -> for (d in KNIGHT_DELTAS) addIfTarget(sq, f + d[0], r + d[1], us, moves)
                PieceType.KING -> {
                    for (d in KING_DELTAS) addIfTarget(sq, f + d[0], r + d[1], us, moves)
                    genCastling(sq, us, moves)
                }
                PieceType.BISHOP -> genSlider(sq, f, r, BISHOP_DIRS, us, moves)
                PieceType.ROOK -> genSlider(sq, f, r, ROOK_DIRS, us, moves)
                PieceType.QUEEN -> genSlider(sq, f, r, QUEEN_DIRS, us, moves)
            }
        }
        return moves
    }

    private fun addIfTarget(from: Int, f: Int, r: Int, us: PieceColor, out: MutableList<Move>) {
        if (!Sq.isValid(f, r)) return
        val t = board[Sq.of(f, r)]
        if (t == null || t.color != us) out.add(Move(from, Sq.of(f, r)))
    }

    private fun genSlider(from: Int, f: Int, r: Int, dirs: Array<IntArray>, us: PieceColor, out: MutableList<Move>) {
        for (d in dirs) {
            var nf = f + d[0]; var nr = r + d[1]
            while (Sq.isValid(nf, nr)) {
                val s = Sq.of(nf, nr)
                val t = board[s]
                if (t == null) out.add(Move(from, s))
                else {
                    if (t.color != us) out.add(Move(from, s))
                    break
                }
                nf += d[0]; nr += d[1]
            }
        }
    }

    private fun genPawn(from: Int, f: Int, r: Int, us: PieceColor, out: MutableList<Move>) {
        val dir = if (us == PieceColor.WHITE) 1 else -1
        val startRank = if (us == PieceColor.WHITE) 1 else 6
        val lastRank = if (us == PieceColor.WHITE) 7 else 0
        fun add(to: Int) {
            if (Sq.rank(to) == lastRank) PieceType.PROMOTIONS.forEach { out.add(Move(from, to, it)) }
            else out.add(Move(from, to))
        }
        val r1 = r + dir
        if (Sq.isValid(f, r1) && board[Sq.of(f, r1)] == null) {
            add(Sq.of(f, r1))
            val r2 = r + 2 * dir
            if (r == startRank && board[Sq.of(f, r2)] == null) out.add(Move(from, Sq.of(f, r2)))
        }
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (!Sq.isValid(nf, r1)) continue
            val s = Sq.of(nf, r1)
            val t = board[s]
            if (t != null && t.color != us) add(s)
            else if (s == epSquare && t == null) out.add(Move(from, s))
        }
    }

    private fun genCastling(kingSq: Int, us: PieceColor, out: MutableList<Move>) {
        val home = if (us == PieceColor.WHITE) Sq.E1 else Sq.E8
        if (kingSq != home) return
        val them = us.opposite
        val rook = Piece.of(PieceType.ROOK, us)
        if (isAttacked(home, them)) return
        if (castling.kingSide(us) && board[home + 3] == rook &&
            board[home + 1] == null && board[home + 2] == null &&
            !isAttacked(home + 1, them) && !isAttacked(home + 2, them)
        ) out.add(Move(home, home + 2))
        if (castling.queenSide(us) && board[home - 4] == rook &&
            board[home - 1] == null && board[home - 2] == null && board[home - 3] == null &&
            !isAttacked(home - 1, them) && !isAttacked(home - 2, them)
        ) out.add(Move(home, home - 2))
    }

    // ------------------------------------------------------------------ informations sur un coup

    fun isCastling(move: Move): Boolean {
        val p = board[move.from] ?: return false
        return p.type == PieceType.KING && kotlin.math.abs(Sq.file(move.to) - Sq.file(move.from)) == 2
    }

    fun isEnPassant(move: Move): Boolean {
        val p = board[move.from] ?: return false
        return p.type == PieceType.PAWN && move.to == epSquare && board[move.to] == null &&
            Sq.file(move.from) != Sq.file(move.to)
    }

    /** Case de la pièce capturée par [move] (différente de la destination en cas de prise en passant), ou -1. */
    fun capturedSquare(move: Move): Int = when {
        isEnPassant(move) -> Sq.of(Sq.file(move.to), Sq.rank(move.from))
        board[move.to] != null && board[move.to]!!.color != board[move.from]?.color -> move.to
        else -> Sq.NONE
    }

    fun capturedPiece(move: Move): Piece? = capturedSquare(move).takeIf { it >= 0 }?.let { board[it] }

    fun isCapture(move: Move): Boolean = capturedSquare(move) >= 0

    fun moveKind(move: Move): MoveKind = when {
        isCastling(move) -> if (Sq.file(move.to) == 6) MoveKind.CASTLE_KINGSIDE else MoveKind.CASTLE_QUEENSIDE
        isEnPassant(move) -> MoveKind.EN_PASSANT
        move.promotion != null && isCapture(move) -> MoveKind.PROMOTION_CAPTURE
        move.promotion != null -> MoveKind.PROMOTION
        isCapture(move) -> MoveKind.CAPTURE
        else -> MoveKind.NORMAL
    }

    // ------------------------------------------------------------------ jouer un coup

    /** Joue un coup légal et renvoie la nouvelle position. Lève une exception si le coup est illégal. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Coup illégal : ${move.uci} dans ${toFen()}" }
        return applyUnchecked(move)
    }

    /** Variante tolérante : complète la promotion manquante par une dame. */
    fun playOrNull(move: Move): Position? {
        val m = normalize(move) ?: return null
        return applyUnchecked(m)
    }

    /** Renvoie le coup légal correspondant (ajoute la promotion dame si besoin), ou null. */
    fun normalize(move: Move): Move? {
        if (isLegal(move)) return move
        if (move.promotion == null) {
            val q = move.copy(promotion = PieceType.QUEEN)
            if (isLegal(q)) return q
        }
        return null
    }

    private fun applyUnchecked(move: Move): Position {
        val b = board.copyOf()
        val p = b[move.from] ?: error("Aucune pièce en ${Sq.name(move.from)}")
        val us = p.color
        var newEp = Sq.NONE
        val captured = capturedSquare(move)
        var half = halfmoveClock + 1
        if (p.type == PieceType.PAWN || captured >= 0) half = 0

        if (captured >= 0) b[captured] = null
        b[move.from] = null
        b[move.to] = if (move.promotion != null) Piece.of(move.promotion, us) else p

        if (p.type == PieceType.KING && kotlin.math.abs(Sq.file(move.to) - Sq.file(move.from)) == 2) {
            val rank = Sq.rank(move.from)
            if (Sq.file(move.to) == 6) {
                b[Sq.of(5, rank)] = b[Sq.of(7, rank)]; b[Sq.of(7, rank)] = null
            } else {
                b[Sq.of(3, rank)] = b[Sq.of(0, rank)]; b[Sq.of(0, rank)] = null
            }
        }
        if (p.type == PieceType.PAWN && kotlin.math.abs(Sq.rank(move.to) - Sq.rank(move.from)) == 2) {
            newEp = (move.from + move.to) / 2
        }
        var wk = castling.whiteKingSide; var wq = castling.whiteQueenSide
        var bk = castling.blackKingSide; var bq = castling.blackQueenSide
        for (s in intArrayOf(move.from, move.to)) {
            when (s) {
                Sq.E1 -> { wk = false; wq = false }
                Sq.E8 -> { bk = false; bq = false }
                Sq.H1 -> wk = false
                Sq.A1 -> wq = false
                Sq.H8 -> bk = false
                Sq.A8 -> bq = false
            }
        }
        return Position(
            b, us.opposite, CastlingRights(wk, wq, bk, bq), newEp, half,
            if (us == PieceColor.BLACK) fullmoveNumber + 1 else fullmoveNumber,
        )
    }

    /** Position identique avec l'autre camp au trait (« coup nul »), utile pour détecter les menaces. */
    fun withSideToMove(color: PieceColor): Position =
        Position(board.copyOf(), color, castling, Sq.NONE, halfmoveClock, fullmoveNumber)

    // ------------------------------------------------------------------ FEN

    fun toFen(): String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = board[Sq.of(file, rank)]
                if (p == null) empty++ else {
                    if (empty > 0) { append(empty); empty = 0 }
                    append(p.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (sideToMove == PieceColor.WHITE) " w " else " b ")
        append(castling.toFen())
        append(' ')
        append(if (epSquare >= 0) Sq.name(epSquare) else "-")
        append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
    }

    /** Clé de répétition : placement, trait, roques et prise en passant réellement jouable. */
    val repetitionKey: String
        get() {
            val ep = if (epSquare >= 0 && legalMoves().any { isEnPassant(it) }) Sq.name(epSquare) else "-"
            return toFen().split(' ').take(3).joinToString(" ") + " " + ep
        }

    override fun equals(other: Any?): Boolean = other is Position && other.toFen() == toFen()
    override fun hashCode(): Int = toFen().hashCode()
    override fun toString(): String = toFen()

    /**
     * Vérifie qu'une position (par exemple saisie dans l'éditeur ou reconnue sur une image) est
     * jouable. Renvoie la liste des problèmes en français (liste vide = position valide).
     */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        for (c in PieceColor.entries) {
            val n = pieces(c).count { it.second.type == PieceType.KING }
            if (n == 0) errors += "Il manque le roi ${c.frenchAdjective}."
            if (n > 1) errors += "Il y a $n rois ${c.frenchAdjective}s."
            val pawns = pieces(c).count { it.second.type == PieceType.PAWN }
            if (pawns > 8) errors += "Trop de pions ${c.frenchAdjective}s ($pawns)."
            val fem = if (c == PieceColor.WHITE) "blanches" else "noires"
            if (pieces(c).size > 16) errors += "Trop de pièces $fem (${pieces(c).size})."
        }
        if (pieces().any { it.second.type == PieceType.PAWN && (Sq.rank(it.first) == 0 || Sq.rank(it.first) == 7) }) {
            errors += "Un pion ne peut pas se trouver sur la 1re ou la 8e rangée."
        }
        val them = sideToMove.opposite
        val k = kingSquare(them)
        if (errors.isEmpty() && k >= 0 && isAttacked(k, sideToMove)) {
            errors += "Le roi ${them.frenchAdjective} est en échec alors que ce n'est pas son tour : vérifiez le trait."
        }
        return errors
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val KNIGHT_DELTAS = arrayOf(
            intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
            intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2),
        )
        val KING_DELTAS = arrayOf(
            intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, 1), intArrayOf(-1, 1),
            intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1),
        )
        val BISHOP_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))
        val ROOK_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        val QUEEN_DIRS = BISHOP_DIRS + ROOK_DIRS

        fun initial(): Position = fromFen(START_FEN)

        /** Construit une position à partir de ses composants (éditeur, reconnaissance d'image). */
        fun create(
            board: Array<Piece?>,
            sideToMove: PieceColor,
            castling: CastlingRights,
            epSquare: Int = Sq.NONE,
            halfmoveClock: Int = 0,
            fullmoveNumber: Int = 1,
        ): Position {
            require(board.size == 64) { "Le plateau doit contenir 64 cases." }
            return Position(board.copyOf(), sideToMove, sanitizeCastling(board, castling), epSquare, halfmoveClock, fullmoveNumber)
        }

        /** Retire les droits au roque incompatibles avec le placement du roi et des tours. */
        fun sanitizeCastling(board: Array<Piece?>, c: CastlingRights): CastlingRights {
            val wK = board[Sq.E1] == Piece.of(PieceType.KING, PieceColor.WHITE)
            val bK = board[Sq.E8] == Piece.of(PieceType.KING, PieceColor.BLACK)
            val wR = Piece.of(PieceType.ROOK, PieceColor.WHITE)
            val bR = Piece.of(PieceType.ROOK, PieceColor.BLACK)
            return CastlingRights(
                c.whiteKingSide && wK && board[Sq.H1] == wR,
                c.whiteQueenSide && wK && board[Sq.A1] == wR,
                c.blackKingSide && bK && board[Sq.H8] == bR,
                c.blackQueenSide && bK && board[Sq.A8] == bR,
            )
        }

        /** Lit une FEN. Les champs manquants après le placement prennent des valeurs par défaut. */
        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty()) throw ChessFormatException("FEN vide.")
            val rows = parts[0].split('/')
            if (rows.size != 8) throw ChessFormatException("La FEN doit contenir 8 rangées (trouvé : ${rows.size}).")
            val board = arrayOfNulls<Piece>(64)
            for ((i, row) in rows.withIndex()) {
                val rank = 7 - i
                var file = 0
                for (c in row) {
                    if (c.isDigit()) {
                        val n = c - '0'
                        if (n !in 1..8) throw ChessFormatException("Chiffre invalide '$c' dans la FEN.")
                        file += n
                    } else {
                        val p = Piece.fromFenChar(c) ?: throw ChessFormatException("Caractère inconnu '$c' dans la FEN.")
                        if (file > 7) throw ChessFormatException("Rangée ${rank + 1} trop longue dans la FEN.")
                        board[Sq.of(file, rank)] = p
                        file++
                    }
                }
                if (file != 8) throw ChessFormatException("La rangée ${rank + 1} ne contient pas 8 cases.")
            }
            val side = when (parts.getOrNull(1) ?: "w") {
                "w" -> PieceColor.WHITE
                "b" -> PieceColor.BLACK
                else -> throw ChessFormatException("Trait invalide : « ${parts[1]} » (attendu w ou b).")
            }
            val castling = CastlingRights.fromFen(parts.getOrNull(2) ?: "-")
                ?: throw ChessFormatException("Droits au roque invalides : « ${parts[2]} ».")
            val epStr = parts.getOrNull(3) ?: "-"
            val ep = if (epStr == "-") Sq.NONE else Sq.parse(epStr).also {
                if (it < 0) throw ChessFormatException("Case de prise en passant invalide : « $epStr ».")
            }
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = (parts.getOrNull(5)?.toIntOrNull() ?: 1).coerceAtLeast(1)
            val epValid = if (ep >= 0 && Sq.rank(ep) == (if (side == PieceColor.WHITE) 5 else 2)) ep else Sq.NONE
            return Position(board, side, sanitizeCastling(board, castling), epValid, half, full)
        }

        fun fromFenOrNull(fen: String): Position? = try { fromFen(fen) } catch (e: ChessFormatException) { null }
    }
}
