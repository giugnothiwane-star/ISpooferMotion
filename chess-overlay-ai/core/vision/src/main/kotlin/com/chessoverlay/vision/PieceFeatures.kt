package com.chessoverlay.vision

import kotlin.math.abs
import kotlin.math.max

/**
 * Description d'une case : fond estimé, masque de la pièce et silhouette normalisée.
 * Toutes les mesures sont relatives à la taille de la case, donc indépendantes de la résolution.
 */
internal class SquareFeatures(
    /** Couleur de fond estimée de la case. */
    val background: Int,
    /** Part de la case couverte par des pixels différents du fond (hors coins). */
    val occupancy: Float,
    /**
     * Indice de blancheur de la pièce (0..255) : part des zones claires « épaisses » (érodées) parmi les
     * zones claires et sombres épaisses. Les pièces blanches ont un remplissage clair, les noires seulement
     * de fins reflets clairs ; les contours sombres fins disparaissent à l'érosion.
     */
    val meanLum: Float,
    /** Silhouette (trous bouchés) recadrée et normalisée sur une grille GRID×GRID, valeurs 0..1. */
    val silhouette: FloatArray,
    /** Largeur et hauteur de la silhouette, en fraction de la case. */
    val width: Float,
    val height: Float,
    /** Surface de la silhouette, en fraction de la case. */
    val area: Float,
) {
    companion object {
        const val N = 48
        const val GRID = 16
        // Poids réglés par validation sur des jeux de pièces absents des gabarits (voir RealWorldDiagTest).
        internal var topWeight = 1.4f
        internal var bottomWeight = 0.4f
        internal var aspectWeight = 0.6f
        internal var fillWeight = 0f
        internal var relHeightWeight = 0.7f
        private const val DIFF_THRESHOLD2 = 40 * 40
        private const val CORNER = 8 // ≈ 17 % de la case : zone des coordonnées a–h / 1–8
        private const val EDGE = 2

        /** Estime la couleur de fond par le mode des pixels d'une couronne intérieure. */
        fun estimateBackground(px: IntArray, n: Int = N): Int {
            val counts = IntArray(4096)
            val ring = ArrayList<Int>(n * 20)
            for (y in 1 until n - 1) for (x in 1 until n - 1) {
                val inRing = x <= 5 || y <= 5 || x >= n - 6 || y >= n - 6
                if (!inRing) continue
                val c = px[y * n + x]
                counts[Rgb.bin(c)]++
                ring.add(c)
            }
            var best = 0
            for (i in counts.indices) if (counts[i] > counts[best]) best = i
            var r = 0L; var g = 0L; var b = 0L; var cnt = 0
            for (c in ring) if (Rgb.bin(c) == best) { r += Rgb.r(c); g += Rgb.g(c); b += Rgb.b(c); cnt++ }
            val mean = Rgb.of((r / cnt).toInt(), (g / cnt).toInt(), (b / cnt).toInt())
            // Affinage : moyenne des pixels proches de la couleur dominante.
            r = 0; g = 0; b = 0; cnt = 0
            for (c in ring) if (Rgb.dist2(c, mean) <= 20 * 20) { r += Rgb.r(c); g += Rgb.g(c); b += Rgb.b(c); cnt++ }
            return if (cnt == 0) mean else Rgb.of((r / cnt).toInt(), (g / cnt).toInt(), (b / cnt).toInt())
        }

        fun extract(px: IntArray, background: Int = estimateBackground(px), n: Int = N): SquareFeatures {
            val mask = BooleanArray(n * n)
            for (y in 0 until n) for (x in 0 until n) {
                val corner = (x < CORNER || x >= n - CORNER) && (y < CORNER || y >= n - CORNER)
                // Marge : la jointure entre cases (mélange avec la case voisine) n'appartient pas à la pièce.
                val edge = x < EDGE || y < EDGE || x >= n - EDGE || y >= n - EDGE
                if (!corner && !edge && Rgb.dist2(px[y * n + x], background) > DIFF_THRESHOLD2) mask[y * n + x] = true
            }
            removeSmallComponents(mask, n, 12)
            var count = 0
            for (i in mask.indices) if (mask[i]) count++
            val occupancy = count.toFloat() / (n * n)

            // Bouchage des trous : tout ce qui n'est pas relié au bord par du fond appartient à la pièce.
            val outside = BooleanArray(n * n)
            val stack = IntArray(n * n); var sp = 0
            for (i in 0 until n) for (p in intArrayOf(i, (n - 1) * n + i, i * n, i * n + n - 1)) {
                if (!mask[p] && !outside[p]) { outside[p] = true; stack[sp++] = p }
            }
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % n; val y = p / n
                if (x > 0) { val q = p - 1; if (!mask[q] && !outside[q]) { outside[q] = true; stack[sp++] = q } }
                if (x < n - 1) { val q = p + 1; if (!mask[q] && !outside[q]) { outside[q] = true; stack[sp++] = q } }
                if (y > 0) { val q = p - n; if (!mask[q] && !outside[q]) { outside[q] = true; stack[sp++] = q } }
                if (y < n - 1) { val q = p + n; if (!mask[q] && !outside[q]) { outside[q] = true; stack[sp++] = q } }
            }
            var x0 = n; var y0 = n; var x1 = -1; var y1 = -1; var filled = 0
            for (y in 0 until n) for (x in 0 until n) if (!outside[y * n + x]) {
                filled++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
            // Blancheur calculée sur la silhouette pleine : les parties du remplissage proches du fond
            // (dégradés gris sur case claire) restent comptées dans la pièce.
            val bright = BooleanArray(n * n); val dark = BooleanArray(n * n)
            for (i in 0 until n * n) if (!outside[i]) {
                val l = Rgb.lum(px[i])
                if (l >= 140) bright[i] = true else if (l < 90) dark[i] = true
            }
            val eb = erodedCount(bright, n); val ed = erodedCount(dark, n)
            val meanLum = 255f * (eb + 1) / (eb + ed + 2)
            val sil = FloatArray(GRID * GRID)
            if (filled == 0) return SquareFeatures(background, occupancy, meanLum, sil, 0f, 0f, 0f)
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            val side = max(bw, bh)
            val offX = (side - bw) / 2f; val offY = (side - bh) / 2f
            val cellCounts = FloatArray(GRID * GRID)
            for (y in y0..y1) for (x in x0..x1) if (!outside[y * n + x]) {
                val gx = (((x - x0) + offX) * GRID / side).toInt().coerceIn(0, GRID - 1)
                val gy = (((y - y0) + offY) * GRID / side).toInt().coerceIn(0, GRID - 1)
                cellCounts[gy * GRID + gx] += 1f
            }
            val cellArea = (side.toFloat() / GRID).let { it * it }
            for (i in sil.indices) sil[i] = (cellCounts[i] / cellArea).coerceAtMost(1f)
            return SquareFeatures(
                background, occupancy, meanLum, sil,
                bw.toFloat() / n, bh.toFloat() / n, filled.toFloat() / (n * n),
            )
        }

        /** Nombre de pixels dont tout le voisinage 3×3 appartient à l'ensemble (érosion). */
        private fun erodedCount(set: BooleanArray, n: Int): Int {
            var c = 0
            for (y in 1 until n - 1) for (x in 1 until n - 1) {
                var all = true
                loop@ for (dy in -1..1) for (dx in -1..1) if (!set[(y + dy) * n + x + dx]) { all = false; break@loop }
                if (all) c++
            }
            return c
        }

        private fun removeSmallComponents(mask: BooleanArray, n: Int, minSize: Int) {
            val seen = BooleanArray(mask.size)
            val stack = IntArray(mask.size)
            val comp = IntArray(mask.size)
            for (start in mask.indices) {
                if (!mask[start] || seen[start]) continue
                var sp = 0; var size = 0
                stack[sp++] = start; seen[start] = true
                while (sp > 0) {
                    val p = stack[--sp]
                    comp[size++] = p
                    val x = p % n; val y = p / n
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue
                        val q = ny * n + nx
                        if (mask[q] && !seen[q]) { seen[q] = true; stack[sp++] = q }
                    }
                }
                if (size < minSize) for (i in 0 until size) mask[comp[i]] = false
            }
        }

        /** Distance de forme entre deux silhouettes (0 = identiques). */
        fun shapeDistance(
            silA: FloatArray, wA: Float, hA: Float, areaA: Float,
            silB: FloatArray, wB: Float, hB: Float, areaB: Float,
        ): Float {
            var d = 0f
            for (i in silA.indices) d += abs(silA[i] - silB[i])
            d /= silA.size
            return d + abs(wA - wB) + abs(hA - hB) + abs(areaA - areaB)
        }
    }

    fun distanceTo(t: PieceTemplate): Float =
        shapeDistance(silhouette, width, height, area, t.silhouette, t.width, t.height, t.area)

    /**
     * Distance indépendante de l'échelle du jeu de pièces : silhouette normalisée, proportions,
     * taux de remplissage et hauteur relative à la plus grande pièce de l'échiquier ([relHeight]).
     * Utilisée pour reconnaître des jeux de pièces différents de ceux des gabarits.
     */
    fun scaleFreeDistanceTo(t: PieceTemplate, relHeight: Float): Float {
        // Le haut de la pièce (croix, couronne, mitre, tête de cheval, créneaux) distingue les types ;
        // le socle, propre à chaque jeu de pièces, compte moins.
        var d = 0f
        for (i in silhouette.indices) d += (if (i / GRID < GRID * 6 / 10) topWeight else bottomWeight) * abs(silhouette[i] - t.silhouette[i])
        d /= silhouette.size
        if (height > 0f && t.height > 0f) {
            d += aspectWeight * abs(width / height - t.width / t.height)
            d += fillWeight * abs(area / (width * height) - t.area / (t.width * t.height))
        }
        if (!relHeight.isNaN() && !t.relHeight.isNaN()) d += relHeightWeight * abs(relHeight - t.relHeight)
        return d
    }
}
