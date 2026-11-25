package com.chessoverlay.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chessoverlay.analysis.AnalysisSettings
import com.chessoverlay.analysis.CaptureInfo
import com.chessoverlay.analysis.Opening
import com.chessoverlay.analysis.PositionAnalysis
import com.chessoverlay.analysis.TacticalReport
import com.chessoverlay.analysis.TacticalScanner
import com.chessoverlay.app.AppContainer
import com.chessoverlay.chess.ChessFormatException
import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Pgn
import com.chessoverlay.chess.Position
import com.chessoverlay.data.AnalysisRecordEntity
import com.chessoverlay.data.SavedGameEntity
import com.chessoverlay.data.SavedPositionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prévisualisation d'une variante sur l'échiquier : [step] coups de la ligne [lineIndex] déjà joués. */
data class LinePreview(val lineIndex: Int, val step: Int)

data class AnalysisUiState(
    val game: Game = Game.new(),
    /** Demi-coup affiché (0 = position de départ). */
    val cursor: Int = 0,
    val flipped: Boolean = false,
    val analysis: PositionAnalysis? = null,
    val analyzing: Boolean = false,
    val paused: Boolean = false,
    val depthBoost: Int = 0,
    val error: String? = null,
    val captures: List<CaptureInfo> = emptyList(),
    val capturesLoading: Boolean = false,
    val tactics: TacticalReport? = null,
    val opening: Opening? = null,
    val preview: LinePreview? = null,
    val message: String? = null,
) {
    val position: Position get() = game.positions[cursor]
    val lastMove: Move? get() = if (cursor > 0) game.moves[cursor - 1] else null
}

class AnalysisViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state

    private var analysisJob: Job? = null
    private var capturesJob: Job? = null
    /** Stockfish ne tourne que lorsque l'écran d'analyse est affiché (économie de batterie). */
    private var visible = false

    init { refresh() }

    fun setVisible(v: Boolean) {
        visible = v
        val s = _state.value
        if (v && s.analysis == null && !s.analyzing && !s.paused && app.settings.value.autoAnalyze) startAnalysis()
    }

    // ------------------------------------------------------------------ chargement

    fun loadFen(fen: String): Boolean {
        val pos = try { Position.fromFen(fen) } catch (e: ChessFormatException) {
            _state.update { it.copy(message = "FEN invalide : ${e.message}") }
            return false
        }
        val errors = pos.validationErrors()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(message = "Position impossible : " + errors.joinToString(" ")) }
            return false
        }
        setGame(Game.new(pos), 0, flipped = pos.sideToMove == com.chessoverlay.chess.PieceColor.BLACK)
        return true
    }

    fun loadPgn(pgn: String): Boolean = try {
        val g = Pgn.parse(pgn)
        setGame(g, g.ply, flipped = false)
        true
    } catch (e: Exception) {
        _state.update { it.copy(message = "PGN illisible : ${e.message}") }
        false
    }

    /** Accepte indifféremment une FEN ou un PGN. */
    fun loadText(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val looksFen = t.count { it == '/' } == 7 && !t.contains('[') && !t.contains('.')
        return if (looksFen) loadFen(t) else loadPgn(t)
    }

    fun loadGame(game: Game, ply: Int = game.ply) = setGame(game, ply, flipped = false)

    private fun setGame(game: Game, cursor: Int, flipped: Boolean) {
        _state.update {
            it.copy(game = game, cursor = cursor.coerceIn(0, game.ply), flipped = flipped, preview = null, depthBoost = 0, paused = false, message = null)
        }
        refresh()
    }

    // ------------------------------------------------------------------ interactions

    fun play(move: Move) {
        val s = _state.value
        val pos = s.position
        val legal = pos.normalize(move) ?: return
        app.sounds.playFor(pos, legal)
        val base = s.game.truncated(s.cursor)
        val g = base.play(legal)
        _state.update { it.copy(game = g, cursor = g.ply, preview = null, depthBoost = 0) }
        refresh()
    }

    fun goTo(ply: Int) {
        val s = _state.value
        val p = ply.coerceIn(0, s.game.ply)
        if (p == s.cursor) return
        _state.update { it.copy(cursor = p, preview = null, depthBoost = 0) }
        refresh()
    }

    fun back() = goTo(_state.value.cursor - 1)
    fun forward() = goTo(_state.value.cursor + 1)
    fun flip() = _state.update { it.copy(flipped = !it.flipped) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setPreview(preview: LinePreview?) = _state.update { it.copy(preview = preview) }

    /** Joue sur l'échiquier le premier coup de la variante [index]. */
    fun playLine(index: Int) {
        val line = _state.value.analysis?.lines?.getOrNull(index) ?: return
        play(line.first.move)
    }

    fun togglePause() {
        val s = _state.value
        if (s.analyzing) {
            // Arrêt : le moteur renvoie immédiatement le meilleur coup trouvé à la profondeur atteinte.
            app.engines.analysisEngine.stop()
            _state.update { it.copy(paused = true) }
        } else {
            _state.update { it.copy(paused = false) }
            startAnalysis()
        }
    }

    /** Reprend le calcul avec 4 demi-coups de profondeur supplémentaires. */
    fun deeper() {
        _state.update { it.copy(depthBoost = it.depthBoost + 4, paused = false) }
        startAnalysis()
    }

    // ------------------------------------------------------------------ analyse

    private fun refresh() {
        val s = _state.value
        val pos = s.position
        _state.update {
            it.copy(
                analysis = null, captures = emptyList(), error = null,
                tactics = runCatching { TacticalScanner.scan(pos) }.getOrNull(),
            )
        }
        // L'identification de l'ouverture charge la base ECO au premier appel : hors du fil principal.
        viewModelScope.launch(Dispatchers.Default) {
            val o = runCatching { app.openings.identify(s.game.truncated(s.cursor)) }.getOrNull()
            if (_state.value.position == pos) _state.update { it.copy(opening = o) }
        }
        if (app.settings.value.autoAnalyze && !s.paused && visible) startAnalysis() else stopJobs()
    }

    private fun stopJobs() {
        analysisJob?.cancel(); capturesJob?.cancel()
        _state.update { it.copy(analyzing = false, capturesLoading = false) }
    }

    fun startAnalysis() {
        analysisJob?.cancel()
        capturesJob?.cancel()
        if (!app.engines.isAvailable) {
            _state.update { it.copy(error = "Le moteur Stockfish est introuvable dans l'installation (${app.engines.binary}).") }
            return
        }
        val pos = _state.value.position
        val cfg = app.settings.value
        val settings = AnalysisSettings(
            depth = cfg.effectiveDepth + _state.value.depthBoost,
            moveTimeMs = cfg.moveTimeMs.takeIf { it > 0 && _state.value.depthBoost == 0 }?.toLong(),
            multiPv = cfg.multiPv,
        )
        _state.update { it.copy(analyzing = true, error = null) }
        analysisJob = viewModelScope.launch {
            try {
                var last: PositionAnalysis? = null
                app.analysis.analyze(pos, settings).collect { a ->
                    last = a
                    _state.update { it.copy(analysis = a) }
                }
                _state.update { it.copy(analyzing = false) }
                last?.let { onFinished(pos, it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(analyzing = false, error = "Erreur du moteur : ${e.message}") }
            }
        }
    }

    private fun onFinished(pos: Position, a: PositionAnalysis) {
        val best = a.best ?: return
        if (app.settings.value.keepHistory) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    app.database.analyses().insert(
                        AnalysisRecordEntity(
                            fen = pos.toFen(), bestMoveUci = best.first.uci,
                            bestMoveText = best.first.headline + " (" + best.first.sanFrench + ")",
                            evaluation = best.scoreWhite.format(), depth = best.depth,
                        ),
                    )
                    app.database.analyses().trim()
                }
            }
        }
        // Évaluation de chaque capture possible par Stockfish (searchmoves), après l'analyse principale.
        if (pos.legalMoves().any { pos.isCapture(it) }) {
            _state.update { it.copy(capturesLoading = true) }
            capturesJob = viewModelScope.launch {
                val caps = runCatching { app.analysis.evaluateCaptures(pos, depth = 12) }.getOrElse { emptyList() }
                if (_state.value.position == pos) _state.update { it.copy(captures = caps, capturesLoading = false) }
            }
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    fun savePosition(name: String) {
        val fen = _state.value.position.toFen()
        viewModelScope.launch(Dispatchers.IO) {
            app.database.positions().insert(SavedPositionEntity(name = name.ifBlank { "Position" }, fen = fen))
            _state.update { it.copy(message = "Position enregistrée.") }
        }
    }

    fun saveGame() {
        val g = _state.value.game
        if (g.ply == 0) { _state.update { it.copy(message = "Aucun coup à enregistrer.") }; return }
        viewModelScope.launch(Dispatchers.IO) {
            app.database.games().insert(
                SavedGameEntity(
                    title = "Analyse du " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date()),
                    white = g.tags["White"] ?: "?", black = g.tags["Black"] ?: "?", result = g.result,
                    pgn = g.toPgn(), finalFen = g.current.toFen(), source = "Analyse", plyCount = g.ply,
                ),
            )
            _state.update { it.copy(message = "Partie enregistrée dans l'historique.") }
        }
    }

    override fun onCleared() {
        stopJobs()
        super.onCleared()
    }

    suspend fun currentFen(): String = withContext(Dispatchers.Default) { _state.value.position.toFen() }
}
