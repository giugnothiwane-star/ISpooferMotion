package com.chessoverlay.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chessoverlay.app.BuildConfig
import com.chessoverlay.app.container
import com.chessoverlay.app.ui.common.ScreenHeader
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.InfoRow
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.container
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("À propos", "Version ${BuildConfig.VERSION_NAME}", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth(), glow = true) {
                SectionTitle("Chess Overlay AI Ultimate")
                Text(
                    "Assistant d'analyse d'échecs fonctionnant hors ligne. Ce programme est un logiciel libre distribué sous " +
                        "licence GNU GPL version 3 : vous pouvez le redistribuer et le modifier selon ses termes. " +
                        "Il est fourni SANS AUCUNE GARANTIE.",
                    color = Palette.TextSecondary,
                )
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Moteur Stockfish")
                InfoRow("Version", "Stockfish 17.1 (sf_17.1)")
                InfoRow("Licence", "GNU GPL v3")
                InfoRow("Compilation", "ARMv8.2 + dotprod, NDK Android")
                InfoRow("Réseaux NNUE", "nn-1c0000000000 · nn-37f18f62d772")
                InfoRow("Binaire", if (app.engines.isAvailable) "présent" else "absent")
                Text(
                    "Stockfish est développé par les auteurs de Stockfish (voir le fichier AUTHORS du projet). Le code source " +
                        "exact utilisé est celui de l'étiquette sf_17.1 ; le script de compilation est fourni avec le code de l'application.",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                GlowButton("Code source de Stockfish", { open("https://github.com/official-stockfish/Stockfish/tree/sf_17.1") }, secondary = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                GlowButton("Texte de la licence GPL v3", { open("https://www.gnu.org/licenses/gpl-3.0.html") }, secondary = true, modifier = Modifier.fillMaxWidth())
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Ressources graphiques et données")
                PieceSet.entries.forEach { InfoRow("Pièces ${it.label}", it.credit) }
                InfoRow("Ouvertures", "lichess-org/chess-openings — domaine public (CC0)")
                InfoRow("Sons", "Synthétisés pour l'application")
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("Jeu loyal")
                Text(
                    "L'utilisation d'un moteur pendant une partie classée contre un humain est interdite par les plateformes " +
                        "d'échecs et les fédérations. L'application n'intègre aucun mécanisme de contournement des protections " +
                        "anti-triche et le déplacement automatique des pièces n'existe que dans les parties locales.",
                    color = Palette.TextSecondary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
