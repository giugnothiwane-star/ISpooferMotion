package com.chessoverlay.vision

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Échiquier détecté : cadre et couleurs des deux types de cases. */
internal data class DetectedBoard(val rect: BoardRect, val colorA: Int, val colorB: Int, val checkerScore: Float)

/**
 * Localise un échiquier numérique (aligné sur les axes) dans une capture d'écran :
 * 1. sous-échantillonnage et histogramme des couleurs ;
 * 2. pour chaque paire de couleurs dominantes, plus grande composante connexe de pixels de ces couleurs ;
 * 3. validation de l'alternance en damier sur une grille 8×8 ;
 * 4. affinage des bords en pleine résolution.
 */
internal object BoardDetector {
    private const val MAX_SIDE = 512
    private const val COLOR_TOL2 = 28 * 28

    fun detect(img: RgbImage): DetectedBoard? {
        val step = max(1, ceil(max(img.width, img.height) / MAX_SIDE.toDouble()).toInt())
        val w = img.width / step
        val h = img.height / step
        if (w < 16 || h < 16) return null
        val small = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0 until step) {
                var idx = (y * step + dy) * img.width + x * step
                for (dx in 0 until step) {
                    val c = img.pixels[idx++]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
            }
            val n = step * step
            small[y * w + x] = Rgb.of(r / n, g / n, b / n)
        }
        val colors = dominantColors(small, 10)
        var best: DetectedBoard? = null
        var bestScore = 0f
        val labels = IntArray(w * h)
        for (i in colors.indices) for (j in i + 1 until colors.size) {
            val a = colors[i]; val b = colors[j]
            if (Rgb.dist2(a, b) < 25 * 25) continue
            val cand = evaluatePair(small, w, h, a, b, labels) ?: continue
            val (bx, by, bs, checker) = cand
            val score = checker * sqrt(bs.toFloat())
            if (score > bestScore) {
                bestScore = score
                best = DetectedBoard(BoardRect(bx * step.toFloat(), by * step.toFloat(), bs * step.toFloat()), a, b, checker)
            }
        }
        val coarse = best ?: return null
        return refine(img, coarse, step)
    }

    private data class Candidate(val x: Int, val y: Int, val size: Int, val checker: Float)

    /** Couleurs moyennes des classes les plus fréquentes (classes voisines fusionnées). */
    private fun dominantColors(px: IntArray, k: Int): List<Int> {
        val counts = IntArray(4096)
        val sr = LongArray(4096); val sg = LongArray(4096); val sb = LongArray(4096)
        for (c in px) {
            val bin = Rgb.bin(c)
            counts[bin]++; sr[bin] += Rgb.r(c).toLong(); sg[bin] += Rgb.g(c).toLong(); sb[bin] += Rgb.b(c).toLong()
        }
        val order = counts.indices.filter { counts[it] >= px.size / 200 }.sortedByDescending { counts[it] }
        val result = ArrayList<Pair<Int, Int>>() // couleur, effectif
        for (bin in order) {
            val n = counts[bin]
            val c = Rgb.of((sr[bin] / n).toInt(), (sg[bin] / n).toInt(), (sb[bin] / n).toInt())
            val idx = result.indexOfFirst { Rgb.dist2(it.first, c) < 18 * 18 }
            if (idx >= 0) result[idx] = result[idx].first to result[idx].second + n
            else result.add(c to n)
            if (result.size >= k) break
        }
        return result.sortedByDescending { it.second }.map { it.first }
    }

    private fun evaluatePair(px: IntArray, w: Int, h: Int, a: Int, b: Int, labels: IntArray): Candidate? {
        for (i in px.indices) {
            val c = px[i]
            labels[i] = when {
                Rgb.dist2(c, a) <= COLOR_TOL2 -> 1
                Rgb.dist2(c, b) <= COLOR_TOL2 -> 2
                // Mélange des deux couleurs (bord de case sous-échantillonné) : relie les cases entre elles.
                isBlend(c, a, b) -> 3
                else -> 0
            }
        }
        // Plus grande composante connexe (4-connexité) de pixels des deux couleurs.
        val seen = BooleanArray(px.size)
        val stack = IntArray(px.size)
        var bestCount = 0; var bx0 = 0; var by0 = 0; var bx1 = 0; var by1 = 0
        for (start in px.indices) {
            if (labels[start] == 0 || seen[start]) continue
            var sp = 0; var count = 0
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            stack[sp++] = start; seen[start] = true
            while (sp > 0) {
                val p = stack[--sp]
                count++
                val x = p % w; val y = p / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x > 0) { val q = p - 1; if (labels[q] != 0 && !seen[q]) { seen[q] = true; stack[sp++] = q } }
                if (x < w - 1) { val q = p + 1; if (labels[q] != 0 && !seen[q]) { seen[q] = true; stack[sp++] = q } }
                if (y > 0) { val q = p - w; if (labels[q] != 0 && !seen[q]) { seen[q] = true; stack[sp++] = q } }
                if (y < h - 1) { val q = p + w; if (labels[q] != 0 && !seen[q]) { seen[q] = true; stack[sp++] = q } }
            }
            if (count > bestCount) { bestCount = count; bx0 = x0; by0 = y0; bx1 = x1; by1 = y1 }
        }
        val bw = bx1 - bx0 + 1; val bh = by1 - by0 + 1
        if (bw < 16 || bh < 16) return null
        if (min(bw, bh) < 0.12f * min(w, h)) return null
        val aspect = bw.toFloat() / bh
        if (aspect < 0.8f || aspect > 1.25f) return null
        if (bestCount < 0.3f * bw * bh) return null
        // Vérification du damier.
        var match0 = 0; var match1 = 0; var cellsA = 0; var cellsB = 0
        for (row in 0 until 8) for (col in 0 until 8) {
            val cx0 = bx0 + col * bw / 8; val cx1 = bx0 + (col + 1) * bw / 8
            val cy0 = by0 + row * bh / 8; val cy1 = by0 + (row + 1) * bh / 8
            var na = 0; var nb = 0; var total = 0
            for (y in cy0 until cy1) for (x in cx0 until cx1) {
                total++
                when (labels[y * w + x]) { 1 -> na++; 2 -> nb++ }
            }
            val label = when {
                total == 0 -> 0
                na > nb && na > 0.2f * total -> 1
                nb > na && nb > 0.2f * total -> 2
                else -> 0
            }
            if (label == 1) cellsA++
            if (label == 2) cellsB++
            val even = (row + col) % 2 == 0
            if ((even && label == 1) || (!even && label == 2)) match0++
            if ((even && label == 2) || (!even && label == 1)) match1++
        }
        if (cellsA < 8 || cellsB < 8) return null
        val checker = max(match0, match1) / 64f
        if (checker < 0.7f) return null
        return Candidate(bx0, by0, (bw + bh) / 2, checker)
    }

    /** Vrai si [c] est proche du segment [a, b] dans l'espace RVB (mélange des deux couleurs). */
    private fun isBlend(c: Int, a: Int, b: Int): Boolean {
        val ar = Rgb.r(a).toFloat(); val ag = Rgb.g(a).toFloat(); val ab = Rgb.b(a).toFloat()
        val dr = Rgb.r(b) - ar; val dg = Rgb.g(b) - ag; val db = Rgb.b(b) - ab
        val len2 = dr * dr + dg * dg + db * db
        if (len2 == 0f) return false
        val cr = Rgb.r(c) - ar; val cg = Rgb.g(c) - ag; val cb = Rgb.b(c) - ab
        val t = (cr * dr + cg * dg + cb * db) / len2
        if (t < 0f || t > 1f) return false
        val er = cr - t * dr; val eg = cg - t * dg; val eb = cb - t * db
        return er * er + eg * eg + eb * eb <= 12f * 12f
    }

    /** Affine les bords en pleine résolution à partir du cadre grossier. */
    private fun refine(img: RgbImage, coarse: DetectedBoard, step: Int): DetectedBoard {
        val r = coarse.rect
        val margin = 2 * step + 2
        val wx0 = (r.left - margin).toInt().coerceAtLeast(0)
        val wy0 = (r.top - margin).toInt().coerceAtLeast(0)
        val wx1 = (r.left + r.size + margin + step).toInt().coerceAtMost(img.width - 1)
        val wy1 = (r.top + r.size + margin + step).toInt().coerceAtMost(img.height - 1)
        val a = coarse.colorA; val b = coarse.colorB
        fun isBoard(c: Int) = Rgb.dist2(c, a) <= COLOR_TOL2 || Rgb.dist2(c, b) <= COLOR_TOL2
        val iy0 = (r.top + step).toInt().coerceIn(wy0, wy1); val iy1 = (r.top + r.size - step).toInt().coerceIn(wy0, wy1)
        val ix0 = (r.left + step).toInt().coerceIn(wx0, wx1); val ix1 = (r.left + r.size - step).toInt().coerceIn(wx0, wx1)
        val rowStride = max(1, (iy1 - iy0) / 200)
        val colStride = max(1, (ix1 - ix0) / 200)
        val colCount = IntArray(wx1 - wx0 + 1)
        var rowsSampled = 0
        var y = iy0
        while (y <= iy1) {
            rowsSampled++
            val base = y * img.width
            for (x in wx0..wx1) if (isBoard(img.pixels[base + x])) colCount[x - wx0]++
            y += rowStride
        }
        val rowCount = IntArray(wy1 - wy0 + 1)
        var colsSampled = 0
        var x = ix0
        while (x <= ix1) {
            colsSampled++
            for (yy in wy0..wy1) if (isBoard(img.pixels[yy * img.width + x])) rowCount[yy - wy0]++
            x += colStride
        }
        val colThr = 0.3f * rowsSampled; val rowThr = 0.3f * colsSampled
        val left = colCount.indexOfFirst { it >= colThr }
        val right = colCount.indexOfLast { it >= colThr }
        val top = rowCount.indexOfFirst { it >= rowThr }
        val bottom = rowCount.indexOfLast { it >= rowThr }
        if (left < 0 || right <= left || top < 0 || bottom <= top) return coarse
        val bw = right - left + 1; val bh = bottom - top + 1
        val size = (bw + bh) / 2f
        if (size < r.size * 0.8f || size > r.size * 1.2f) return coarse
        return coarse.copy(rect = BoardRect((wx0 + left).toFloat(), (wy0 + top).toFloat(), size))
    }
}
