package com.chessoverlay.app.ui.recognition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import com.chessoverlay.app.vision.RecognitionSession
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecognitionUiState(
    val loading: Boolean = false,
    val session: RecognitionSession? = null,
    val error: String? = null,
    val sideToMove: PieceColor = PieceColor.WHITE,
    val lastUri: Uri? = null,
)

class RecognitionViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RecognitionUiState())
    val state: StateFlow<RecognitionUiState> = _state

    fun recognize(uri: Uri, forceWhiteAtBottom: Boolean? = null) {
        _state.update { it.copy(loading = true, error = null, lastUri = uri) }
        viewModelScope.launch {
            try {
                val s = app.recognition.recognizeUri(uri, forceWhiteAtBottom)
                _state.update { it.copy(loading = false, session = s, sideToMove = s.result.suggestedSideToMove ?: PieceColor.WHITE) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, session = null, error = e.message ?: "Reconnaissance impossible.") }
            }
        }
    }

    /** Relance la reconnaissance en inversant l'orientation détectée. */
    fun flipOrientation() {
        val s = _state.value
        val uri = s.lastUri ?: return
        val current = s.session?.result?.whiteAtBottom ?: true
        recognize(uri, forceWhiteAtBottom = !current)
    }

    fun setSide(c: PieceColor) = _state.update { it.copy(sideToMove = c) }

    fun resetLearning() {
        viewModelScope.launch {
            app.recognition.resetTemplates()
            _state.value.lastUri?.let { recognize(it) }
        }
    }
}

@Composable
fun RecognitionScreen(
    vm: RecognitionViewModel,
    onBack: () -> Unit,
    onEdit: (RecognitionSession, PieceColor) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings = appSettings()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.recognize(it) } }
    var showOriginal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reconnaissance", "Capture d'écran → position d'échecs", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth(), glow = s.session == null) {
                Text("Importer une capture d'écran", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choisissez une image contenant un échiquier (application, site, livre numérique). " +
                        "Vous pouvez aussi partager une capture vers Chess Overlay AI depuis la galerie.",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                GlowButton("Choisir une image", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Rounded.Image, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            if (s.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyse de l'image : recherche de l'échiquier, des 64 cases et des pièces…", color = Palette.TextSecondary)
                }
            }
            s.error?.let {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Échiquier non reconnu", style = MaterialTheme.typography.titleSmall, color = Palette.Danger)
                    Text(it, color = Palette.TextSecondary)
                }
            }
            val session = s.session
            if (session != null && !s.loading) {
                val r = session.result
                TabRowGlass(listOf("Position détectée", "Image source"), if (showOriginal) 1 else 0) { showOriginal = it == 1 }
                Spacer(Modifier.height(10.dp))
                if (showOriginal) {
                    SourceImage(session)
                } else {
                    ChessBoard(
                        position = r.toPosition(s.sideToMove),
                        modifier = Modifier.fillMaxWidth(),
                        flipped = !r.whiteAtBottom,
                        theme = settings.theme, pieceSet = settings.pieces,
                        marks = r.lowConfidenceSquares().map { SquareMark(it, Palette.Warning, MarkStyle.RING) } +
                            listOfNotNull(r.lastMove?.first, r.lastMove?.second).map { SquareMark(it, Palette.Cyan, MarkStyle.FILL) },
                        animate = false,
                    )
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Résultat")
                    Text("Confiance globale : ${(r.overallConfidence * 100).toInt()} %", style = MaterialTheme.typography.titleSmall,
                        color = if (r.overallConfidence > 0.8f) Palette.Success else Palette.Warning)
                    Text("Orientation : ${if (r.whiteAtBottom) "Blancs en bas" else "Noirs en bas"}", color = Palette.TextSecondary)
                    if (r.calibratedFromStartPosition) Text("Position initiale reconnue : le jeu de pièces de cette image a été appris automatiquement.", color = Palette.CyanSoft)
                    val low = r.lowConfidenceSquares()
                    if (low.isNotEmpty()) Text("${low.size} case(s) incertaine(s), encadrées en orange : vérifiez-les.", color = Palette.Warning)
                    r.warnings.forEach { Text("• $it", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    r.suggestedSideToMove?.let { Text("Trait suggéré d'après le dernier coup surligné : ${if (it == PieceColor.WHITE) "Blancs" else "Noirs"}", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(10.dp))
                    Text("Camp au trait", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    Spacer(Modifier.height(4.dp))
                    TabRowGlass(listOf("Blancs", "Noirs"), if (s.sideToMove == PieceColor.WHITE) 0 else 1) { vm.setSide(if (it == 0) PieceColor.WHITE else PieceColor.BLACK) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton("Orientation", vm::flipOrientation, icon = Icons.Rounded.SwapVert, secondary = true, modifier = Modifier.weight(1f))
                    GlowButton("Réapprendre", vm::resetLearning, icon = Icons.Rounded.RestartAlt, secondary = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                // Pas d'analyse directe : la position passe toujours par l'éditeur pour être vérifiée.
                GlowButton("Vérifier, corriger puis analyser", { onEdit(session, s.sideToMove) }, icon = Icons.Rounded.Edit, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "Par sécurité, l'analyse n'est jamais lancée automatiquement : confirmez la position dans l'éditeur.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SourceImage(session: RecognitionSession) {
    val bmp = session.bitmap
    val rect = session.result.boardRect
    Box(Modifier.fillMaxWidth().heightIn(max = 520.dp).aspectRatio(bmp.width.toFloat() / bmp.height).clip(RoundedCornerShape(14.dp))) {
        Image(bmp.asImageBitmap(), "Image importée", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / bmp.width
            drawRect(Palette.Cyan, Offset(rect.left * k, rect.top * k), Size(rect.size * k, rect.size * k), style = Stroke(3.dp.toPx()))
        }
        Icons.Rounded.Insights
    }
}
