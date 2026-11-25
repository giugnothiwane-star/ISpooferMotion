package com.chessoverlay.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chessoverlay.app.AppContainer
import com.chessoverlay.app.ui.analysis.TabRowGlass
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.TextInputDialog
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.app.vision.RecognitionSession
import com.chessoverlay.chess.CastlingRights
import com.chessoverlay.chess.ChessFormatException
import com.chessoverlay.chess.Piece
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.chess.PieceType
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val board: List<Piece?> = Position.initial().boardCopy().toList(),
    val sideToMove: PieceColor = PieceColor.WHITE,
    val wk: Boolean = true, val wq: Boolean = true, val bk: Boolean = true, val bq: Boolean = true,
    val epSquare: Int = Sq.NONE,
    /** Outil sélectionné : une pièce à poser, ou null pour la gomme. */
    val tool: Piece? = Piece.of(PieceType.PAWN, PieceColor.WHITE),
    val flipped: Boolean = false,
    val session: RecognitionSession? = null,
    val lowConfidence: Set<Int> = emptySet(),
    val error: String? = null,
) {
    val position: Position
        get() = Position.create(board.toTypedArray(), sideToMove, CastlingRights(wk, wq, bk, bq), epSquare)
    val errors: List<String> get() = position.validationErrors()
}

class EditorViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    fun load(fen: String) {
        val p = Position.fromFenOrNull(fen) ?: return
        _state.value = fromPosition(p, null, emptySet(), flipped = false)
    }

    /** Ouvre l'éditeur sur une position reconnue, en signalant les cases peu sûres. */
    fun loadRecognition(session: RecognitionSession, side: PieceColor) {
        val p = session.result.toPosition(side)
        _state.value = fromPosition(p, session, session.result.lowConfidenceSquares().toSet(), flipped = !session.result.whiteAtBottom)
    }

    private fun fromPosition(p: Position, session: RecognitionSession?, low: Set<Int>, flipped: Boolean) = EditorState(
        board = p.boardCopy().toList(), sideToMove = p.sideToMove,
        wk = p.castling.whiteKingSide, wq = p.castling.whiteQueenSide, bk = p.castling.blackKingSide, bq = p.castling.blackQueenSide,
        epSquare = p.epSquare, session = session, lowConfidence = low, flipped = flipped,
    )

    fun tap(sq: Int) = _state.update { s ->
        val b = s.board.toMutableList()
        b[sq] = if (s.tool == null || b[sq] == s.tool) null else s.tool
        s.copy(board = b, lowConfidence = s.lowConfidence - sq, epSquare = Sq.NONE)
    }

    fun selectTool(p: Piece?) = _state.update { it.copy(tool = p) }
    fun clear() = _state.update { it.copy(board = List(64) { null }, epSquare = Sq.NONE, lowConfidence = emptySet()) }
    fun reset() = _state.update { fromPosition(Position.initial(), it.session, emptySet(), it.flipped) }
    fun flip() = _state.update { it.copy(flipped = !it.flipped) }
    fun side(c: PieceColor) = _state.update { it.copy(sideToMove = c, epSquare = Sq.NONE) }
    fun castling(wk: Boolean? = null, wq: Boolean? = null, bk: Boolean? = null, bq: Boolean? = null) = _state.update {
        it.copy(wk = wk ?: it.wk, wq = wq ?: it.wq, bk = bk ?: it.bk, bq = bq ?: it.bq)
    }

    fun setFen(fen: String): Boolean = try {
        val p = Position.fromFen(fen)
        _state.update { fromPosition(p, it.session, emptySet(), it.flipped) }
        true
    } catch (e: ChessFormatException) {
        _state.update { it.copy(error = e.message) }
        false
    }

    /** Valide la position ; si elle provient d'une reconnaissance, les corrections servent d'apprentissage. */
    fun confirm(onDone: (String) -> Unit) {
        val s = _state.value
        if (s.errors.isNotEmpty()) return
        val fen = s.position.toFen()
        val session = s.session
        if (session != null) {
            app.scope.launch { runCatching { app.recognition.learnFromCorrection(session, s.board.toTypedArray()) } }
        }
        onDone(fen)
    }
}

@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onAnalyze: (String) -> Unit, onPlay: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    var showFen by remember { mutableStateOf(false) }
    val errors = s.errors
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Éditeur de position",
            if (s.session != null) "Vérifiez et corrigez la position reconnue" else "Créez une position personnalisée",
            onBack = onBack,
        ) {
            RoundIconButton(Icons.Rounded.SwapVert, "Retourner", vm::flip, size = 42.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (s.session != null) {
                val r = s.session!!.result
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Reconnaissance : confiance ${(r.overallConfidence * 100).toInt()} %", style = MaterialTheme.typography.titleSmall)
                    if (s.lowConfidence.isNotEmpty()) Text(
                        "Cases à vérifier (encadrées en orange) : " + s.lowConfidence.sorted().joinToString { Sq.upperName(it) },
                        color = Palette.Warning, style = MaterialTheme.typography.bodySmall,
                    )
                    r.warnings.forEach { Text("• $it", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    Text("Vos corrections amélioreront les prochaines reconnaissances.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
            }
            ChessBoard(
                position = s.position,
                modifier = Modifier.fillMaxWidth(),
                flipped = s.flipped,
                theme = settings.theme,
                pieceSet = settings.pieces,
                marks = s.lowConfidence.map { SquareMark(it, Palette.Warning, MarkStyle.RING) },
                showCoordinates = true,
                animate = false,
                onSquareClick = vm::tap,
            )
            Spacer(Modifier.height(12.dp))
            PiecePalette(s.tool, settings.pieces, vm::selectTool)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowButton("Vider", vm::clear, icon = Icons.Rounded.CleaningServices, secondary = true, modifier = Modifier.weight(1f))
                GlowButton("Initiale", vm::reset, icon = Icons.Rounded.RestartAlt, secondary = true, modifier = Modifier.weight(1f))
                GlowButton("FEN", { showFen = true }, icon = Icons.Rounded.AutoFixHigh, secondary = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Trait")
                TabRowGlass(listOf("Blancs", "Noirs"), if (s.sideToMove == PieceColor.WHITE) 0 else 1) {
                    vm.side(if (it == 0) PieceColor.WHITE else PieceColor.BLACK)
                }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Droits au roque")
                Row { CastleBox("Blancs O-O", s.wk) { vm.castling(wk = it) }; CastleBox("Blancs O-O-O", s.wq) { vm.castling(wq = it) } }
                Row { CastleBox("Noirs O-O", s.bk) { vm.castling(bk = it) }; CastleBox("Noirs O-O-O", s.bq) { vm.castling(bq = it) } }
                Text("Les droits incompatibles avec le placement du roi et des tours sont ignorés automatiquement.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text("FEN", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Text(s.position.toFen(), style = MaterialTheme.typography.bodySmall)
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    errors.forEach { Text("⚠ $it", color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
                }
                s.error?.let { Text(it, color = Palette.Danger, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton("Jouer contre Stockfish", { vm.confirm(onPlay) }, enabled = errors.isEmpty(), secondary = true, modifier = Modifier.weight(1f))
                GlowButton(if (s.session != null) "Confirmer et analyser" else "Analyser", { vm.confirm(onAnalyze) }, enabled = errors.isEmpty(), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (showFen) TextInputDialog("Saisir une FEN", "FEN", initial = s.position.toFen(), onDismiss = { showFen = false }) {
        if (vm.setFen(it)) showFen = false
    }
}

@Composable
private fun CastleBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(end = 12.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Palette.Violet))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PiecePalette(tool: Piece?, set: com.chessoverlay.ui.board.PieceSet, onSelect: (Piece?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in PieceColor.entries) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)) {
                    val p = Piece.of(t, c)
                    ToolCell(tool == p, Modifier.weight(1f), { onSelect(p) }) {
                        Image(painterResource(set.drawable(p)), p.frenchName, Modifier.fillMaxSize().padding(4.dp))
                    }
                }
                if (c == PieceColor.WHITE) ToolCell(tool == null, Modifier.weight(1f), { onSelect(null) }) {
                    Icon(Icons.Rounded.Delete, "Gomme", tint = Palette.TextSecondary, modifier = Modifier.align(Alignment.Center))
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCell(selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.Violet.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) Palette.Violet else Palette.GraphiteLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        content = content,
    )
}
