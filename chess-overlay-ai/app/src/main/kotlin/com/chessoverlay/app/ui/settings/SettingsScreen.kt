package com.chessoverlay.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chessoverlay.app.container
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.app.ui.common.appSettings
import com.chessoverlay.app.ui.common.pieces
import com.chessoverlay.app.ui.common.theme
import com.chessoverlay.app.ui.training.sliderColors
import com.chessoverlay.chess.Position
import com.chessoverlay.data.AppSettings
import com.chessoverlay.ui.board.BoardTheme
import com.chessoverlay.ui.board.ChessBoard
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.InfoRow
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.components.SettingSwitch
import com.chessoverlay.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onAbout: () -> Unit, onPositions: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.container
    val s = appSettings()
    val scope = rememberCoroutineScope()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { app.settingsRepository.update(f) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Paramètres", "Sauvegardés localement sur l'appareil")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Moteur Stockfish")
                SliderRow("Profondeur d'analyse", "${s.depth} demi-coups", s.depth.toFloat(), 8f..34f, 25) { v -> update { it.copy(depth = v.toInt()) } }
                SliderRow(
                    "Temps de réflexion maximal",
                    if (s.moveTimeMs == 0) "illimité (profondeur seule)" else String.format(java.util.Locale.FRANCE, "%.1f s", s.moveTimeMs / 1000.0),
                    s.moveTimeMs.toFloat(), 0f..20000f, 39,
                ) { v -> update { it.copy(moveTimeMs = (v / 500).toInt() * 500) } }
                SliderRow("Nombre de variantes (MultiPV)", "${s.multiPv}", s.multiPv.toFloat(), 1f..5f, 3) { v -> update { it.copy(multiPv = v.toInt()) } }
                SliderRow("Cœurs du processeur utilisés", "${s.threads} / ${app.engines.cpuCores}", s.threads.toFloat(), 1f..8f, 6) { v -> update { it.copy(threads = v.toInt()) } }
                SliderRow("Mémoire de hachage", "${s.hashMb} Mo", s.hashMb.toFloat(), 16f..512f, 30) { v -> update { it.copy(hashMb = ((v / 16).toInt() * 16).coerceAtLeast(16)) } }
                SettingSwitch("Économie de batterie", "Limite à 2 cœurs et à la profondeur 16", s.powerSaving, { v -> update { it.copy(powerSaving = v) } })
                SettingSwitch("Analyse automatique", "Lance Stockfish dès que la position change", s.autoAnalyze, { v -> update { it.copy(autoAnalyze = v) } })
                Text(
                    "Conseil S23 Ultra : 4 cœurs offrent un bon équilibre entre vitesse et chauffe ; 6 à 8 cœurs pour les analyses longues sur secteur.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Échiquier")
                Text("Thème", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoardTheme.entries.forEach { t ->
                        Choice(t.label, t.id == s.boardTheme, { update { it.copy(boardTheme = t.id) } }) {
                            ChessBoard(Position.initial(), Modifier.size(84.dp), theme = t, pieceSet = s.pieces, showCoordinates = false, animate = false)
                        }
                    }
                }
                Text("Style des pièces", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PieceSet.entries.forEach { p ->
                        Choice(p.label, p.id == s.pieceStyle, { update { it.copy(pieceStyle = p.id) } }) {
                            ChessBoard(Position.fromFen("8/8/8/2qkrn2/2QKBN2/8/8/8 w - - 0 1"), Modifier.size(84.dp), theme = s.theme, pieceSet = p, showCoordinates = false, animate = false)
                        }
                    }
                }
                SettingSwitch("Coordonnées", "Lettres et chiffres sur les bords", s.showCoordinates, { v -> update { it.copy(showCoordinates = v) } })
                SettingSwitch("Flèches de recommandation", "Meilleur coup et variantes sur l'échiquier", s.showArrows, { v -> update { it.copy(showArrows = v) } })
                SettingSwitch("Animations", "Déplacements animés des pièces", s.animations, { v -> update { it.copy(animations = v) } })
                SettingSwitch("Notation française", "R, D, T, F, C au lieu de K, Q, R, B, N", s.frenchNotation, { v -> update { it.copy(frenchNotation = v) } })
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Menu flottant")
                SliderRow("Transparence", "${(s.overlayAlpha * 100).toInt()} % d'opacité", s.overlayAlpha, 0.3f..1f, 13) { v -> update { it.copy(overlayAlpha = v) } }
                SliderRow("Taille", "${(s.overlayScale * 100).toInt()} %", s.overlayScale, 0.7f..1.4f, 13) { v -> update { it.copy(overlayScale = v) } }
                GlowButton("Autorisation de superposition", {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, icon = Icons.Rounded.Layers, secondary = true, modifier = Modifier.fillMaxWidth())
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Sons, historique et données")
                SettingSwitch("Sons", "Déplacements, captures, échecs", s.sounds, { v -> update { it.copy(sounds = v) } })
                SettingSwitch("Retour haptique", "Vibrations légères", s.haptics, { v -> update { it.copy(haptics = v) } })
                SettingSwitch("Historique des analyses", "Conserver les 300 dernières analyses", s.keepHistory, { v -> update { it.copy(keepHistory = v) } })
                GlowButton("Positions enregistrées", onPositions, secondary = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GlowButton("Réinitialiser la reconnaissance d'image", { scope.launch { app.recognition.resetTemplates() } }, secondary = true, modifier = Modifier.fillMaxWidth())
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Langue")
                InfoRow("Langue de l'interface", "Français")
                Text("Cette version est entièrement rédigée en français ; aucune autre langue n'est encore disponible.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            GlowButton("À propos, licences et sources", onAbout, icon = Icons.Rounded.Info, secondary = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = Palette.CyanSoft)
        }
        Slider(current, onChange, valueRange = range, steps = steps, colors = sliderColors())
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Violet.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) Palette.Violet else Palette.GraphiteLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Box { preview() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp), maxLines = 2)
    }
}
