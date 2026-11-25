package com.chessoverlay.engine

/**
 * Évaluation renvoyée par le moteur, toujours du point de vue du camp AU TRAIT dans la position analysée
 * (convention UCI). Utiliser [forWhite] pour un affichage du point de vue des Blancs.
 */
data class EngineScore(
    /** Centipions, ou null si un mat est annoncé. */
    val centipawns: Int?,
    /** Mat en N coups (positif : le camp au trait mate ; négatif : il est maté), ou null. */
    val mate: Int?,
    val lowerBound: Boolean = false,
    val upperBound: Boolean = false,
) {
    val isMate: Boolean get() = mate != null

    /** Valeur ordonnable : un mat proche vaut plus qu'un mat lointain, qui vaut plus que tout avantage. */
    val sortValue: Int
        get() = when {
            mate != null && mate > 0 -> 100_000 - mate
            mate != null -> -100_000 - mate
            else -> centipawns ?: 0
        }

    fun negate(): EngineScore = EngineScore(centipawns?.let { -it }, mate?.let { -it }, upperBound, lowerBound)

    /** Score du point de vue des Blancs, sachant le camp au trait. */
    fun forWhite(whiteToMove: Boolean): EngineScore = if (whiteToMove) this else negate()

    /** Texte court : « +1,20 », « -0,35 », « M3 », « -M2 ». */
    fun format(): String = when {
        mate != null -> if (mate > 0) "M$mate" else if (mate < 0) "-M${-mate}" else "#"
        centipawns != null -> {
            val v = centipawns / 100.0
            (if (v > 0) "+" else "") + String.format(java.util.Locale.FRANCE, "%.2f", v)
        }
        else -> "?"
    }

    /** Probabilité de gain (0..100) du camp concerné, formule utilisée par Lichess. */
    fun winPercent(): Double {
        if (mate != null) return if (mate > 0) 100.0 else 0.0
        val cp = (centipawns ?: 0).coerceIn(-1000, 1000)
        return 50 + 50 * (2 / (1 + kotlin.math.exp(-0.00368208 * cp)) - 1)
    }

    companion object {
        val ZERO = EngineScore(0, null)
    }
}

/** Une ligne « info » d'analyse (variante principale numéro [multipv]). */
data class PvLine(
    val multipv: Int,
    val depth: Int,
    val selDepth: Int,
    val score: EngineScore,
    /** Coups de la variante en notation UCI. */
    val pv: List<String>,
    val nodes: Long,
    val nps: Long,
    val timeMs: Long,
    val hashFull: Int = 0,
)

/** Résultat d'une ligne « bestmove ». */
data class BestMove(val move: String?, val ponder: String?)

/** Lecture des messages UCI émis par le moteur. */
object UciParser {

    fun parseInfo(line: String): PvLine? {
        if (!line.startsWith("info ")) return null
        val t = line.split(' ').filter { it.isNotEmpty() }
        if (!t.contains("pv") || !t.contains("score")) return null
        var depth = 0; var sel = 0; var multipv = 1; var nodes = 0L; var nps = 0L; var time = 0L; var hash = 0
        var cp: Int? = null; var mate: Int? = null; var lower = false; var upper = false
        var pv: List<String> = emptyList()
        var i = 1
        while (i < t.size) {
            when (t[i]) {
                "depth" -> { depth = t.getOrNull(i + 1)?.toIntOrNull() ?: 0; i++ }
                "seldepth" -> { sel = t.getOrNull(i + 1)?.toIntOrNull() ?: 0; i++ }
                "multipv" -> { multipv = t.getOrNull(i + 1)?.toIntOrNull() ?: 1; i++ }
                "nodes" -> { nodes = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i++ }
                "nps" -> { nps = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i++ }
                "time" -> { time = t.getOrNull(i + 1)?.toLongOrNull() ?: 0; i++ }
                "hashfull" -> { hash = t.getOrNull(i + 1)?.toIntOrNull() ?: 0; i++ }
                "score" -> {
                    when (t.getOrNull(i + 1)) {
                        "cp" -> cp = t.getOrNull(i + 2)?.toIntOrNull()
                        "mate" -> mate = t.getOrNull(i + 2)?.toIntOrNull()
                    }
                    i += 2
                    if (t.getOrNull(i + 1) == "lowerbound") { lower = true; i++ }
                    if (t.getOrNull(i + 1) == "upperbound") { upper = true; i++ }
                }
                "pv" -> { pv = t.subList(i + 1, t.size).toList(); i = t.size }
            }
            i++
        }
        if (pv.isEmpty() || (cp == null && mate == null)) return null
        return PvLine(multipv, depth, sel, EngineScore(cp, mate, lower, upper), pv, nodes, nps, time, hash)
    }

    fun parseBestMove(line: String): BestMove? {
        if (!line.startsWith("bestmove")) return null
        val t = line.split(' ').filter { it.isNotEmpty() }
        val move = t.getOrNull(1)?.takeIf { it != "(none)" && it != "0000" }
        val ponder = if (t.getOrNull(2) == "ponder") t.getOrNull(3) else null
        return BestMove(move, ponder)
    }
}

/** Limites d'une recherche (« go »). */
data class SearchLimits(
    val depth: Int? = null,
    val moveTimeMs: Long? = null,
    val nodes: Long? = null,
    val infinite: Boolean = false,
    val multiPv: Int = 1,
    /** Restreint la recherche à ces coups (UCI « searchmoves »). */
    val searchMoves: List<String> = emptyList(),
) {
    fun toGoCommand(): String = buildString {
        append("go")
        if (infinite) append(" infinite") else {
            depth?.let { append(" depth ").append(it) }
            moveTimeMs?.let { append(" movetime ").append(it) }
            nodes?.let { append(" nodes ").append(it) }
            if (depth == null && moveTimeMs == null && nodes == null) append(" depth 18")
        }
        // Stockfish lit tous les jetons qui suivent « searchmoves » comme des coups : à placer en dernier.
        if (searchMoves.isNotEmpty()) append(" searchmoves ").append(searchMoves.joinToString(" "))
    }
}

/** Options du moteur. */
data class EngineOptions(
    val threads: Int = 4,
    val hashMb: Int = 128,
    /** Niveau 0..20 (20 = pleine puissance). */
    val skillLevel: Int = 20,
    /** Si non nul, limite la force à cet Elo (1320..3190). */
    val limitElo: Int? = null,
)

/** Instantané d'analyse : les variantes connues, triées par numéro de multipv. */
data class AnalysisSnapshot(
    val fen: String,
    val lines: List<PvLine>,
    val bestMove: BestMove? = null,
    val finished: Boolean = false,
) {
    val depth: Int get() = lines.firstOrNull()?.depth ?: 0
    val best: PvLine? get() = lines.firstOrNull()
}
