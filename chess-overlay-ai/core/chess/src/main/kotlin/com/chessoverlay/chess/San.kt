package com.chessoverlay.chess

/** Conversion entre coups et notation algébrique abrégée (SAN). */
object San {

    /**
     * Notation algébrique d'un coup légal de [pos].
     * @param french true pour la notation française (Cf3, Dxd5, Txe1+), false pour la notation PGN (Nf3).
     */
    fun toSan(pos: Position, move: Move, french: Boolean = false): String {
        val piece = pos.pieceAt(move.from) ?: return move.uci
        val sb = StringBuilder()
        if (pos.isCastling(move)) {
            sb.append(if (Sq.file(move.to) == 6) "O-O" else "O-O-O")
        } else {
            val capture = pos.isCapture(move)
            if (piece.type == PieceType.PAWN) {
                if (capture) sb.append('a' + Sq.file(move.from)).append('x')
                sb.append(Sq.name(move.to))
                if (move.promotion != null) {
                    sb.append('=').append(if (french) move.promotion.frenchLetter else move.promotion.sanLetter)
                }
            } else {
                sb.append(if (french) piece.type.frenchLetter else piece.type.sanLetter)
                // Désambiguïsation : autres pièces identiques pouvant aller sur la même case.
                val rivals = pos.legalMoves().filter {
                    it.to == move.to && it.from != move.from && pos.pieceAt(it.from) == piece
                }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { Sq.file(it.from) == Sq.file(move.from) }
                    val sameRank = rivals.any { Sq.rank(it.from) == Sq.rank(move.from) }
                    when {
                        !sameFile -> sb.append('a' + Sq.file(move.from))
                        !sameRank -> sb.append('1' + Sq.rank(move.from))
                        else -> sb.append(Sq.name(move.from))
                    }
                }
                if (capture) sb.append('x')
                sb.append(Sq.name(move.to))
            }
        }
        val next = pos.playOrNull(move) ?: return sb.toString()
        if (next.isCheckmate) sb.append('#') else if (next.isCheck) sb.append('+')
        return sb.toString()
    }

    /** Convertit une suite de coups UCI en SAN à partir de [pos]. S'arrête au premier coup illégal. */
    fun uciLineToSan(pos: Position, uciMoves: List<String>, french: Boolean = false): List<String> {
        val out = ArrayList<String>()
        var p = pos
        for (u in uciMoves) {
            val m = Move.fromUci(u)?.let { p.normalize(it) } ?: break
            out.add(toSan(p, m, french))
            p = p.play(m)
        }
        return out
    }

    private val sanRegex = Regex("^([NBRQKCFTD])?([a-h])?([1-8])?(x|:)?([a-h][1-8])(=?([NBRQCFTD]))?$")

    /** Lit un coup en notation SAN (PGN ou française) et renvoie le coup légal correspondant, ou null. */
    fun parse(pos: Position, input: String): Move? {
        var s = input.trim()
            .replace("e.p.", "").replace("ep", "")
            .trimEnd('+', '#', '!', '?')
            .replace("0", "O")
        if (s.isEmpty()) return null
        val legal = pos.legalMoves()
        if (s == "O-O" || s == "O-O-O") {
            val long = s == "O-O-O"
            return legal.firstOrNull { pos.isCastling(it) && (Sq.file(it.to) == 2) == long }
        }
        s = s.replace("-", "")
        // Coup UCI ou notation longue (e2e4, e2-e4) acceptés par commodité.
        Move.fromUci(s)?.let { m -> pos.normalize(m)?.let { return it } }
        val match = sanRegex.matchEntire(s) ?: return null
        val pieceLetter = match.groupValues[1]
        val fromFile = match.groupValues[2].firstOrNull()?.let { it - 'a' }
        val fromRank = match.groupValues[3].firstOrNull()?.let { it - '1' }
        val to = Sq.parse(match.groupValues[5])
        val promoLetter = match.groupValues[7]
        val type = when (pieceLetter) {
            "" -> PieceType.PAWN
            "N", "C" -> PieceType.KNIGHT
            "B", "F" -> PieceType.BISHOP
            "R" -> if (isFrenchKing(pos, fromFile, fromRank, to)) PieceType.KING else PieceType.ROOK
            "T" -> PieceType.ROOK
            "Q", "D" -> PieceType.QUEEN
            "K" -> PieceType.KING
            else -> return null
        }
        val promo = when (promoLetter) {
            "" -> null
            "N", "C" -> PieceType.KNIGHT
            "B", "F" -> PieceType.BISHOP
            "R", "T" -> PieceType.ROOK
            "Q", "D" -> PieceType.QUEEN
            else -> return null
        }
        val candidates = legal.filter { m ->
            val p = pos.pieceAt(m.from)
            m.to == to && p?.type == type &&
                (fromFile == null || Sq.file(m.from) == fromFile) &&
                (fromRank == null || Sq.rank(m.from) == fromRank) &&
                (if (type == PieceType.PAWN) m.promotion == (promo ?: if (Sq.rank(to) == 0 || Sq.rank(to) == 7) PieceType.QUEEN else null) else true)
        }
        return candidates.singleOrNull()
    }

    /** En notation française « R » désigne le roi ; en PGN, la tour. On choisit l'interprétation légale. */
    private fun isFrenchKing(pos: Position, fromFile: Int?, fromRank: Int?, to: Int): Boolean {
        val rookOk = pos.legalMoves().any {
            it.to == to && pos.pieceAt(it.from)?.type == PieceType.ROOK &&
                (fromFile == null || Sq.file(it.from) == fromFile) && (fromRank == null || Sq.rank(it.from) == fromRank)
        }
        if (rookOk) return false
        return pos.legalMoves().any { it.to == to && pos.pieceAt(it.from)?.type == PieceType.KING }
    }
}
