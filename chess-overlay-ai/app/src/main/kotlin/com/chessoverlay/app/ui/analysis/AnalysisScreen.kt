package com.chessoverlay.app.ui.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.TextInputDialog
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Position
import com.chessoverlay.chess.Sq
import com.chessoverlay.engine.EngineScore
import com.chessoverlay.ui.board.BoardArrow
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.EvalBar
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.PulseDot
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.theme.Palette

@Composable
fun AnalysisScreen(vm: AnalysisViewModel, onEdit: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    var showImport by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    // Position affichée : position courante, ou position de la variante prévisualisée.
    val analysis = s.analysis
    val preview = s.preview
    val previewLine = preview?.let { analysis?.lines?.getOrNull(it.lineIndex) }
    val shownPosition: Position
    var shownLastMove: Move? = s.lastMove
    if (previewLine != null && preview != null) {
        var p = s.position
        for (i in 0 until preview.step.coerceAtMost(previewLine.moves.size)) {
            val m = previewLine.moves[i].move
            p = p.playOrNull(m) ?: break
            shownLastMove = m
        }
        shownPosition = p
    } else shownPosition = s.position

    val arrows = mutableListOf<BoardArrow>()
    val marks = mutableListOf<SquareMark>()
    if (settings.showArrows) {
        if (previewLine != null && preview != null) {
            previewLine.moves.getOrNull(preview.step)?.let {
                arrows += BoardArrow(it.from, it.to, Palette.Violet.copy(alpha = 0.85f), 0.19f)
                marks += SquareMark(it.from, Palette.Violet, MarkStyle.RING)
            }
        } else analysis?.lines?.forEachIndexed { i, l ->
            val c = when (i) { 0 -> Palette.Violet.copy(alpha = 0.88f); 1 -> Palette.Cyan.copy(alpha = 0.65f); else -> Color.White.copy(alpha = 0.35f) }
            arrows += BoardArrow(l.first.from, l.first.to, c, if (i == 0) 0.2f else 0.13f)
        }
        analysis?.best?.takeIf { previewLine == null }?.first?.let {
            marks += SquareMark(it.from, Palette.Violet, MarkStyle.RING)
            marks += SquareMark(it.to, Palette.Cyan, MarkStyle.FILL)
        }
    }
    val whiteWin = analysis?.best?.scoreWhite?.winPercent()?.toFloat() ?: 50f
    val evalText = analysis?.best?.scoreWhite?.format() ?: "…"

    val boardBlock: @Composable ColumnScope.() -> Unit = {
        s.opening?.let {
            Text("${it.eco} · ${it.name}", style = MaterialTheme.typography.labelMedium, color = Palette.VioletSoft, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
        }
        EvalBar(whiteWin, evalText)
        Spacer(Modifier.height(8.dp))
        val bestLabel = if (previewLine != null && preview != null) {
            previewLine.moves.getOrNull(preview.step)?.let { "Variante #${preview.lineIndex + 1} · ${it.headline}" } ?: "Fin de la variante"
        } else analysis?.best?.first?.headline
        BestMoveChip(bestLabel, analysis?.best?.scoreWhite)
        Spacer(Modifier.height(8.dp))
        ChessBoard(
            position = shownPosition,
            modifier = Modifier.fillMaxWidth(),
            flipped = s.flipped,
            theme = settings.theme,
            pieceSet = settings.pieces,
            interactive = previewLine == null,
            onMove = vm::play,
            lastMove = shownLastMove,
            arrows = arrows,
            marks = marks,
            showCoordinates = settings.showCoordinates,
            animate = settings.animations,
        )
        Spacer(Modifier.height(10.dp))
        if (previewLine != null && preview != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Coup précédent de la variante", { vm.setPreview(preview.copy(step = (preview.step - 1).coerceAtLeast(0))) })
                RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Coup suivant de la variante", { vm.setPreview(preview.copy(step = (preview.step + 1).coerceAtMost(previewLine.moves.size))) })
                Text("Coup ${preview.step} / ${previewLine.moves.size}", color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text("Quitter", color = Palette.CyanSoft, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.setPreview(null) }.padding(10.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.FirstPage, "Début", { vm.goTo(0) }, enabled = s.cursor > 0)
                RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Coup précédent", vm::back, enabled = s.cursor > 0)
                RoundIconButton(if (s.analyzing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.analyzing) "Arrêter le calcul" else "Reprendre le calcul", vm::togglePause, active = s.analyzing)
                RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Coup suivant", vm::forward, enabled = s.cursor < s.game.ply)
                RoundIconButton(Icons.Rounded.LastPage, "Fin", { vm.goTo(s.game.ply) }, enabled = s.cursor < s.game.ply)
                RoundIconButton(Icons.Rounded.TrendingUp, "Calculer plus profondément", vm::deeper)
            }
        }
        Spacer(Modifier.height(8.dp))
        EngineStatus(s)
        if (s.game.ply > 0) MoveListStrip(s.game.sans, s.cursor, vm::goTo)
    }

    val panelBlock: @Composable ColumnScope.() -> Unit = {
        s.error?.let {
            GlassCard(Modifier.fillMaxWidth()) { Text(it, color = Palette.Danger) }
            Spacer(Modifier.height(10.dp))
        }
        TabRowGlass(listOf("Meilleur coup", "Variantes", "Captures", "Tactique"), tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> BestMovePanel(analysis, s.analyzing, s.captures, s.capturesLoading, settings.frenchNotation)
            1 -> LinesPanel(analysis, s.preview, vm::setPreview, vm::playLine)
            2 -> CapturesPanel(s.captures, s.capturesLoading, s.tactics)
            else -> TacticsPanel(s.tactics)
        }
        Spacer(Modifier.height(24.dp))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Analyse", "Stockfish local · ${if (s.position.sideToMove == com.chessoverlay.chess.PieceColor.WHITE) "trait aux Blancs" else "trait aux Noirs"}") {
                RoundIconButton(Icons.Rounded.UploadFile, "Importer FEN ou PGN", { showImport = true }, size = 42.dp)
                RoundIconButton(Icons.Rounded.SwapVert, "Retourner l'échiquier", vm::flip, size = 42.dp)
                RoundIconButton(Icons.Rounded.Edit, "Modifier la position", { onEdit(s.position.toFen()) }, size = 42.dp)
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val boxHeight = maxHeight
                if (maxWidth > maxHeight && maxWidth > 600.dp) {
                    // Paysage : échiquier à gauche, panneau à droite.
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.widthIn(max = boxHeight - 40.dp).verticalScroll(rememberScrollState()), content = boardBlock)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            ActionsRow(onCopy = { clipboard.setText(AnnotatedString(s.position.toFen())) }, onSave = { showSave = true }, onSaveGame = vm::saveGame)
                            Spacer(Modifier.height(10.dp))
                            panelBlock()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        boardBlock()
                        Spacer(Modifier.height(10.dp))
                        ActionsRow(onCopy = { clipboard.setText(AnnotatedString(s.position.toFen())) }, onSave = { showSave = true }, onSaveGame = vm::saveGame)
                        Spacer(Modifier.height(14.dp))
                        panelBlock()
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showImport) TextInputDialog(
        title = "Importer une position", label = "FEN ou PGN",
        onDismiss = { showImport = false },
        onConfirm = { if (vm.loadText(it)) showImport = false },
    )
    if (showSave) TextInputDialog(
        title = "Enregistrer la position", label = "Nom", singleLine = true, initial = s.opening?.name ?: "",
        onDismiss = { showSave = false },
        onConfirm = { vm.savePosition(it); showSave = false },
    )
}

@Composable
private fun ActionsRow(onCopy: () -> Unit, onSave: () -> Unit, onSaveGame: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipAction(Icons.Rounded.ContentCopy, "Copier la FEN", onCopy)
        ChipAction(Icons.Rounded.Bookmark, "Enregistrer la position", onSave)
        ChipAction(Icons.Rounded.Save, "Enregistrer la partie", onSaveGame)
    }
}

@Composable
fun ChipAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Palette.GraphiteLine, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = Palette.CyanSoft, modifier = Modifier.height(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BestMoveChip(label: String?, score: EngineScore?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Violet.copy(alpha = 0.14f))
            .border(1.dp, Palette.Violet.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label ?: "Calcul du meilleur coup…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        if (score != null) Pill(score.format(), scoreColor(score))
    }
}

@Composable
private fun EngineStatus(s: AnalysisUiState) {
    val a = s.analysis
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulseDot(active = s.analyzing)
        Spacer(Modifier.width(8.dp))
        val txt = when {
            a == null && s.analyzing -> "Stockfish démarre…"
            a == null -> "Moteur en attente"
            else -> buildString {
                append("Profondeur ").append(a.depth)
                if (a.nps > 0) append(" · ").append(String.format(java.util.Locale.FRANCE, "%.1f", a.nps / 1_000_000.0)).append(" M nœuds/s")
                append(" · ").append(String.format(java.util.Locale.FRANCE, "%,d", a.nodes)).append(" nœuds")
                if (!s.analyzing) append(" · arrêté")
            }
        }
        Text(txt, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
    }
}

@Composable
private fun MoveListStrip(sans: List<String>, cursor: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sans.forEachIndexed { i, san ->
            val ply = i + 1
            val label = if (i % 2 == 0) "${i / 2 + 1}. $san" else san
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (ply == cursor) Color.White else Palette.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (ply == cursor) Palette.Violet.copy(alpha = 0.45f) else Color.Transparent)
                    .clickable { onSelect(ply) }.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun TabRowGlass(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Palette.GraphiteLine, RoundedCornerShape(16.dp)).padding(4.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Palette.Violet.copy(alpha = 0.40f) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Palette.TextSecondary, maxLines = 1)
            }
        }
    }
}
