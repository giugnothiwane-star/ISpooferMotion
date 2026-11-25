package com.chessoverlay.app.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessoverlay.data.AppSettings
import com.chessoverlay.app.ui.analysis.TabRowGlass
import com.chessoverlay.app.ui.analysis.scoreColor
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Sq
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.BoardTheme
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.PulseDot
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.flow.StateFlow

/** Bulle flottante déplaçable : un toucher ouvre ou ferme le panneau. */
@Composable
fun OverlayBubble(state: StateFlow<OverlayState>, onTap: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val s by state.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (dragging) 1.12f else 1f, tween(140), label = "bubble")
    val busy = s.phase is OverlayPhase.Working || (s.phase as? OverlayPhase.Analyzing)?.running == true
    Box(
        Modifier.padding(6.dp).scale(scale).size(56.dp).shadow(14.dp, CircleShape)
            .clip(CircleShape).background(Palette.Accent)
            .border(2.dp, Color.White.copy(alpha = if (s.expanded) 0.9f else 0.35f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false; onDragEnd() },
                ) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(50.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.dp)
        Icon(Icons.Rounded.Bolt, "Chess Overlay AI", tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/** Panneau flottant : capture, vérification de la position reconnue, puis meilleur coup. */
@Composable
fun OverlayPanel(
    state: StateFlow<OverlayState>,
    settingsFlow: StateFlow<AppSettings>,
    scale: Float,
    actions: OverlayActions,
    onDrag: (Float, Float) -> Unit,
) {
    val s by state.collectAsState()
    val settings by settingsFlow.collectAsState()
    val theme = BoardTheme.fromId(settings.boardTheme)
    val pieces = PieceSet.fromId(settings.pieceStyle)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.padding(6.dp).fillMaxWidth().shadow(24.dp, shape).clip(shape)
            .background(Color(0xF2101019))
            .border(1.dp, Palette.AccentSoft, shape),
    ) {
        // En-tête déplaçable
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.04f))
                .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DragIndicator, "Déplacer", tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Chess Overlay AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Rounded.Remove, "Réduire", actions::collapse, size = 34.dp)
            Spacer(Modifier.width(6.dp))
            RoundIconButton(Icons.Rounded.Close, "Fermer le menu flottant", actions::close, size = 34.dp)
        }
        Column(Modifier.heightIn(max = (560 * scale).dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
            AnimatedContent(s.phase, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, contentKey = { it::class }, label = "phase") { phase ->
                when (phase) {
                    is OverlayPhase.Idle -> Column {
                        Text("Affichez une partie à l'écran puis capturez-la. Android vous demandera l'autorisation de capture.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        GlowButton("Capturer l'écran", actions::capture, icon = Icons.Rounded.Screenshot, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        GlowButton("Ouvrir l'application", actions::openApp, icon = Icons.Rounded.OpenInNew, secondary = true, modifier = Modifier.fillMaxWidth())
                    }
                    is OverlayPhase.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(phase.text, color = Palette.TextSecondary)
                    }
                    is OverlayPhase.Error -> Column {
                        Text(phase.message, color = Palette.Warning)
                        Spacer(Modifier.height(10.dp))
                        GlowButton("Réessayer", actions::capture, icon = Icons.Rounded.Screenshot, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        GlowButton(if (phase.canFrame) "Cadrer l'échiquier dans l'application" else "Corriger dans l'application", actions::openEditor, icon = Icons.Rounded.Edit, secondary = true, modifier = Modifier.fillMaxWidth())
                    }
                    is OverlayPhase.Recognized -> {
                        val r = phase.session.result
                        val pos = r.toPosition(phase.side)
                        val errors = pos.validationErrors()
                        Column {
                            Text("Position détectée — vérifiez-la", style = MaterialTheme.typography.titleSmall)
                            Text("Confiance ${(r.overallConfidence * 100).toInt()} %" + if (r.lowConfidenceSquares().isNotEmpty()) " · cases à vérifier en orange" else "",
                                color = if (r.overallConfidence > 0.8f) Palette.Success else Palette.Warning, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            ChessBoard(pos, Modifier.fillMaxWidth(), flipped = !r.whiteAtBottom, theme = theme, pieceSet = pieces,
                                marks = r.lowConfidenceSquares().map { SquareMark(it, Palette.Warning, MarkStyle.RING) }, showCoordinates = true, animate = false)
                            Spacer(Modifier.height(8.dp))
                            r.warnings.take(2).forEach { Text("• $it", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall) }
                            Text("Camp au trait", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                            TabRowGlass(listOf("Blancs", "Noirs"), if (phase.side == PieceColor.WHITE) 0 else 1) { actions.setSide(if (it == 0) PieceColor.WHITE else PieceColor.BLACK) }
                            errors.firstOrNull()?.let { Text("⚠ $it", color = Palette.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                            Spacer(Modifier.height(10.dp))
                            GlowButton("Confirmer et analyser", actions::confirmAndAnalyze, icon = Icons.Rounded.Bolt, enabled = errors.isEmpty(), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlowButton("Corriger", actions::openEditor, icon = Icons.Rounded.Edit, secondary = true, modifier = Modifier.weight(1f))
                                GlowButton("Recapturer", actions::capture, icon = Icons.Rounded.Screenshot, secondary = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    is OverlayPhase.Analyzing -> AnalysisContent(phase, theme, pieces, actions)
                }
            }
        }
    }
}

@Composable
private fun AnalysisContent(phase: OverlayPhase.Analyzing, theme: BoardTheme, pieces: PieceSet, actions: OverlayActions) {
    val r = phase.session.result
    val pos = r.toPosition(phase.side)
    val a = phase.analysis
    val best = a?.best
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(phase.running)
            Spacer(Modifier.width(8.dp))
            Text(if (phase.running) "Stockfish calcule… profondeur ${a?.depth ?: 0}" else "Analyse terminée · profondeur ${a?.depth ?: 0}",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
            best?.let { Pill(it.scoreWhite.format(), scoreColor(it.score)) }
        }
        Spacer(Modifier.height(8.dp))
        val arrows = a?.lines.orEmpty().mapIndexed { i, l ->
            BoardArrow(l.first.from, l.first.to, if (i == 0) Palette.Violet.copy(alpha = 0.9f) else Palette.Cyan.copy(alpha = 0.5f), if (i == 0) 0.2f else 0.12f)
        }
        ChessBoard(pos, Modifier.fillMaxWidth(), flipped = !r.whiteAtBottom, theme = theme, pieceSet = pieces, arrows = arrows,
            marks = best?.let { listOf(SquareMark(it.first.from, Palette.Violet, MarkStyle.RING), SquareMark(it.first.to, Palette.Cyan)) } ?: emptyList(),
            showCoordinates = true, animate = false)
        Spacer(Modifier.height(10.dp))
        if (a?.gameOverReason != null) {
            Text(a.gameOverReason!!, style = MaterialTheme.typography.titleSmall)
        } else if (best == null) {
            Text("Calcul en cours…", color = Palette.TextSecondary)
        } else {
            val d = best.first
            Text("MEILLEUR COUP", style = MaterialTheme.typography.labelMedium, color = Palette.VioletSoft)
            Text(d.piece.frenchName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${Sq.upperName(d.from)} → ${Sq.upperName(d.to)}  ·  ${d.sanFrench}  ·  ${d.kind.frenchLabel}", color = Palette.CyanSoft, style = MaterialTheme.typography.bodyMedium)
            d.captured?.let { Text("Capture : ${it.frenchName} en ${Sq.upperName(d.capturedSquare)}", color = Palette.Warning, style = MaterialTheme.typography.bodySmall) }
            best.explanation.take(2).forEach { Text("• $it", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            a.opponentBestReply?.let {
                Spacer(Modifier.height(6.dp))
                Text("Réponse adverse : ${it.headline}", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }
            if (a.lines.size > 1) {
                Spacer(Modifier.height(6.dp))
                a.lines.drop(1).forEachIndexed { i, l ->
                    Text("#${i + 2}  ${l.first.headline}  (${l.scoreWhite.format()})", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phase.running) GlowButton("Arrêter", actions::stopAnalysis, icon = Icons.Rounded.Stop, secondary = true, modifier = Modifier.weight(1f))
            GlowButton("Recapturer", actions::capture, icon = Icons.Rounded.Screenshot, secondary = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        GlowButton("Détails dans l'application", actions::openAnalysis, icon = Icons.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
    }
}
