package com.chessoverlay.app.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessoverlay.analysis.GameReview
import com.chessoverlay.analysis.MoveClassification
import com.chessoverlay.app.AppContainer
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.TextInputDialog
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Pgn
import com.chessoverlay.data.SavedPositionEntity
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.InfoRow
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val game: Game? = null,
    val review: GameReview? = null,
    val running: Boolean = false,
    val progress: Pair<Int, Int> = 0 to 0,
    /** Demi-coup sélectionné (1 = premier coup). */
    val selected: Int = 0,
    val depth: Int = 14,
    val error: String? = null,
    val message: String? = null,
)

class ReviewViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state
    private var job: Job? = null

    fun load(game: Game) {
        job?.cancel()
        _state.value = ReviewUiState(game = game, selected = game.ply)
    }

    fun loadPgn(pgn: String): Boolean = runCatching { load(Pgn.parse(pgn)) }.onFailure {
        _state.update { s -> s.copy(error = "PGN illisible : ${it.message}") }
    }.isSuccess

    fun setDepth(d: Int) = _state.update { it.copy(depth = d) }

    fun run() {
        val g = _state.value.game ?: return
        job?.cancel()
        _state.update { it.copy(running = true, review = null, error = null, progress = 0 to g.ply) }
        job = viewModelScope.launch {
            try {
                val r = app.analysis.reviewGame(g, _state.value.depth) { done, total -> _state.update { it.copy(progress = done to total) } }
                _state.update { it.copy(review = r, running = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = "Erreur pendant la revue : ${e.message}") }
            }
        }
    }

    fun select(ply: Int) = _state.update { s -> s.copy(selected = ply.coerceIn(0, s.game?.ply ?: 0)) }

    /** Enregistre la position précédant une erreur comme exercice personnel. */
    fun createExercise(ply: Int) {
        val s = _state.value
        val g = s.game ?: return
        val mr = s.review?.moves?.firstOrNull { it.ply == ply } ?: return
        val best = mr.bestMoveUci ?: return
        val fen = g.positions[ply - 1].toFen()
        viewModelScope.launch(Dispatchers.IO) {
            app.database.positions().insert(
                SavedPositionEntity(
                    name = "Erreur au coup ${(ply + 1) / 2} (${mr.san})", fen = fen, category = "erreur", bestMoveUci = best,
                    note = "Vous avez joué ${mr.san} (${mr.classification.frenchLabel.lowercase()}). Stockfish recommandait ${mr.bestMoveSan ?: best}.",
                ),
            )
            _state.update { it.copy(message = "Exercice créé dans « Mes erreurs ».") }
        }
    }

    override fun onCleared() { job?.cancel(); super.onCleared() }
}

fun classificationColor(c: MoveClassification): Color = when (c) {
    MoveClassification.BEST -> Palette.Success
    MoveClassification.EXCELLENT -> Color(0xFF6EE7B7)
    MoveClassification.GOOD -> Palette.Cyan
    MoveClassification.BOOK -> Palette.VioletSoft
    MoveClassification.FORCED -> Palette.TextSecondary
    MoveClassification.INACCURACY -> Palette.Warning
    MoveClassification.MISTAKE -> Color(0xFFFB923C)
    MoveClassification.BLUNDER -> Palette.Danger
}

@Composable
fun ReviewScreen(vm: ReviewViewModel, onBack: () -> Unit, onAnalyze: (Game, Int) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    var showImport by remember { mutableStateOf(false) }
    val g = s.game
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Revue de partie", g?.let { "${it.tags["White"] ?: "?"} – ${it.tags["Black"] ?: "?"} · ${it.result}" } ?: "Importez ou choisissez une partie", onBack = onBack) {
            RoundIconButton(Icons.Rounded.UploadFile, "Importer un PGN", { showImport = true }, size = 42.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (g == null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Importez un PGN (bouton en haut à droite) ou ouvrez une partie depuis l'historique.", color = Palette.TextSecondary)
                }
                return@Column
            }
            val sel = s.selected
            val reviewMove = s.review?.moves?.firstOrNull { it.ply == sel }
            val arrows = buildList {
                if (reviewMove != null && sel > 0) {
                    reviewMove.bestMoveUci?.let { Move.fromUci(it) }?.let { add(BoardArrow(it.from, it.to, Palette.Success.copy(alpha = 0.75f))) }
                }
            }
            ChessBoard(
                position = if (reviewMove != null && sel > 0) g.positions[sel - 1] else g.positions[sel],
                modifier = Modifier.fillMaxWidth(), theme = settings.theme, pieceSet = settings.pieces,
                lastMove = if (reviewMove != null && sel > 0) g.moves[sel - 1] else if (sel > 0) g.moves[sel - 1] else null,
                arrows = arrows, showCoordinates = settings.showCoordinates, animate = settings.animations,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Précédent", { vm.select(sel - 1) }, enabled = sel > 0)
                RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Suivant", { vm.select(sel + 1) }, enabled = sel < g.ply)
                Text(if (sel == 0) "Position de départ" else "Coup ${(sel + 1) / 2}${if (sel % 2 == 1) "" else "…"} ${g.sans[sel - 1]}", color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            }
            if (reviewMove != null) {
                Spacer(Modifier.height(4.dp))
                Text("Flèche verte : le coup que Stockfish recommandait dans cette position.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Spacer(Modifier.height(12.dp))
            if (s.review == null) {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    SectionTitle("Analyse complète")
                    Text("Stockfish évalue chaque position de la partie (${g.ply} demi-coups) à la profondeur ${s.depth}.", color = Palette.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Slider(s.depth.toFloat(), { vm.setDepth(it.toInt()) }, valueRange = 8f..22f, steps = 13,
                        colors = com.chessoverlay.app.ui.training.sliderColors())
                    if (s.running) {
                        LinearProgressIndicator(
                            progress = { if (s.progress.second == 0) 0f else s.progress.first.toFloat() / s.progress.second },
                            modifier = Modifier.fillMaxWidth(), color = Palette.Cyan, trackColor = Palette.GraphiteHigh,
                        )
                        Text("Position ${s.progress.first} / ${s.progress.second}", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    } else GlowButton("Lancer la revue", vm::run, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
                }
            }
            s.error?.let { Text(it, color = Palette.Danger) }
            s.message?.let { Text(it, color = Palette.Success) }
            val review = s.review
            if (review != null) {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Évaluation au fil de la partie")
                    EvalGraph(review, sel, vm::select)
                    Spacer(Modifier.height(10.dp))
                    InfoRow("Précision des Blancs", String.format(java.util.Locale.FRANCE, "%.1f %%", review.accuracyWhite), emphasize = true)
                    InfoRow("Précision des Noirs", String.format(java.util.Locale.FRANCE, "%.1f %%", review.accuracyBlack), emphasize = true)
                    val counts = review.moves.groupingBy { it.classification }.eachCount()
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MoveClassification.INACCURACY, MoveClassification.MISTAKE, MoveClassification.BLUNDER).forEach {
                            Pill("${it.frenchLabel} : ${counts[it] ?: 0}", classificationColor(it))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (reviewMove != null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(reviewMove.san, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Pill(reviewMove.classification.frenchLabel, classificationColor(reviewMove.classification), filled = true)
                        }
                        Text(reviewMove.comment, color = Palette.TextSecondary)
                        InfoRow("Évaluation avant", reviewMove.evalBeforeWhite.format())
                        InfoRow("Évaluation après", reviewMove.evalAfterWhite.format())
                        reviewMove.bestMoveSan?.let { InfoRow("Meilleur coup", it, valueColor = Palette.Success) }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlowButton("Analyser ici", { onAnalyze(g, sel - 1) }, secondary = true, modifier = Modifier.weight(1f))
                            if (reviewMove.classification in setOf(MoveClassification.MISTAKE, MoveClassification.BLUNDER, MoveClassification.INACCURACY))
                                GlowButton("Créer un exercice", { vm.createExercise(sel) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Coups")
                    review.moves.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (m.ply == sel) Palette.Violet.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { vm.select(m.ply) }.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${(m.ply + 1) / 2}${if (m.ply % 2 == 1) "." else "…"}", color = Palette.TextMuted, modifier = Modifier.width(44.dp))
                            Text(m.san, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(m.evalAfterWhite.format(), color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                            Pill(m.classification.frenchLabel, classificationColor(m.classification))
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (showImport) TextInputDialog("Importer une partie", "PGN", onDismiss = { showImport = false }) { if (vm.loadPgn(it)) showImport = false }
}

/** Graphique de probabilité de gain des Blancs, coup par coup ; un toucher sélectionne le coup. */
@Composable
private fun EvalGraph(review: GameReview, selected: Int, onSelect: (Int) -> Unit) {
    val points = listOf(review.moves.firstOrNull()?.evalBeforeWhite?.winPercent() ?: 50.0) + review.moves.map { it.evalAfterWhite.winPercent() }
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F0F17))
            .pointerInput(points.size) {
                detectTapGesturesCompat { x -> onSelect(((x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)) }
            },
    ) {
        val n = points.size
        if (n < 2) return@Canvas
        val dx = size.width / (n - 1)
        val path = Path().apply {
            moveTo(0f, size.height)
            points.forEachIndexed { i, p -> lineTo(i * dx, size.height * (1 - (p / 100f).toFloat())) }
            lineTo(size.width, size.height); close()
        }
        drawPath(path, Color(0xFFEDEBF7).copy(alpha = 0.85f))
        drawLine(Palette.Violet.copy(alpha = 0.6f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
        review.moves.forEach { m ->
            if (m.classification == MoveClassification.BLUNDER || m.classification == MoveClassification.MISTAKE) {
                val x = m.ply * dx
                drawCircle(classificationColor(m.classification), 4.dp.toPx(), Offset(x, size.height * (1 - (m.evalAfterWhite.winPercent() / 100f).toFloat())))
            }
        }
        drawLine(Palette.Cyan, Offset(selected * dx, 0f), Offset(selected * dx, size.height), 2f)
        drawRect(Palette.GraphiteLine, style = Stroke(1f))
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesCompat(onTap: (Float) -> Unit) {
    detectTapGestures { offset -> onTap(offset.x) }
}
