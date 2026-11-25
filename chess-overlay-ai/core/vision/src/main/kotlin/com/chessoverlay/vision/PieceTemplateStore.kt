package com.chessoverlay.vision

import com.chessoverlay.chess.Piece

/** Gabarit de silhouette d'une pièce, appris à partir d'une case d'exemple. */
class PieceTemplate internal constructor(
    val piece: Piece,
    internal val silhouette: FloatArray,
    internal val width: Float,
    internal val height: Float,
    internal val area: Float,
) {
    internal fun distanceTo(o: PieceTemplate): Float =
        SquareFeatures.shapeDistance(silhouette, width, height, area, o.silhouette, o.width, o.height, o.area)
}

/**
 * Banque de gabarits de pièces. Elle s'enrichit avec les dessins de l'application, la calibration sur la
 * position initiale et les corrections de l'utilisateur ; elle se sérialise en texte pour être conservée.
 */
class PieceTemplateStore {
    private val templates = ArrayList<PieceTemplate>()

    val size: Int get() = synchronized(templates) { templates.size }

    fun hasTemplatesFor(piece: Piece): Boolean = synchronized(templates) { templates.any { it.piece == piece } }

    fun clear() = synchronized(templates) { templates.clear() }

    internal fun all(): List<PieceTemplate> = synchronized(templates) { templates.toList() }

    /** Ajoute un gabarit (ignoré s'il est quasi identique à un gabarit existant de la même pièce). */
    internal fun add(t: PieceTemplate): Boolean = synchronized(templates) {
        if (t.area <= 0f) return false
        if (templates.any { it.piece == t.piece && it.distanceTo(t) < DUPLICATE_DISTANCE }) return false
        templates.add(t)
        val same = templates.filter { it.piece == t.piece }
        if (same.size > MAX_PER_PIECE) templates.remove(same.first())
        true
    }

    fun serialize(): String = buildString {
        append(HEADER).append('\n')
        for (t in all()) {
            append(t.piece.fenChar).append(';')
            append(fmt(t.width)).append(';').append(fmt(t.height)).append(';').append(fmt(t.area)).append(';')
            for (v in t.silhouette) {
                val b = (v * 255f).toInt().coerceIn(0, 255)
                append(HEX[b shr 4]).append(HEX[b and 15])
            }
            append('\n')
        }
    }

    companion object {
        private const val HEADER = "chess-overlay-templates v1"
        private const val MAX_PER_PIECE = 40
        private const val DUPLICATE_DISTANCE = 0.015f
        private val HEX = "0123456789abcdef".toCharArray()

        private fun fmt(v: Float) = String.format(java.util.Locale.ROOT, "%.4f", v)

        /** Relit une banque sérialisée ; les lignes invalides sont ignorées. */
        fun deserialize(text: String): PieceTemplateStore {
            val store = PieceTemplateStore()
            val lines = text.lines()
            if (lines.firstOrNull()?.trim() != HEADER) return store
            val cells = SquareFeatures.GRID * SquareFeatures.GRID
            for (line in lines.drop(1)) {
                val parts = line.trim().split(';')
                if (parts.size != 5 || parts[0].length != 1) continue
                val piece = Piece.fromFenChar(parts[0][0]) ?: continue
                val w = parts[1].toFloatOrNull() ?: continue
                val h = parts[2].toFloatOrNull() ?: continue
                val a = parts[3].toFloatOrNull() ?: continue
                val hex = parts[4]
                if (hex.length != cells * 2) continue
                val sil = FloatArray(cells) { i ->
                    val v = hex.substring(2 * i, 2 * i + 2).toIntOrNull(16) ?: 0
                    v / 255f
                }
                store.templates.add(PieceTemplate(piece, sil, w, h, a))
            }
            return store
        }
    }
}
