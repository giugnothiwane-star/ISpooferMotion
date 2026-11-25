package com.chessoverlay.app.ui.recognition

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.chessoverlay.app.ui.training.sliderColors
import com.chessoverlay.app.util.DiagnosticLog
import com.chessoverlay.app.vision.RecognitionSession
import com.chessoverlay.chess.PieceColor
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.MarkStyle
import com.chessoverlay.ui.board.SquareMark
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette
import com.chessoverlay.vision.BoardRect
import com.chessoverlay.vision.RecognitionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecognitionUiState(
    val loading: Boolean = false,
    val session: RecognitionSession? = null,
    /** Image en cours (importée ou capturée), disponible même si la reconnaissance a échoué. */
    val image: Bitmap? = null,
    val error: String? = null,
    val sideToMove: PieceColor = PieceColor.WHITE,
    /** Mode cadrage manuel : l'utilisateur place lui-même le cadre de l'échiquier. */
    val framing: Boolean = false,
    val frame: BoardRect? = null,
)

class RecognitionViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RecognitionUiState())
    val state: StateFlow<RecognitionUiState> = _state

    fun recognize(uri: Uri) {
        _state.update { it.copy(loading = true, error = null, framing = false) }
        viewModelScope.launch {
            try {
                val s = app.recognition.recognizeUri(uri)
                onSuccess(s)
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    /** Reprend la dernière capture du menu flottant (par exemple après un échec de reconnaissance). */
    fun openLastCapture(error: String?) {
        val session = app.recognition.session.value
        val img = app.recognition.lastImage.value
        when {
            error == null && session != null -> onSuccess(session)
            img != null -> _state.update {
                RecognitionUiState(image = img, error = error, framing = true, frame = defaultFrame(img))
            }
        }
    }

    private fun run(bitmap: Bitmap, forceWhiteAtBottom: Boolean? = null, hint: BoardRect? = null) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                onSuccess(app.recognition.recognize(bitmap, forceWhiteAtBottom, hint))
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private fun onSuccess(s: RecognitionSession) = _state.update {
        it.copy(loading = false, session = s, image = s.bitmap, error = null, framing = false, frame = s.result.boardRect,
            sideToMove = s.result.suggestedSideToMove ?: if (s.result.whiteAtBottom) PieceColor.WHITE else PieceColor.BLACK)
    }

    private fun onFailure(e: Exception) {
        val msg = if (e is RecognitionException) e.message!! else "Reconnaissance impossible (${DiagnosticLog.record("reconnaissance", e)})."
        val img = app.recognition.lastImage.value
        // Échec : on propose directement le cadrage manuel sur l'image.
        _state.update { it.copy(loading = false, session = null, image = img, error = msg, framing = img != null, frame = img?.let(::defaultFrame)) }
    }

    private fun defaultFrame(b: Bitmap): BoardRect {
        val size = minOf(b.width, b.height) * 0.94f
        return BoardRect((b.width - size) / 2f, (b.height - size) / 2f, size)
    }

    fun startFraming() = _state.update { s -> s.copy(framing = true, frame = s.frame ?: s.image?.let(::defaultFrame)) }
    fun cancelFraming() = _state.update { it.copy(framing = false) }
    fun setFrame(r: BoardRect) = _state.update { it.copy(frame = r) }

    fun recognizeInFrame() {
        val s = _state.value
        val img = s.image ?: return
        run(img, hint = s.frame)
    }

    /** Relance la reconnaissance en inversant l'orientation détectée. */
    fun flipOrientation() {
        val s = _state.value
        val img = s.image ?: return
        val current = s.session?.result?.whiteAtBottom ?: true
        run(img, forceWhiteAtBottom = !current, hint = s.session?.result?.boardRect)
    }

    fun setSide(c: PieceColor) = _state.update { it.copy(sideToMove = c) }

    fun resetLearning() {
        viewModelScope.launch {
            app.recognition.resetTemplates()
            _state.value.image?.let { run(it) }
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
            if (s.image == null) {
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
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
            }
            if (s.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Analyse de l'image : recherche de l'échiquier, des 64 cases et des pièces…", color = Palette.TextSecondary)
                }
            }
            s.error?.let {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Échiquier non reconnu automatiquement", style = MaterialTheme.typography.titleSmall, color = Palette.Warning)
                    Text(it, color = Palette.TextSecondary)
                    if (s.image != null) Text(
                        "Placez le cadre bleu sur l'échiquier (glissez pour déplacer, curseur pour la taille), puis « Reconnaître dans ce cadre ».",
                        color = Palette.CyanSoft, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            val image = s.image
            if (image != null && s.framing && !s.loading) {
                FramingEditor(image, s.frame ?: BoardRect(0f, 0f, minOf(image.width, image.height).toFloat()), vm::setFrame)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.session != null) GlowButton("Annuler", vm::cancelFraming, secondary = true, modifier = Modifier.weight(1f))
                    GlowButton("Reconnaître dans ce cadre", vm::recognizeInFrame, icon = Icons.Rounded.CropFree, modifier = Modifier.weight(2f))
                }
                Spacer(Modifier.height(10.dp))
                GlowButton("Choisir une autre image", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Rounded.Image, secondary = true, modifier = Modifier.fillMaxWidth())
            }
            val session = s.session
            if (session != null && !s.loading && !s.framing) {
                val r = session.result
                TabRowGlass(listOf("Position détectée", "Image source"), if (showOriginal) 1 else 0) { showOriginal = it == 1 }
                Spacer(Modifier.height(10.dp))
                if (showOriginal) {
                    SourceImage(session.bitmap, r.boardRect)
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
                    Spacer(Modifier.height(10.dp))
                    Text("Camp au trait", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    Spacer(Modifier.height(4.dp))
                    TabRowGlass(listOf("Blancs", "Noirs"), if (s.sideToMove == PieceColor.WHITE) 0 else 1) { vm.setSide(if (it == 0) PieceColor.WHITE else PieceColor.BLACK) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton("Orientation", vm::flipOrientation, icon = Icons.Rounded.SwapVert, secondary = true, modifier = Modifier.weight(1f))
                    GlowButton("Recadrer", vm::startFraming, icon = Icons.Rounded.CropFree, secondary = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                GlowButton("Réapprendre les pièces", vm::resetLearning, icon = Icons.Rounded.RestartAlt, secondary = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                // Pas d'analyse directe : la position passe toujours par l'éditeur pour être vérifiée.
                GlowButton("Vérifier, corriger puis analyser", { onEdit(session, s.sideToMove) }, icon = Icons.Rounded.Edit, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "Par sécurité, l'analyse n'est jamais lancée automatiquement : confirmez la position dans l'éditeur. " +
                        "Vos corrections apprennent à l'application le jeu de pièces de votre site.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                GlowButton("Choisir une autre image", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Rounded.Image, secondary = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Image avec un cadre carré déplaçable (glisser) et redimensionnable (curseur). */
@Composable
private fun FramingEditor(bitmap: Bitmap, frame: BoardRect, onChange: (BoardRect) -> Unit) {
    val current by rememberUpdatedState(frame)
    val onChangeState by rememberUpdatedState(onChange)
    val maxSide = minOf(bitmap.width, bitmap.height).toFloat()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val k = widthPx / bitmap.width
        // Hauteur affichée limitée : l'image est mise à l'échelle de la largeur disponible.
        val heightDp = with(density) { (bitmap.height * k).toDp() }
        Box(Modifier.fillMaxWidth().height(heightDp).clip(RoundedCornerShape(14.dp))) {
            Image(bitmap.asImageBitmap(), "Image à recadrer", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(
                Modifier.fillMaxSize().pointerInput(bitmap) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val f = current
                        onChangeState(
                            f.copy(
                                left = (f.left + drag.x / k).coerceIn(0f, bitmap.width - f.size),
                                top = (f.top + drag.y / k).coerceIn(0f, bitmap.height - f.size),
                            ),
                        )
                    }
                },
            ) {
                val tl = Offset(frame.left * k, frame.top * k)
                val sz = frame.size * k
                drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, Size(size.width, tl.y))
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, tl.y + sz), Size(size.width, size.height - tl.y - sz))
                drawRect(Palette.Cyan, tl, Size(sz, sz), style = Stroke(3.dp.toPx()))
                // Grille 8×8 pour aligner les cases.
                for (i in 1..7) {
                    val d = sz * i / 8
                    drawLine(Palette.Cyan.copy(alpha = 0.45f), Offset(tl.x + d, tl.y), Offset(tl.x + d, tl.y + sz), 1.dp.toPx())
                    drawLine(Palette.Cyan.copy(alpha = 0.45f), Offset(tl.x, tl.y + d), Offset(tl.x + sz, tl.y + d), 1.dp.toPx())
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Taille du cadre", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
    Slider(
        value = frame.size, valueRange = (maxSide * 0.2f)..maxSide, colors = sliderColors(),
        onValueChange = { v ->
            val cx = frame.left + frame.size / 2; val cy = frame.top + frame.size / 2
            onChange(BoardRect((cx - v / 2).coerceIn(0f, bitmap.width - v), (cy - v / 2).coerceIn(0f, bitmap.height - v), v))
        },
    )
}

@Composable
private fun SourceImage(bmp: Bitmap, rect: BoardRect) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val k = with(density) { maxWidth.toPx() } / bmp.width
        val heightDp = with(density) { (bmp.height * k).toDp() }
        Box(Modifier.fillMaxWidth().height(heightDp).clip(RoundedCornerShape(14.dp))) {
            Image(bmp.asImageBitmap(), "Image importée", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Palette.Cyan, Offset(rect.left * k, rect.top * k), Size(rect.size * k, rect.size * k), style = Stroke(3.dp.toPx()))
            }
        }
    }
}
