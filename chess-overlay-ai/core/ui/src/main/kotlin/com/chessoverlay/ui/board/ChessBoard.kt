package com.chessoverlay.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import com.chessoverlay.ui.theme.Palette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Flèche dessinée sur l'échiquier (meilleur coup, variantes). [widthFraction] : épaisseur relative à une case. */
data class BoardArrow(val from: Int, val to: Int, val color: Color, val widthFraction: Float = 0.17f)

enum class MarkStyle { FILL, RING }

/** Marquage d'une case (case de départ / d'arrivée recommandées, case douteuse après reconnaissance…). */
data class SquareMark(val square: Int, val color: Color, val style: MarkStyle = MarkStyle.FILL)

/**
 * Échiquier haut de gamme : sélection au toucher, glisser-déposer, cases de destination, animation
 * des coups, flèches, dernier coup, échec, coordonnées, rotation et choix de promotion.
 */
@Composable
fun ChessBoard(
    position: Position,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    theme: BoardTheme = BoardTheme.GRAPHITE,
    pieceSet: PieceSet = PieceSet.CBURNETT,
    interactive: Boolean = false,
    /** Couleur que l'utilisateur peut déplacer (null = camp au trait). */
    movableColor: PieceColor? = null,
    onMove: (Move) -> Unit = {},
    lastMove: Move? = null,
    arrows: List<BoardArrow> = emptyList(),
    marks: List<SquareMark> = emptyList(),
    showCoordinates: Boolean = true,
    animate: Boolean = true,
    /** Mode éditeur : le toucher d'une case est transmis tel quel et aucune règle n'est appliquée. */
    onSquareClick: ((Int) -> Unit)? = null,
) {
    val painters = HashMap<Piece, Painter>(12)
    for (c in PieceColor.entries) for (t in PieceType.entries) {
        val p = Piece.of(t, c)
        painters[p] = painterResource(pieceSet.drawable(p))
    }
    val textMeasurer = rememberTextMeasurer()
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(position) { mutableStateOf<Int?>(null) }
    var dragPos by remember(position) { mutableStateOf<Offset?>(null) }
    var pendingPromotion by remember(position) { mutableStateOf<Pair<Int, Int>?>(null) }
    var skipAnimationFor by remember { mutableStateOf<Move?>(null) }

    val progress = remember { Animatable(1f) }
    LaunchedEffect(position.toFen(), lastMove) {
        if (animate && lastMove != null && lastMove != skipAnimationFor && position.pieceAt(lastMove.to) != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
        } else progress.snapTo(1f)
    }

    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSquare by rememberUpdatedState(onSquareClick)
    val canMove = interactive && onSquareClick == null
    val mover = movableColor ?: position.sideToMove

    fun tryMove(from: Int, to: Int, fromDrag: Boolean): Boolean {
        val candidates = position.legalMoves().filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return false
        if (candidates.any { it.promotion != null }) {
            pendingPromotion = from to to
            return true
        }
        val m = candidates.first()
        if (fromDrag) skipAnimationFor = m
        selected = null
        currentOnMove(m)
        return true
    }

    BoxWithConstraints(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
        val density = LocalDensity.current
        val boardPx = with(density) { maxWidth.toPx() }
        val sq = boardPx / 8f

        fun squareAt(o: Offset): Int {
            val col = (o.x / sq).toInt().coerceIn(0, 7)
            val row = (o.y / sq).toInt().coerceIn(0, 7)
            return if (flipped) Sq.of(7 - col, row) else Sq.of(col, 7 - row)
        }
        fun topLeft(s: Int): Offset {
            val col = if (flipped) 7 - Sq.file(s) else Sq.file(s)
            val row = if (flipped) Sq.rank(s) else 7 - Sq.rank(s)
            return Offset(col * sq, row * sq)
        }
        fun center(s: Int): Offset = topLeft(s) + Offset(sq / 2, sq / 2)

        Canvas(
            Modifier.fillMaxSize().pointerInput(position, flipped, canMove, mover, onSquareClick != null) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startSq = squareAt(down.position)
                    if (currentOnSquare != null) {
                        down.consume()
                        currentOnSquare?.invoke(startSq)
                        return@awaitEachGesture
                    }
                    if (!canMove) return@awaitEachGesture
                    if (pendingPromotion != null) { pendingPromotion = null; return@awaitEachGesture }
                    val p = position.pieceAt(startSq)
                    val ownPiece = p != null && p.color == mover && p.color == position.sideToMove
                    // Toucher d'une case de destination de la pièce sélectionnée.
                    val sel = selected
                    if (sel != null && !ownPiece) {
                        if (!tryMove(sel, startSq, fromDrag = false)) selected = null
                        down.consume()
                        return@awaitEachGesture
                    }
                    if (!ownPiece) { selected = null; return@awaitEachGesture }
                    val wasSelected = selected == startSq
                    selected = startSq
                    dragFrom = startSq
                    var moved = false
                    var lastPos = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { lastPos = change.position; break }
                        if (change.positionChange() != Offset.Zero) {
                            lastPos = change.position
                            if ((lastPos - down.position).getDistance() > sq * 0.15f) moved = true
                            if (moved) dragPos = lastPos
                            change.consume()
                        }
                    }
                    dragFrom = null
                    dragPos = null
                    if (moved) {
                        val target = squareAt(lastPos)
                        if (target != startSq && !tryMove(startSq, target, fromDrag = true)) selected = startSq
                    } else if (wasSelected) {
                        selected = null
                    }
                }
            },
        ) {
            // Cases
            for (s in 0 until 64) {
                val color = if (Sq.isLight(s)) theme.light else theme.dark
                drawRect(color, topLeft(s), Size(sq, sq))
            }
            // Dernier coup
            lastMove?.let { m ->
                for (s in intArrayOf(m.from, m.to)) drawRect(Palette.Violet.copy(alpha = 0.30f), topLeft(s), Size(sq, sq))
            }
            // Marquages
            for (mk in marks) {
                when (mk.style) {
                    MarkStyle.FILL -> drawRect(mk.color.copy(alpha = mk.color.alpha * 0.45f), topLeft(mk.square), Size(sq, sq))
                    MarkStyle.RING -> drawRect(mk.color, topLeft(mk.square) + Offset(sq * 0.04f, sq * 0.04f), Size(sq * 0.92f, sq * 0.92f), style = Stroke(sq * 0.07f))
                }
            }
            // Sélection
            selected?.let { drawRect(Palette.Cyan.copy(alpha = 0.33f), topLeft(it), Size(sq, sq)) }
            // Roi en échec : halo rouge
            if (position.isCheck) {
                val k = position.kingSquare(position.sideToMove)
                if (k >= 0) {
                    val c = center(k)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3B5C).copy(alpha = 0.95f), Color(0xFFFF3B5C).copy(alpha = 0f)), c, sq * 0.62f), sq * 0.62f, c)
                }
            }
            // Coordonnées
            if (showCoordinates) {
                val style = TextStyle(fontSize = (sq * 0.17f / density.fontScale / density.density).sp, fontWeight = FontWeight.Bold)
                for (i in 0..7) {
                    val fileSq = if (flipped) Sq.of(7 - i, 7) else Sq.of(i, 0)
                    val fileLabel = ('a' + Sq.file(fileSq)).toString()
                    val bottomSq = if (flipped) Sq.of(7 - i, 7) else Sq.of(i, 0)
                    val fc = if (Sq.isLight(bottomSq)) theme.coordLight else theme.coordDark
                    val tl = topLeft(bottomSq)
                    val m = textMeasurer.measure(fileLabel, style.copy(color = fc))
                    drawText(m, topLeft = Offset(tl.x + sq - m.size.width - sq * 0.05f, tl.y + sq - m.size.height))
                    val rankSq = if (flipped) Sq.of(7, i) else Sq.of(0, 7 - i)
                    val rc = if (Sq.isLight(rankSq)) theme.coordLight else theme.coordDark
                    val rl = topLeft(rankSq)
                    val rm = textMeasurer.measure((Sq.rank(rankSq) + 1).toString(), style.copy(color = rc))
                    drawText(rm, topLeft = Offset(rl.x + sq * 0.05f, rl.y + sq * 0.02f))
                }
            }
            // Pièces (avec animation du dernier coup)
            val anim = progress.value
            val animMove = lastMove?.takeIf { anim < 1f }
            val rookAnim: Pair<Int, Int>? = animMove?.let { m ->
                val p = position.pieceAt(m.to)
                if (p?.type == PieceType.KING && kotlin.math.abs(Sq.file(m.to) - Sq.file(m.from)) == 2) {
                    val r = Sq.rank(m.to)
                    if (Sq.file(m.to) == 6) Sq.of(7, r) to Sq.of(5, r) else Sq.of(0, r) to Sq.of(3, r)
                } else null
            }
            for (s in 0 until 64) {
                val piece = position.pieceAt(s) ?: continue
                if (s == dragFrom && dragPos != null) continue
                var tl = topLeft(s)
                if (animMove != null && s == animMove.to) tl = lerp(topLeft(animMove.from), tl, anim)
                if (rookAnim != null && s == rookAnim.second) tl = lerp(topLeft(rookAnim.first), tl, anim)
                drawPiece(painters[piece]!!, tl, sq)
            }
            // Destinations possibles
            val from = selected
            if (canMove && from != null) {
                for (m in position.legalMovesFrom(from).distinctBy { it.to }) {
                    val c = center(m.to)
                    if (position.isCapture(m)) drawCircle(Color.Black.copy(alpha = 0.28f), sq * 0.44f, c, style = Stroke(sq * 0.08f))
                    else drawCircle(Color.Black.copy(alpha = 0.26f), sq * 0.15f, c)
                }
            }
            // Flèches
            for (a in arrows) drawArrow(center(a.from), center(a.to), a.color, sq * a.widthFraction, sq)
            // Pièce en cours de déplacement (légèrement agrandie sous le doigt)
            val df = dragFrom; val dp = dragPos
            if (df != null && dp != null) {
                position.pieceAt(df)?.let { p ->
                    val size = sq * 1.25f
                    drawCircle(Palette.Cyan.copy(alpha = 0.18f), sq * 0.7f, dp)
                    drawPiece(painters[p]!!, Offset(dp.x - size / 2, dp.y - size * 0.75f), size)
                }
            }
        }

        // Choix de la pièce de promotion
        pendingPromotion?.let { (from, to) ->
            val tl = topLeft(to)
            val down = tl.y > boardPx / 2
            val color = position.pieceAt(from)?.color ?: PieceColor.WHITE
            val items = PieceType.PROMOTIONS
            val totalH = sq * items.size
            val y = if (down) tl.y + sq - totalH else tl.y
            Column(
                Modifier
                    .offset { IntOffset(tl.x.roundToInt(), y.roundToInt()) }
                    .size(with(density) { sq.toDp() }, with(density) { totalH.toDp() })
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF01B1B28))
                    .border(1.dp, Palette.Violet, RoundedCornerShape(10.dp)),
            ) {
                for (t in items) {
                    Box(
                        Modifier.size(with(density) { sq.toDp() }).clickable {
                            pendingPromotion = null
                            selected = null
                            currentOnMove(Move(from, to, t))
                        }.padding(4.dp),
                    ) {
                        Image(painters[Piece.of(t, color)]!!, contentDescription = t.frenchName, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun DrawScope.drawPiece(painter: Painter, topLeft: Offset, size: Float) {
    val inset = size * 0.04f
    translate(topLeft.x + inset, topLeft.y + inset) {
        with(painter) { draw(Size(size - 2 * inset, size - 2 * inset)) }
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, width: Float, sq: Float) {
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val angle = atan2(dy, dx)
    val head = width * 2.6f
    val start = Offset(from.x + cos(angle) * sq * 0.18f, from.y + sin(angle) * sq * 0.18f)
    val end = Offset(to.x - cos(angle) * head * 0.9f, to.y - sin(angle) * head * 0.9f)
    drawLine(color, start, end, width, cap = StrokeCap.Round)
    val tip = Offset(to.x - cos(angle) * sq * 0.08f, to.y - sin(angle) * sq * 0.08f)
    val left = Offset(tip.x - cos(angle - 0.5f) * head, tip.y - sin(angle - 0.5f) * head)
    val right = Offset(tip.x - cos(angle + 0.5f) * head, tip.y - sin(angle + 0.5f) * head)
    val path = Path().apply { moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
    drawPath(path, color)
}
