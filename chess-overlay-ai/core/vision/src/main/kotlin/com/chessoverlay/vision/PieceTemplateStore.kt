package com.chessoverlay.vision

import com.chessoverlay.chess.Piece

/** Gabarit de silhouette d'une pièce, appris à partir d'une case d'exemple. */
class PieceTemplate internal constructor(
    val piece: Piece,
    internal val silhouette: FloatArray,
    internal val width: Float,
    internal val height: Float,
    internal val area: Float,
    /**
     * Hauteur relative à la plus grande pièce du même jeu (en pratique le roi), ou NaN si inconnue.
     * Elle rend la comparaison indépendante de la taille des pièces propre à chaque jeu.
     */
    internal val relHeight: Float = Float.NaN,
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
            append(if (t.relHeight.isNaN()) "-" else fmt(t.relHeight)).append(';')
            for (v in t.silhouette) {
                val b = (v * 255f).toInt().coerceIn(0, 255)
                append(HEX[b shr 4]).append(HEX[b and 15])
            }
            append('\n')
        }
    }

    companion object {
        private const val HEADER = "chess-overlay-templates v2"
        private const val HEADER_V1 = "chess-overlay-templates v1"
        private const val MAX_PER_PIECE = 120
        private const val DUPLICATE_DISTANCE = 0.015f
        private val HEX = "0123456789abcdef".toCharArray()

        private fun fmt(v: Float) = String.format(java.util.Locale.ROOT, "%.4f", v)

        /**
         * Banque par défaut livrée avec l'application : silhouettes de 7 jeux de pièces sous licence libre
         * (Cburnett, Merida, Chessnut, MPChess, Fantasy, Spatial, RhosGFX) sur 8 couleurs de cases.
         * Seules des mesures numériques sont incluses, pas les dessins.
         */
        fun loadDefault(): PieceTemplateStore {
            val text = PieceTemplateStore::class.java.getResourceAsStream("/vision/default_templates.txt")
                ?.bufferedReader()?.use { it.readText() } ?: return PieceTemplateStore()
            return deserialize(text)
        }

        /** Relit une banque sérialisée ; les lignes invalides sont ignorées. */
        fun deserialize(text: String): PieceTemplateStore {
            val store = PieceTemplateStore()
            val lines = text.lines()
            val head = lines.firstOrNull()?.trim()
            val v2 = head == HEADER
            if (!v2 && head != HEADER_V1) return store
            val cells = SquareFeatures.GRID * SquareFeatures.GRID
            for (line in lines.drop(1)) {
                val parts = line.trim().split(';')
                if (parts.size != (if (v2) 6 else 5) || parts[0].length != 1) continue
                val piece = Piece.fromFenChar(parts[0][0]) ?: continue
                val w = parts[1].toFloatOrNull() ?: continue
                val h = parts[2].toFloatOrNull() ?: continue
                val a = parts[3].toFloatOrNull() ?: continue
                val rel = if (v2) parts[4].toFloatOrNull() ?: Float.NaN else Float.NaN
                val hex = parts.last()
                if (hex.length != cells * 2) continue
                val sil = FloatArray(cells) { i ->
                    val v = hex.substring(2 * i, 2 * i + 2).toIntOrNull(16) ?: 0
                    v / 255f
                }
                store.templates.add(PieceTemplate(piece, sil, w, h, a, rel))
            }
            return store
        }
    }
}
