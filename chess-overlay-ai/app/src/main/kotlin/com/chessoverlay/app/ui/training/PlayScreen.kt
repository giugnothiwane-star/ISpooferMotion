package com.chessoverlay.app.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessoverlay.app.AppContainer
import com.chessoverlay.app.ui.analysis.TabRowGlass
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.chess.Game
import com.chessoverlay.chess.GameStatus
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Position
import com.chessoverlay.data.SavedGameEntity
import com.chessoverlay.engine.EngineOptions
import com.chessoverlay.engine.SearchLimits
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.components.SettingSwitch
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayUiState(
    val started: Boolean = false,
    val game: Game = Game.new(),
    val humanColor: PieceColor = PieceColor.WHITE,
    /** Niveau 1..10 (10 = Stockfish à pleine puissance). */
    val level: Int = 4,
    val thinkMs: Int = 800,
    val engineThinking: Boolean = false,
    val hint: Move? = null,
    val autopilot: Boolean = false,
    val title: String = "Partie contre Stockfish",
    val goal: String? = null,
    val finished: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class PlayViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state
    private var engineJob: Job? = null

    /** Prépare une partie (depuis la position initiale ou une position personnalisée / une finale). */
    fun prepare(fen: String? = null, title: String = "Partie contre Stockfish", goal: String? = null) {
        engineJob?.cancel()
        val pos = fen?.let { Position.fromFenOrNull(it) } ?: Position.initial()
        _state.value = PlayUiState(game = Game.new(pos), humanColor = pos.sideToMove, title = title, goal = goal,
            level = _state.value.level, thinkMs = _state.value.thinkMs)
    }

    fun setColor(c: PieceColor) = _state.update { it.copy(humanColor = c) }
    fun setLevel(l: Int) = _state.update { it.copy(level = l.coerceIn(1, 10)) }
    fun setThink(ms: Int) = _state.update { it.copy(thinkMs = ms) }

    fun start() {
        _state.update { it.copy(started = true, finished = false, saved = false, hint = null) }
        viewModelScope.launch { runCatching { app.engines.playEngine().newGame() } }
        maybeEngineMove()
    }

    fun newGame() {
        engineJob?.cancel()
        _state.update { it.copy(started = false, game = Game.new(it.game.initial), finished = false, saved = false, hint = null, autopilot = false) }
    }

    fun humanMove(move: Move) {
        val s = _state.value
        if (!s.started || s.finished || s.engineThinking) return
        if (s.game.current.sideToMove != s.humanColor) return
        apply(move)
    }

    private fun apply(move: Move) {
        val s = _state.value
        val pos = s.game.current
        val legal = pos.normalize(move) ?: return
        app.sounds.playFor(pos, legal)
        val g = s.game.play(legal)
        _state.update { it.copy(game = g, hint = null) }
        if (g.status != GameStatus.ONGOING) finish() else maybeEngineMove()
    }

    private fun eloFor(level: Int): Int? = if (level >= 10) null else 1320 + (level - 1) * 200

    private fun maybeEngineMove() {
        val s = _state.value
        if (!s.started || s.finished) return
        val pos = s.game.current
        val engineTurn = pos.sideToMove != s.humanColor
        if (!engineTurn && !s.autopilot) return
        engineJob?.cancel()
        _state.update { it.copy(engineThinking = true) }
        engineJob = viewModelScope.launch {
            try {
                val g = s.game
                val result = if (engineTurn) {
                    val e = app.engines.playEngine()
                    e.configure(EngineOptions(threads = 2, hashMb = 64, limitElo = eloFor(s.level)))
                    e.search(g.initial.toFen(), SearchLimits(moveTimeMs = s.thinkMs.toLong()), g.uciMoves)
                } else {
                    // Pilote automatique : Stockfish joue aussi vos coups (partie locale uniquement).
                    app.engines.analysisEngine.search(g.initial.toFen(), SearchLimits(moveTimeMs = s.thinkMs.toLong()), g.uciMoves)
                }
                _state.update { it.copy(engineThinking = false) }
                val m = result.bestMove?.move?.let { Move.fromUci(it) } ?: return@launch
                if (_state.value.game.ply == g.ply) apply(m)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(engineThinking = false, error = "Stockfish : ${e.message}") }
            }
        }
    }

    fun toggleAutopilot() {
        _state.update { it.copy(autopilot = !it.autopilot) }
        if (_state.value.autopilot && !_state.value.engineThinking) maybeEngineMove()
    }

    fun undo() {
        engineJob?.cancel()
        val s = _state.value
        var n = s.game.ply
        if (n == 0) return
        n -= 1
        // Revient au dernier coup du joueur humain.
        if (n > 0 && s.game.positions[n].sideToMove != s.humanColor) n -= 1
        _state.update { it.copy(game = it.game.truncated(n), engineThinking = false, finished = false, hint = null) }
    }

    fun hint() {
        val s = _state.value
        if (s.finished) return
        viewModelScope.launch {
            runCatching {
                val r = app.engines.analysisEngine.search(s.game.current.toFen(), SearchLimits(depth = 16))
                _state.update { it.copy(hint = r.bestMove?.move?.let { u -> Move.fromUci(u) }) }
            }
        }
    }

    fun resign() {
        val s = _state.value
        val result = if (s.humanColor == PieceColor.WHITE) "0-1" else "1-0"
        _state.update { it.copy(game = it.game.withTags(it.game.tags + ("Result" to result))) }
        finish()
    }

    private fun finish() {
        engineJob?.cancel()
        _state.update { it.copy(finished = true, engineThinking = false, autopilot = false) }
        save()
    }

    private fun save() {
        val s = _state.value
        if (s.saved || s.game.ply == 0) return
        _state.update { it.copy(saved = true) }
        val human = "Vous"
        val bot = if (s.level >= 10) "Stockfish (max)" else "Stockfish (${eloFor(s.level)} Elo)"
        val white = if (s.humanColor == PieceColor.WHITE) human else bot
        val black = if (s.humanColor == PieceColor.WHITE) bot else human
        val g = s.game.withTags(s.game.tags + mapOf("White" to white, "Black" to black, "Event" to s.title))
        viewModelScope.launch(Dispatchers.IO) {
            app.database.games().insert(
                SavedGameEntity(
                    title = s.title, white = white, black = black, result = g.result, pgn = g.toPgn(),
                    finalFen = g.current.toFen(), source = "Entraînement", plyCount = g.ply,
                ),
            )
        }
    }

    fun statusText(): String {
        val s = _state.value
        val st = s.game.status
        return when {
            st != GameStatus.ONGOING -> st.frenchLabel + " — " + resultText(s.game.result, s.humanColor)
            s.finished -> "Abandon — " + resultText(s.game.result, s.humanColor)
            s.engineThinking -> "Stockfish réfléchit…"
            s.game.current.sideToMove == s.humanColor -> "À vous de jouer" + if (s.game.current.isCheck) " (échec !)" else ""
            else -> "Tour de Stockfish"
        }
    }

    private fun resultText(r: String, human: PieceColor) = when (r) {
        "1-0" -> if (human == PieceColor.WHITE) "vous gagnez" else "Stockfish gagne"
        "0-1" -> if (human == PieceColor.BLACK) "vous gagnez" else "Stockfish gagne"
        "1/2-1/2" -> "partie nulle"
        else -> ""
    }

    override fun onCleared() {
        engineJob?.cancel()
        app.engines.releasePlayEngine()
        super.onCleared()
    }
}

@Composable
fun PlayScreen(vm: PlayViewModel, onBack: () -> Unit, onReview: (Game) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(s.title, s.goal?.let { "Objectif : $it" } ?: "Entraînement local", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!s.started) {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    SectionTitle("Configuration")
                    Text("Votre couleur", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    TabRowGlass(listOf("Blancs", "Noirs"), if (s.humanColor == PieceColor.WHITE) 0 else 1) { vm.setColor(if (it == 0) PieceColor.WHITE else PieceColor.BLACK) }
                    Spacer(Modifier.height(14.dp))
                    val elo = if (s.level >= 10) "pleine puissance" else "≈ ${1320 + (s.level - 1) * 200} Elo"
                    Text("Niveau ${s.level} / 10 · $elo", style = MaterialTheme.typography.titleSmall)
                    Slider(s.level.toFloat(), { vm.setLevel(it.toInt()) }, valueRange = 1f..10f, steps = 8, colors = sliderColors())
                    Text("Temps de réflexion de Stockfish : ${s.thinkMs} ms", style = MaterialTheme.typography.titleSmall)
                    Slider(s.thinkMs.toFloat(), { vm.setThink((it / 100).toInt() * 100) }, valueRange = 200f..5000f, colors = sliderColors())
                    Text("Niveaux 1 à 9 : force limitée par l'option officielle UCI_Elo de Stockfish.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    GlowButton("Commencer la partie", vm::start, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.engineThinking) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(vm.statusText(), style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            ChessBoard(
                position = s.game.current,
                modifier = Modifier.fillMaxWidth(),
                flipped = s.humanColor == PieceColor.BLACK,
                theme = settings.theme, pieceSet = settings.pieces,
                interactive = s.started && !s.finished && !s.engineThinking,
                movableColor = s.humanColor,
                onMove = vm::humanMove,
                lastMove = s.game.moves.lastOrNull(),
                arrows = s.hint?.let { listOf(BoardArrow(it.from, it.to, Palette.Cyan.copy(alpha = 0.8f))) } ?: emptyList(),
                showCoordinates = settings.showCoordinates,
                animate = settings.animations,
            )
            Spacer(Modifier.height(12.dp))
            if (s.started) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Rounded.Undo, "Annuler le coup", vm::undo, enabled = s.game.ply > 0)
                    RoundIconButton(Icons.Rounded.Lightbulb, "Indice", vm::hint, enabled = !s.finished)
                    RoundIconButton(Icons.Rounded.SmartToy, "Pilote automatique", vm::toggleAutopilot, active = s.autopilot, enabled = !s.finished)
                    RoundIconButton(Icons.Rounded.Flag, "Abandonner", vm::resign, enabled = !s.finished)
                    RoundIconButton(Icons.Rounded.SwapVert, "Nouvelle partie", vm::newGame)
                }
                Spacer(Modifier.height(10.dp))
                SettingSwitch(
                    "Déplacement automatique", "Stockfish joue aussi vos coups (uniquement dans cette partie locale)",
                    s.autopilot, { vm.toggleAutopilot() },
                )
                if (s.game.sans.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Coups", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                        Text(
                            s.game.sans.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString("  "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (s.finished) {
                    Spacer(Modifier.height(12.dp))
                    GlowButton("Analyser la partie avec Stockfish", { onReview(s.game) }, icon = Icons.Rounded.Insights, modifier = Modifier.fillMaxWidth())
                }
            }
            s.error?.let { Text(it, color = Palette.Danger) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun sliderColors() = SliderDefaults.colors(thumbColor = Palette.Cyan, activeTrackColor = Palette.Violet, inactiveTrackColor = Palette.GraphiteHigh)

@Composable
fun SimpleInfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Palette.GraphiteHigh,
        title = { Text(title) }, text = { Text(text, color = Palette.TextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Palette.CyanSoft) } },
    )
}
