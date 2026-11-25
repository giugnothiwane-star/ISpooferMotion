package com.chessoverlay.app.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chessoverlay.app.container
import com.chessoverlay.app.overlay.OverlayService
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.training.HubCard
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onAnalyze: () -> Unit, onRecognize: () -> Unit, onTraining: () -> Unit, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.container
    val settings = appSettings()
    val scope = rememberCoroutineScope()
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val overlayRunning by OverlayService.running.collectAsStateWithLifecycle()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Revérifie l'autorisation au retour des réglages Android.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) canOverlay = Settings.canDrawOverlays(ctx) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("CHESS OVERLAY AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("ULTIMATE · Stockfish 17.1 sur votre téléphone", style = MaterialTheme.typography.labelMedium, color = Palette.VioletSoft)
            }
        }
        Spacer(Modifier.height(20.dp))
        GlassCard(Modifier.fillMaxWidth(), glow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = Palette.Cyan)
                Spacer(Modifier.width(10.dp))
                Text("Moteur local", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(if (app.engines.isAvailable) "Prêt" else "Introuvable", if (app.engines.isAvailable) Palette.Success else Palette.Danger)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Stockfish fonctionne entièrement hors ligne : aucune clé d'API, aucun serveur, aucun abonnement. " +
                    "${settings.effectiveThreads} cœurs · ${settings.hashMb} Mo · profondeur ${settings.effectiveDepth}.",
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            GlowButton("Analyser une position", onAnalyze, icon = Icons.Rounded.Bolt, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Layers, null, tint = Palette.VioletSoft)
                Spacer(Modifier.width(10.dp))
                Text("Menu flottant", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(if (overlayRunning) "Actif" else if (canOverlay) "Autorisé" else "Autorisation requise", if (overlayRunning) Palette.Success else if (canOverlay) Palette.Cyan else Palette.Warning)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Une bulle déplaçable reste au-dessus de vos applications : capturez l'écran (avec votre accord Android), " +
                    "vérifiez la position reconnue puis obtenez l'analyse sans quitter votre application.",
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            when {
                !canOverlay -> GlowButton("Autoriser l'affichage par-dessus", {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }, icon = Icons.Rounded.Layers, modifier = Modifier.fillMaxWidth())
                overlayRunning -> GlowButton("Fermer le menu flottant", { OverlayService.stop(ctx) }, icon = Icons.Rounded.LayersClear, secondary = true, modifier = Modifier.fillMaxWidth())
                else -> GlowButton("Lancer le menu flottant", {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    OverlayService.start(ctx)
                }, icon = Icons.Rounded.Layers, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                GlassCard(Modifier.fillMaxWidth(), onClick = onRecognize) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, tint = Palette.Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Reconnaître", style = MaterialTheme.typography.titleSmall)
                    Text("depuis une capture", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
            Box(Modifier.weight(1f)) {
                GlassCard(Modifier.fillMaxWidth(), onClick = onImport) {
                    Icon(Icons.Rounded.UploadFile, null, tint = Palette.Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Importer", style = MaterialTheme.typography.titleSmall)
                    Text("FEN ou PGN", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HubCard(Icons.Rounded.School, "Entraînement", "Parties, exercices, ouvertures, finales, revue", onTraining)
        Spacer(Modifier.height(28.dp))
    }

    if (!settings.fairPlayAccepted) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Palette.GraphiteHigh,
            title = { Text("Jeu loyal") },
            text = {
                Text(
                    "Chess Overlay AI est un outil d'analyse et d'entraînement. Utiliser l'aide d'un moteur pendant une partie " +
                        "en ligne ou en tournoi contre un adversaire humain est interdit par les règles de toutes les plateformes " +
                        "et des fédérations. L'application ne contourne aucune protection : utilisez-la pour étudier, analyser " +
                        "vos parties terminées, jouer contre l'ordinateur ou là où l'aide d'un moteur est explicitement autorisée.",
                    color = Palette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { app.settingsRepository.update { it.copy(fairPlayAccepted = true) } } }) {
                    Text("J'ai compris", color = Palette.CyanSoft)
                }
            },
        )
    }
}
