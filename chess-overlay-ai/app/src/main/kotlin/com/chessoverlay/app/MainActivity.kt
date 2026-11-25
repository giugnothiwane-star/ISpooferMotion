package com.chessoverlay.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chessoverlay.app.ui.analysis.AnalysisScreen
import com.chessoverlay.app.ui.analysis.AnalysisViewModel
import com.chessoverlay.app.ui.common.TextInputDialog
import com.chessoverlay.app.ui.editor.EditorScreen
import com.chessoverlay.app.ui.editor.EditorViewModel
import com.chessoverlay.app.ui.history.HistoryScreen
import com.chessoverlay.app.ui.history.ReplayScreen
import com.chessoverlay.app.ui.home.HomeScreen
import com.chessoverlay.app.ui.recognition.RecognitionScreen
import com.chessoverlay.app.ui.recognition.RecognitionViewModel
import com.chessoverlay.app.ui.review.ReviewScreen
import com.chessoverlay.app.ui.review.ReviewViewModel
import com.chessoverlay.app.ui.settings.AboutScreen
import com.chessoverlay.app.ui.settings.SettingsScreen
import com.chessoverlay.app.ui.training.EndgamesScreen
import com.chessoverlay.app.ui.training.OpeningsScreen
import com.chessoverlay.app.ui.training.PlayScreen
import com.chessoverlay.app.ui.training.PlayViewModel
import com.chessoverlay.app.ui.training.PuzzleViewModel
import com.chessoverlay.app.ui.training.PuzzlesScreen
import com.chessoverlay.app.ui.training.TrainingHubScreen
import com.chessoverlay.data.SavedGameEntity
import com.chessoverlay.ui.components.AppBackground
import com.chessoverlay.ui.theme.ChessOverlayTheme
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingIntent.value = intent
        setContent {
            ChessOverlayTheme {
                AppBackground(Modifier.fillMaxSize()) {
                    AppRoot(pendingIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    companion object {
        const val EXTRA_FEN = "com.chessoverlay.extra.FEN"
        const val EXTRA_EDIT_RECOGNITION = "com.chessoverlay.extra.EDIT_RECOGNITION"
        const val EXTRA_SIDE_WHITE = "com.chessoverlay.extra.SIDE_WHITE"
    }
}

private object Routes {
    const val HOME = "home"; const val ANALYSIS = "analysis"; const val TRAINING = "training"
    const val HISTORY = "history"; const val SETTINGS = "settings"; const val RECOGNITION = "recognition"
    const val EDITOR = "editor"; const val PLAY = "play"; const val PUZZLES = "puzzles"; const val OPENINGS = "openings"
    const val ENDGAMES = "endgames"; const val REVIEW = "review"; const val REPLAY = "replay"; const val ABOUT = "about"
}

@Composable
private fun AppRoot(pendingIntent: MutableStateFlow<Intent?>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.container
    val nav = rememberNavController()
    val analysisVm: AnalysisViewModel = viewModel { AnalysisViewModel(app) }
    val editorVm: EditorViewModel = viewModel { EditorViewModel(app) }
    val recognitionVm: RecognitionViewModel = viewModel { RecognitionViewModel(app) }
    val playVm: PlayViewModel = viewModel { PlayViewModel(app) }
    val puzzleVm: PuzzleViewModel = viewModel { PuzzleViewModel(app) }
    val reviewVm: ReviewViewModel = viewModel { ReviewViewModel(app) }
    var replay by remember { mutableStateOf<SavedGameEntity?>(null) }
    var showImport by remember { mutableStateOf(false) }

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    // Intentions entrantes : partage d'image / de texte, ouverture depuis le menu flottant.
    val incoming by pendingIntent.collectAsState()
    LaunchedEffect(incoming) {
        val i = incoming ?: return@LaunchedEffect
        pendingIntent.value = null
        when {
            i.getBooleanExtra(MainActivity.EXTRA_EDIT_RECOGNITION, false) -> {
                app.recognition.session.value?.let { s ->
                    val side = if (i.getBooleanExtra(MainActivity.EXTRA_SIDE_WHITE, true)) com.chessoverlay.chess.PieceColor.WHITE else com.chessoverlay.chess.PieceColor.BLACK
                    editorVm.loadRecognition(s, side); go(Routes.EDITOR)
                }
            }
            i.hasExtra(MainActivity.EXTRA_FEN) -> { if (analysisVm.loadFen(i.getStringExtra(MainActivity.EXTRA_FEN)!!)) go(Routes.ANALYSIS) }
            i.action == Intent.ACTION_SEND && i.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { recognitionVm.recognize(it); go(Routes.RECOGNITION) }
            }
            i.action == Intent.ACTION_SEND && i.type == "text/plain" -> {
                i.getStringExtra(Intent.EXTRA_TEXT)?.let { if (analysisVm.loadText(it)) go(Routes.ANALYSIS) }
            }
            i.action == Intent.ACTION_VIEW && i.data != null -> {
                runCatching { ctx.contentResolver.openInputStream(i.data!!)?.bufferedReader()?.readText() }.getOrNull()
                    ?.let { if (analysisVm.loadText(it)) go(Routes.ANALYSIS) }
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val topLevel = route in setOf(Routes.HOME, Routes.ANALYSIS, Routes.TRAINING, Routes.HISTORY, Routes.SETTINGS)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f).then(if (topLevel) Modifier else Modifier.navigationBarsPadding())) {
            AppNavHost(nav, analysisVm, editorVm, recognitionVm, playVm, puzzleVm, reviewVm, replay, { replay = it }, { showImport = true })
        }
        if (topLevel) BottomBar(route) { r ->
            nav.navigate(r) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    if (showImport) TextInputDialog("Importer une position ou une partie", "FEN ou PGN", onDismiss = { showImport = false }) {
        if (analysisVm.loadText(it)) { showImport = false; go(Routes.ANALYSIS) }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    analysisVm: AnalysisViewModel,
    editorVm: EditorViewModel,
    recognitionVm: RecognitionViewModel,
    playVm: PlayViewModel,
    puzzleVm: PuzzleViewModel,
    reviewVm: ReviewViewModel,
    replay: SavedGameEntity?,
    setReplay: (SavedGameEntity) -> Unit,
    onImport: () -> Unit,
) {
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.HOME, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
        composable(Routes.HOME) {
            HomeScreen(onAnalyze = { go(Routes.ANALYSIS) }, onRecognize = { go(Routes.RECOGNITION) }, onTraining = { go(Routes.TRAINING) }, onImport = onImport)
        }
        composable(Routes.ANALYSIS) {
            AnalysisScreen(analysisVm, onEdit = { fen -> editorVm.load(fen); go(Routes.EDITOR) })
        }
        composable(Routes.RECOGNITION) {
            RecognitionScreen(recognitionVm, onBack = back, onEdit = { s, side -> editorVm.loadRecognition(s, side); go(Routes.EDITOR) })
        }
        composable(Routes.EDITOR) {
            EditorScreen(
                editorVm, onBack = back,
                onAnalyze = { fen -> if (analysisVm.loadFen(fen)) nav.navigate(Routes.ANALYSIS) { popUpTo(Routes.HOME); launchSingleTop = true } },
                onPlay = { fen -> playVm.prepare(fen, "Partie depuis une position"); go(Routes.PLAY) },
            )
        }
        composable(Routes.TRAINING) {
            TrainingHubScreen(
                onPlay = { playVm.prepare(); go(Routes.PLAY) },
                onPuzzles = { go(Routes.PUZZLES) }, onOpenings = { go(Routes.OPENINGS) }, onEndgames = { go(Routes.ENDGAMES) },
                onReview = { go(Routes.REVIEW) }, onEditor = { editorVm.load(com.chessoverlay.chess.Position.START_FEN); go(Routes.EDITOR) },
                onHistory = { go(Routes.HISTORY) },
            )
        }
        composable(Routes.PLAY) { PlayScreen(playVm, onBack = back, onReview = { g -> reviewVm.load(g); go(Routes.REVIEW) }) }
        composable(Routes.PUZZLES) { PuzzlesScreen(puzzleVm, onBack = back) }
        composable(Routes.OPENINGS) {
            OpeningsScreen(onBack = back,
                onAnalyze = { g -> analysisVm.loadGame(g); go(Routes.ANALYSIS) },
                onPlayFrom = { fen, name -> playVm.prepare(fen, name); go(Routes.PLAY) })
        }
        composable(Routes.ENDGAMES) {
            EndgamesScreen(onBack = back, onPlay = { fen, title, goal -> playVm.prepare(fen, title, goal); go(Routes.PLAY) })
        }
        composable(Routes.REVIEW) { ReviewScreen(reviewVm, onBack = back, onAnalyze = { g, ply -> analysisVm.loadGame(g, ply); go(Routes.ANALYSIS) }) }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenFen = { fen -> if (analysisVm.loadFen(fen)) go(Routes.ANALYSIS) },
                onReview = { g -> reviewVm.load(g); go(Routes.REVIEW) },
                onReplay = { e -> setReplay(e); go(Routes.REPLAY) },
            )
        }
        composable(Routes.REPLAY) {
            replay?.let { ReplayScreen(it, onBack = back, onAnalyze = { g, ply -> analysisVm.loadGame(g, ply); go(Routes.ANALYSIS) }) }
        }
        composable(Routes.SETTINGS) { SettingsScreen(onAbout = { go(Routes.ABOUT) }, onPositions = { go(Routes.HISTORY) }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple(Routes.HOME, "Accueil", Icons.Rounded.Home),
        Triple(Routes.ANALYSIS, "Analyse", Icons.Rounded.Bolt),
        Triple(Routes.TRAINING, "Entraînement", Icons.Rounded.School),
        Triple(Routes.HISTORY, "Historique", Icons.Rounded.History),
        Triple(Routes.SETTINGS, "Réglages", Icons.Rounded.Tune),
    )
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color(0xE6101018))
            .border(1.dp, Palette.GraphiteLine, RoundedCornerShape(24.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { (r, label, icon) -> NavItem(label, icon, r == current, Modifier.weight(1f)) { onSelect(r) } }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Palette.Violet.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) Palette.CyanSoft else Palette.TextMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Palette.TextMuted, maxLines = 1)
    }
}
