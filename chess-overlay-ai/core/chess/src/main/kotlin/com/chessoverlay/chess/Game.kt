package com.chessoverlay.chess

/** État d'une partie. */
enum class GameStatus(val frenchLabel: String) {
    ONGOING("Partie en cours"),
    CHECKMATE("Échec et mat"),
    STALEMATE("Pat"),
    INSUFFICIENT_MATERIAL("Nulle : matériel insuffisant"),
    FIFTY_MOVES("Nulle : règle des 50 coups"),
    THREEFOLD_REPETITION("Nulle : triple répétition"),
}

/**
 * Partie immuable : position de départ + coups joués. Chaque appel à [play] renvoie une nouvelle partie.
 */
class Game private constructor(
    val initial: Position,
    val moves: List<Move>,
    val positions: List<Position>,
    val sans: List<String>,
    val tags: Map<String, String>,
) {
    val current: Position get() = positions.last()
    val ply: Int get() = moves.size

    fun play(move: Move): Game {
        val pos = current
        val legal = pos.normalize(move) ?: throw IllegalArgumentException("Coup illégal : ${move.uci}")
        val san = San.toSan(pos, legal)
        return Game(initial, moves + legal, positions + pos.play(legal), sans + san, tags)
    }

    fun playSan(san: String): Game {
        val m = San.parse(current, san) ?: throw ChessFormatException("Coup invalide ou illégal : « $san ».")
        return play(m)
    }

    /** Partie tronquée aux [ply] premiers demi-coups (pour annuler ou naviguer). */
    fun truncated(ply: Int): Game {
        val n = ply.coerceIn(0, moves.size)
        return Game(initial, moves.take(n), positions.take(n + 1), sans.take(n), tags)
    }

    fun withTags(newTags: Map<String, String>): Game = Game(initial, moves, positions, sans, newTags)

    val status: GameStatus
        get() {
            val p = current
            return when {
                p.isCheckmate -> GameStatus.CHECKMATE
                p.isStalemate -> GameStatus.STALEMATE
                p.isInsufficientMaterial -> GameStatus.INSUFFICIENT_MATERIAL
                p.halfmoveClock >= 100 -> GameStatus.FIFTY_MOVES
                positions.count { it.repetitionKey == p.repetitionKey } >= 3 -> GameStatus.THREEFOLD_REPETITION
                else -> GameStatus.ONGOING
            }
        }

    /** Résultat PGN ("1-0", "0-1", "1/2-1/2" ou "*"). */
    val result: String
        get() = when (status) {
            GameStatus.ONGOING -> tags["Result"] ?: "*"
            GameStatus.CHECKMATE -> if (current.sideToMove == PieceColor.WHITE) "0-1" else "1-0"
            else -> "1/2-1/2"
        }

    /** Coups au format UCI (pour la commande « position ... moves ... »). */
    val uciMoves: List<String> get() = moves.map { it.uci }

    fun toPgn(): String = Pgn.write(this)

    companion object {
        fun new(initial: Position = Position.initial(), tags: Map<String, String> = emptyMap()): Game =
            Game(initial, emptyList(), listOf(initial), emptyList(), tags)

        fun fromUci(initial: Position, uciMoves: List<String>): Game {
            var g = new(initial)
            for (u in uciMoves) {
                val m = Move.fromUci(u) ?: throw ChessFormatException("Coup UCI invalide : $u")
                g = g.play(m)
            }
            return g
        }
    }
}
