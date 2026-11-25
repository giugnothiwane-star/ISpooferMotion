package com.chessoverlay.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chessoverlay.analysis.EndgameSet
import com.chessoverlay.analysis.Opening
import com.chessoverlay.analysis.Puzzle
import com.chessoverlay.analysis.PuzzleSet
import com.chessoverlay.app.AppContainer
import com.chessoverlay.app.container
import com.chessoverlay.app.ui.analysis.TabRowGlass
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.Position
import com.chessoverlay.data.SavedPositionEntity
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------- hub

@Composable
fun TrainingHubScreen(
    onPlay: () -> Unit, onPuzzles: () -> Unit, onOpenings: () -> Unit, onEndgames: () -> Unit,
    onReview: () -> Unit, onEditor: () -> Unit, onHistory: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Entraînement", "Progressez avec Stockfish, hors ligne")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HubCard(Icons.Rounded.SportsEsports, "Jouer contre Stockfish", "10 niveaux, indices, annulation, pilote automatique", onPlay, glow = true)
            HubCard(Icons.Rounded.Extension, "Exercices tactiques", "Mats, fourchettes, clouages… et vos propres erreurs", onPuzzles)
            HubCard(Icons.Rounded.AutoStories, "Ouvertures", "Plus de 3 500 lignes ECO à étudier et à jouer", onOpenings)
            HubCard(Icons.Rounded.EmojiEvents, "Finales", "Positions types à gagner ou à annuler contre le moteur", onEndgames)
            HubCard(Icons.Rounded.Insights, "Analyser mes parties", "Revue coup par coup : gaffes, erreurs, précision", onReview)
            HubCard(Icons.Rounded.EditNote, "Créer une position", "Éditeur complet, puis analyse ou partie", onEditor)
            HubCard(Icons.Rounded.History, "Rejouer une partie enregistrée", "Historique des parties et positions", onHistory)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HubCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, glow: Boolean = false) {
    GlassCard(Modifier.fillMaxWidth(), glow = glow, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Palette.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = Palette.TextMuted)
        }
    }
}

// ---------------------------------------------------------------------------------------- exercices

data class PuzzleUiState(
    val puzzle: Puzzle? = null,
    val position: Position = Position.initial(),
    val step: Int = 0,
    val lastMove: Move? = null,
    val feedback: String? = null,
    val success: Boolean = false,
    val showSolution: Boolean = false,
    val showHint: Boolean = false,
)

class PuzzleViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PuzzleUiState())
    val state: StateFlow<PuzzleUiState> = _state

    fun open(p: Puzzle) {
        _state.value = PuzzleUiState(puzzle = p, position = Position.fromFen(p.fen))
    }

    fun close() { _state.value = PuzzleUiState() }

    fun move(m: Move) {
        val s = _state.value
        val p = s.puzzle ?: return
        if (s.success) return
        val legal = s.position.normalize(m) ?: return
        if (!PuzzleSet.isCorrectMove(p, s.step, s.position, legal)) {
            _state.update { it.copy(feedback = "Ce n'est pas le coup attendu. Réessayez !", showHint = true) }
            return
        }
        app.sounds.playFor(s.position, legal)
        var pos = s.position.play(legal)
        val next = s.step + 1
        val done = next >= p.solution.size || pos.isCheckmate
        _state.update { it.copy(position = pos, lastMove = legal, step = next, feedback = if (done) "Bravo, exercice résolu !" else "Bon coup ! Réponse de l'adversaire…", showSolution = false) }
        if (done) { markSolved(p); _state.update { it.copy(success = true) }; return }
        viewModelScope.launch {
            delay(550)
            val reply = Move.fromUci(p.solution[next])?.let { pos.normalize(it) } ?: return@launch
            app.sounds.playFor(pos, reply)
            pos = pos.play(reply)
            _state.update { it.copy(position = pos, lastMove = reply, step = next + 1, feedback = "À vous : trouvez la suite.") }
        }
    }

    private fun markSolved(p: Puzzle) {
        viewModelScope.launch { app.settingsRepository.update { it.copy(solvedPuzzles = it.solvedPuzzles + p.id) } }
    }

    fun reveal() = _state.update { it.copy(showSolution = true) }
    fun hint() = _state.update { it.copy(showHint = true) }

    /** Transforme une erreur enregistrée lors d'une revue de partie en exercice. */
    fun fromMistake(e: SavedPositionEntity): Puzzle? {
        val best = e.bestMoveUci ?: return null
        return Puzzle(id = "erreur-${e.id}", title = e.name, theme = "Vos erreurs", difficulty = 2, fen = e.fen, solution = listOf(best), hint = e.note.ifBlank { "Retrouvez le coup que Stockfish recommandait." })
    }
}

@Composable
fun PuzzlesScreen(vm: PuzzleViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    val ctx = LocalContext.current
    val mistakes by ctx.container.database.positions().observeCategory("erreur").collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    val puzzle = s.puzzle
    if (puzzle == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Exercices tactiques", "${settings.solvedPuzzles.size} résolu(s) sur ${PuzzleSet.puzzles.size}", onBack = onBack)
            Column(Modifier.padding(horizontal = 16.dp)) { TabRowGlass(listOf("Exercices", "Mes erreurs (${mistakes.size})"), tab) { tab = it } }
            Spacer(Modifier.height(10.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == 0) items(PuzzleSet.puzzles) { p ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { vm.open(p) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.titleSmall)
                                Text("${p.theme} · ${"★".repeat(p.difficulty)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                            if (p.id in settings.solvedPuzzles) Icon(Icons.Rounded.CheckCircle, "Résolu", tint = Palette.Success)
                        }
                    }
                } else {
                    if (mistakes.isEmpty()) item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Aucune erreur enregistrée. Analysez une partie puis touchez « Créer un exercice » sur une erreur ou une gaffe.", color = Palette.TextMuted)
                        }
                    }
                    items(mistakes) { e ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { vm.fromMistake(e)?.let(vm::open) }) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text(e.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
        return
    }
    val expected = puzzle.solution.getOrNull(s.step)?.let { Move.fromUci(it) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(puzzle.title, "${puzzle.theme} · trait aux ${if (Position.fromFen(puzzle.fen).sideToMove == PieceColor.WHITE) "Blancs" else "Noirs"}", onBack = vm::close)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val playerColor = Position.fromFen(puzzle.fen).sideToMove
            ChessBoard(
                position = s.position, modifier = Modifier.fillMaxWidth(),
                flipped = playerColor == PieceColor.BLACK, theme = settings.theme, pieceSet = settings.pieces,
                interactive = !s.success && s.position.sideToMove == playerColor, movableColor = playerColor,
                onMove = vm::move, lastMove = s.lastMove,
                arrows = if (s.showSolution && expected != null) listOf(BoardArrow(expected.from, expected.to, Palette.Success.copy(alpha = 0.8f))) else emptyList(),
                showCoordinates = settings.showCoordinates, animate = settings.animations,
            )
            Spacer(Modifier.height(12.dp))
            s.feedback?.let {
                GlassCard(Modifier.fillMaxWidth()) { Text(it, style = MaterialTheme.typography.titleSmall, color = if (s.success) Palette.Success else Palette.TextPrimary) }
                Spacer(Modifier.height(10.dp))
            }
            if (s.showHint) {
                GlassCard(Modifier.fillMaxWidth()) { Text("Indice : ${puzzle.hint}", color = Palette.CyanSoft) }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Indice", vm::hint, icon = Icons.Rounded.Lightbulb, secondary = true, modifier = Modifier.weight(1f))
                GlowButton("Solution", vm::reveal, icon = Icons.Rounded.Visibility, secondary = true, modifier = Modifier.weight(1f), enabled = !s.success)
            }
            Spacer(Modifier.height(10.dp))
            val list = PuzzleSet.puzzles
            val idx = list.indexOfFirst { it.id == puzzle.id }
            if (idx >= 0 && idx < list.size - 1) GlowButton("Exercice suivant", { vm.open(list[idx + 1]) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------- ouvertures

@Composable
fun OpeningsScreen(onBack: () -> Unit, onAnalyze: (Game) -> Unit, onPlayFrom: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val book = remember { runCatching { ctx.container.openings }.getOrNull() }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Opening?>(null) }
    val settings = appSettings()
    val sel = selected
    if (sel != null) {
        val game = remember(sel) { runCatching { Game.fromUci(Position.initial(), sel.uciMoves) }.getOrNull() }
        var ply by remember(sel) { mutableIntStateOf(sel.uciMoves.size) }
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(sel.name, "${sel.eco} · ${sel.family}", onBack = { selected = null })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (game != null) {
                    ChessBoard(
                        position = game.positions[ply], modifier = Modifier.fillMaxWidth(), theme = settings.theme, pieceSet = settings.pieces,
                        lastMove = if (ply > 0) game.moves[ply - 1] else null, showCoordinates = settings.showCoordinates, animate = settings.animations,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Précédent", { if (ply > 0) ply-- }, enabled = ply > 0)
                        RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Suivant", { if (ply < game.ply) ply++ }, enabled = ply < game.ply)
                        Text("Coup $ply / ${game.ply}", color = Palette.TextSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        SectionTitle("Ligne")
                        Text(sel.pgn, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowButton("Analyser", { onAnalyze(game.truncated(ply)) }, secondary = true, modifier = Modifier.weight(1f))
                        GlowButton("Jouer depuis ici", { onPlayFrom(game.positions[ply].toFen(), sel.name) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        return
    }
    val results = remember(query, book) {
        when {
            book == null -> emptyList()
            query.isBlank() -> book.all.filter { it.uciMoves.size <= 6 }.take(300)
            else -> book.search(query).take(300)
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ouvertures", "${book?.all?.size ?: 0} lignes · base Lichess (domaine public)", onBack = onBack)
        OutlinedTextField(
            query, { query = it }, label = { Text("Rechercher (nom, code ECO, ex. « Sicilian », « B20 »)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Violet, cursorColor = Palette.Cyan),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { o ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f))
                        .clickable { selected = o }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(o.eco, Palette.Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name, style = MaterialTheme.typography.bodyMedium)
                        Text(o.pgn, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- finales

@Composable
fun EndgamesScreen(onBack: () -> Unit, onPlay: (fen: String, title: String, goal: String) -> Unit) {
    val settings = appSettings()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Finales", "Jouez contre Stockfish à pleine puissance", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(EndgameSet.endgames) { e ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onPlay(e.fen, e.title, e.goal) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp)) {
                            ChessBoard(Position.fromFen(e.fen), Modifier.fillMaxSize(), theme = settings.theme, pieceSet = settings.pieces, showCoordinates = false, animate = false,
                                flipped = Position.fromFen(e.fen).sideToMove == PieceColor.BLACK)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall)
                            Pill("Objectif : ${e.goal}", if (e.goal.startsWith("Gagner")) Palette.Success else Palette.Cyan)
                            Spacer(Modifier.height(4.dp))
                            Text(e.description, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

/** Petit utilitaire pour lancer un effet une fois. */
@Composable
fun OnFirstComposition(block: suspend () -> Unit) { LaunchedEffect(Unit) { block() } }
