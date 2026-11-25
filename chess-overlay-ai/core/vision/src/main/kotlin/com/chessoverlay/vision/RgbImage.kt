package com.chessoverlay.vision

/** Image en mémoire, pixels ARGB rangés ligne par ligne (format de Bitmap.getPixels sur Android). */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "Dimensions d'image invalides : ${width}x$height" }
        require(pixels.size >= width * height) { "Tableau de pixels trop petit." }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** Extrait une zone rectangulaire (bornée à l'image). */
    fun crop(left: Int, top: Int, w: Int, h: Int): RgbImage {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val cw = w.coerceAtMost(width - l).coerceAtLeast(1)
        val ch = h.coerceAtMost(height - t).coerceAtLeast(1)
        val out = IntArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(pixels, (t + y) * width + l, out, y * cw, cw)
        return RgbImage(cw, ch, out)
    }
}

/** Petits utilitaires de couleur (RVB 8 bits). */
internal object Rgb {
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    fun of(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun dist2(a: Int, b: Int): Int {
        val dr = r(a) - r(b); val dg = g(a) - g(b); val db = b(a) - b(b)
        return dr * dr + dg * dg + db * db
    }

    fun lum(c: Int): Int = (299 * r(c) + 587 * g(c) + 114 * b(c)) / 1000

    /** Couleur quantifiée sur 4 bits par canal (4096 classes). */
    fun bin(c: Int): Int = ((r(c) shr 4) shl 8) or ((g(c) shr 4) shl 4) or (b(c) shr 4)
}

/**
 * Rééchantillonne la zone carrée (x0, y0, côté [side]) de [img] en une grille n×n par moyenne de zones.
 */
internal fun sampleSquare(img: RgbImage, x0: Float, y0: Float, side: Float, n: Int): IntArray {
    val out = IntArray(n * n)
    val step = side / n
    for (j in 0 until n) {
        val sy0 = (y0 + j * step).toInt().coerceIn(0, img.height - 1)
        val sy1 = (y0 + (j + 1) * step).toInt().coerceIn(sy0 + 1, img.height)
        for (i in 0 until n) {
            val sx0 = (x0 + i * step).toInt().coerceIn(0, img.width - 1)
            val sx1 = (x0 + (i + 1) * step).toInt().coerceIn(sx0 + 1, img.width)
            var r = 0; var g = 0; var b = 0; var cnt = 0
            for (y in sy0 until sy1) {
                var idx = y * img.width + sx0
                for (x in sx0 until sx1) {
                    val c = img.pixels[idx++]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; cnt++
                }
            }
            out[j * n + i] = Rgb.of(r / cnt, g / cnt, b / cnt)
        }
    }
    return out
}
