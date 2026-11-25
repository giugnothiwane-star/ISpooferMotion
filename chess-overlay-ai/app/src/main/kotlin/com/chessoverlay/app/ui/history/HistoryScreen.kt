package com.chessoverlay.app.ui.history

import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chessoverlay.app.container
import com.chessoverlay.app.ui.analysis.TabRowGlass
import com.chessoverlay.app.ui.common.ConfirmDialog
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.formatDate
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.chess.Game
import com.chessoverlay.chess.Pgn
import com.chessoverlay.chess.Position
import com.chessoverlay.data.SavedGameEntity
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onOpenFen: (String) -> Unit,
    onReview: (Game) -> Unit,
    onReplay: (SavedGameEntity) -> Unit,
) {
    val ctx = LocalContext.current
    val db = ctx.container.database
    val scope = rememberCoroutineScope()
    val games by db.games().observeAll().collectAsStateWithLifecycle(emptyList())
    val positions by db.positions().observeAll().collectAsStateWithLifecycle(emptyList())
    val analyses by db.analyses().observeRecent().collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val settings = appSettings()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Historique", "${games.size} parties · ${positions.size} positions · ${analyses.size} analyses") {
            RoundIconButton(Icons.Rounded.DeleteSweep, "Effacer cet onglet", { confirmClear = true }, size = 42.dp)
        }
        Column(Modifier.padding(horizontal = 16.dp)) { TabRowGlass(listOf("Parties", "Positions", "Analyses"), tab) { tab = it } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> {
                    if (games.isEmpty()) item { Empty("Aucune partie enregistrée. Les parties d'entraînement sont sauvegardées automatiquement.") }
                    items(games, key = { it.id }) { g ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(g.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${g.white} – ${g.black} · ${g.result} · ${g.plyCount} demi-coups", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                                    Text("${g.source} · ${formatDate(g.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                }
                                Pill(g.result, Palette.Cyan)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RoundIconButton(Icons.Rounded.Replay, "Rejouer", { onReplay(g) }, size = 40.dp)
                                RoundIconButton(Icons.Rounded.Insights, "Analyser la partie", { runCatching { Pgn.parse(g.pgn) }.getOrNull()?.let(onReview) }, size = 40.dp)
                                RoundIconButton(Icons.Rounded.Share, "Partager le PGN", {
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, g.pgn), "Partager la partie"))
                                }, size = 40.dp)
                                Spacer(Modifier.weight(1f))
                                RoundIconButton(Icons.Rounded.Delete, "Supprimer", { scope.launch(Dispatchers.IO) { db.games().delete(g) } }, size = 40.dp)
                            }
                        }
                    }
                }
                1 -> {
                    if (positions.isEmpty()) item { Empty("Aucune position enregistrée. Utilisez « Enregistrer la position » dans l'écran d'analyse.") }
                    items(positions, key = { it.id }) { p ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenFen(p.fen) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(72.dp)) {
                                    Position.fromFenOrNull(p.fen)?.let { ChessBoard(it, Modifier.fillMaxSize(), theme = settings.theme, pieceSet = settings.pieces, showCoordinates = false, animate = false) }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text(if (p.category == "erreur") "Exercice issu d'une erreur" else formatDate(p.createdAt), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                }
                                RoundIconButton(Icons.Rounded.Delete, "Supprimer", { scope.launch(Dispatchers.IO) { db.positions().delete(p) } }, size = 40.dp)
                            }
                        }
                    }
                }
                else -> {
                    if (analyses.isEmpty()) item { Empty(if (settings.keepHistory) "Aucune analyse pour l'instant." else "L'historique des analyses est désactivé dans les réglages.") }
                    items(analyses, key = { it.id }) { a ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenFen(a.fen) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.bestMoveText, style = MaterialTheme.typography.titleSmall)
                                    Text("Profondeur ${a.depth} · ${formatDate(a.createdAt)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                }
                                Pill(a.evaluation, Palette.Violet)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog(
        "Tout effacer ?", "Cette action supprime définitivement le contenu de l'onglet sélectionné.", "Effacer",
        onDismiss = { confirmClear = false },
    ) {
        confirmClear = false
        scope.launch(Dispatchers.IO) {
            when (tab) { 0 -> db.games().clear(); 1 -> db.positions().clear(); else -> db.analyses().clear() }
        }
    }
}

@Composable
private fun Empty(text: String) {
    GlassCard(Modifier.fillMaxWidth()) { Text(text, color = Palette.TextMuted) }
}

/** Relecture d'une partie enregistrée, avec lecture automatique. */
@Composable
fun ReplayScreen(entity: SavedGameEntity, onBack: () -> Unit, onAnalyze: (Game, Int) -> Unit) {
    val game = remember(entity.id) { runCatching { Pgn.parse(entity.pgn) }.getOrNull() }
    val settings = appSettings()
    var ply by remember(entity.id) { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    if (game == null) {
        Column { ScreenHeader("Relecture", "PGN illisible", onBack = onBack) }
        return
    }
    LaunchedEffect(auto) {
        while (auto && ply < game.ply) { delay(900); ply++ }
        auto = false
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(entity.title, "${entity.white} – ${entity.black} · ${entity.result}", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ChessBoard(game.positions[ply], Modifier.fillMaxWidth(), theme = settings.theme, pieceSet = settings.pieces,
                lastMove = if (ply > 0) game.moves[ply - 1] else null, showCoordinates = settings.showCoordinates, animate = settings.animations)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.KeyboardArrowLeft, "Précédent", { if (ply > 0) ply-- }, enabled = ply > 0)
                RoundIconButton(if (auto) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Lecture automatique", { auto = !auto }, active = auto)
                RoundIconButton(Icons.Rounded.KeyboardArrowRight, "Suivant", { if (ply < game.ply) ply++ }, enabled = ply < game.ply)
                RoundIconButton(Icons.Rounded.Insights, "Analyser cette position", { onAnalyze(game, ply) })
                Text("$ply / ${game.ply}", color = Palette.TextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(game.sans.chunked(2).mapIndexed { i, p -> "${i + 1}. ${p.joinToString(" ")}" }.joinToString("  "), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
