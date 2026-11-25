package com.chessoverlay.app.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessoverlay.analysis.AnalyzedLine
import com.chessoverlay.analysis.CaptureInfo
import com.chessoverlay.analysis.CaptureVerdict
import com.chessoverlay.analysis.MoveDescription
import com.chessoverlay.analysis.PositionAnalysis
import com.chessoverlay.analysis.TacticalReport
import com.chessoverlay.chess.Sq
import com.chessoverlay.engine.EngineScore
import com.chessoverlay.ui.components.GlassCard
import com.chessoverlay.ui.components.GlowButton
import com.chessoverlay.ui.components.InfoRow
import com.chessoverlay.ui.components.Pill
import com.chessoverlay.ui.components.SectionTitle
import com.chessoverlay.ui.theme.Palette

fun scoreColor(score: EngineScore?): Color = when {
    score == null -> Palette.TextMuted
    score.mate != null -> if (score.mate!! > 0) Palette.Success else Palette.Danger
    (score.centipawns ?: 0) > 80 -> Palette.Success
    (score.centipawns ?: 0) < -80 -> Palette.Danger
    else -> Palette.Cyan
}

fun verdictColor(v: CaptureVerdict): Color = when (v) {
    CaptureVerdict.FAVORABLE -> Palette.Success
    CaptureVerdict.EQUAL -> Palette.Cyan
    CaptureVerdict.UNFAVORABLE -> Palette.Danger
    CaptureVerdict.UNKNOWN -> Palette.TextMuted
}

/** Panneau « ANALYSE DU MEILLEUR COUP » (sections A à F). */
@Composable
fun BestMovePanel(analysis: PositionAnalysis?, analyzing: Boolean, captures: List<CaptureInfo>, capturesLoading: Boolean, french: Boolean) {
    if (analysis?.gameOverReason != null) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Partie terminée", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(analysis.gameOverReason!!, color = Palette.TextSecondary)
        }
        return
    }
    val best = analysis?.best
    if (best == null) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (analyzing) CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(if (analyzing) "Stockfish calcule le meilleur coup…" else "Analyse en pause. Appuyez sur Lecture pour lancer Stockfish.", color = Palette.TextSecondary)
            }
        }
        return
    }
    val d = best.first
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.fillMaxWidth(), glow = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.VioletSoft)
                Spacer(Modifier.width(8.dp))
                Text("MEILLEUR COUP", style = MaterialTheme.typography.labelLarge, color = Palette.VioletSoft, modifier = Modifier.weight(1f))
                Pill(best.scoreWhite.format(), scoreColor(best.score), filled = false)
            }
            Spacer(Modifier.height(10.dp))
            Text(d.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(d.summaryFr(), color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Profondeur ${best.depth}" + if (!analysis.finished) " · calcul en cours…" else " · calcul terminé",
                style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted,
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("A · Pièce à déplacer")
            InfoRow("Pièce", d.piece.frenchName, emphasize = true)
            InfoRow("Couleur", d.piece.color.frenchSide.removeSuffix("s"))
            InfoRow("Position actuelle", Sq.upperName(d.from), valueColor = Palette.VioletSoft, emphasize = true)
            InfoRow("Destination", Sq.upperName(d.to), valueColor = Palette.Cyan, emphasize = true)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("B · Informations du déplacement")
            InfoRow("Case de départ", Sq.upperName(d.from))
            InfoRow("Case d'arrivée", Sq.upperName(d.to))
            InfoRow("Notation algébrique", if (french) "${d.sanFrench}  (intern. ${d.san})" else "${d.san}  (fr. ${d.sanFrench})")
            InfoRow("Notation UCI", d.uci)
            InfoRow("Type de déplacement", d.kind.frenchLabel)
            InfoRow("Capture", if (d.captured != null) "Oui" else "Non", valueColor = if (d.captured != null) Palette.Warning else Palette.TextPrimary)
            if (d.givesMate) InfoRow("Effet", "Échec et mat", valueColor = Palette.Success, emphasize = true)
            else if (d.givesCheck) InfoRow("Effet", "Échec au roi", valueColor = Palette.Warning)
            d.promotion?.let { InfoRow("Promotion", it.frenchName) }
        }

        if (d.captured != null) {
            val info = captures.firstOrNull { it.description.uci == d.uci }
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle("C · Informations sur la capture")
                InfoRow("Pièce adverse", d.captured!!.frenchName, emphasize = true)
                InfoRow("Case de la pièce", Sq.upperName(d.capturedSquare))
                InfoRow("Pièce qui capture", d.piece.frenchName)
                InfoRow("Case de destination", Sq.upperName(d.to))
                when {
                    info != null -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(info.verdict.frenchLabel, verdictColor(info.verdict))
                        Spacer(Modifier.height(6.dp))
                        Text(info.explanation, color = Palette.TextSecondary)
                        info.consequences.forEach { Bullet(it) }
                    }
                    capturesLoading -> Text("Stockfish évalue les conséquences de la capture…", color = Palette.TextMuted)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("D · Explication")
            if (best.explanation.isEmpty()) Text("Coup recommandé par Stockfish d'après son calcul (évaluation ${best.scoreWhite.format()}).", color = Palette.TextSecondary)
            best.explanation.forEach { Bullet(it) }
            if (best.motifs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                best.motifs.forEach { m -> Bullet("${m.title} : ${m.description}", Palette.CyanSoft) }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("E · Réponse adverse")
            val r = analysis.opponentBestReply
            if (r == null) Text("Aucune réponse calculée (fin de variante ou calcul en cours).", color = Palette.TextMuted)
            else {
                Text(r.headline, style = MaterialTheme.typography.titleMedium)
                Text(r.summaryFr(), color = Palette.TextSecondary)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionTitle("F · Suite de la variante")
            if (analysis.followUp.isEmpty()) Text("La variante calculée ne contient pas d'autres coups.", color = Palette.TextMuted)
            analysis.followUp.forEachIndexed { i, m -> MoveStep(i + 3, m) }
            Spacer(Modifier.height(8.dp))
            Text(best.sanLine, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
}

@Composable
fun MoveStep(index: Int, m: MoveDescription) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$index.", color = Palette.TextMuted, modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f)) {
            Text(m.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (m.captured != null || m.givesCheck) {
                Text(
                    listOfNotNull(m.captured?.let { "capture ${it.frenchName} en ${Sq.upperName(m.capturedSquare)}" }, if (m.givesMate) "mat" else if (m.givesCheck) "échec" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Warning,
                )
            }
        }
        Text(m.sanFrench, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Bullet(text: String, color: Color = Palette.TextSecondary) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", color = Palette.Violet, modifier = Modifier.width(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Les meilleures variantes (MultiPV) avec sélection pour visualisation. */
@Composable
fun LinesPanel(analysis: PositionAnalysis?, preview: LinePreview?, onPreview: (LinePreview?) -> Unit, onPlay: (Int) -> Unit) {
    val lines = analysis?.lines.orEmpty()
    if (lines.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) { Text("Les variantes apparaîtront dès les premiers résultats de Stockfish.", color = Palette.TextMuted) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lines.forEachIndexed { i, line -> LineCard(i, line, preview?.lineIndex == i, onPreview, onPlay) }
    }
}

@Composable
private fun LineCard(i: Int, line: AnalyzedLine, selected: Boolean, onPreview: (LinePreview?) -> Unit, onPlay: (Int) -> Unit) {
    val accent = when (i) { 0 -> Palette.Violet; 1 -> Palette.Cyan; else -> Palette.TextSecondary }
    GlassCard(Modifier.fillMaxWidth(), glow = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("#${i + 1}", accent, filled = true)
            Spacer(Modifier.width(10.dp))
            Text(line.first.headline, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill(line.scoreWhite.format(), scoreColor(line.score))
        }
        Spacer(Modifier.height(8.dp))
        InfoRow("Pièce à jouer", line.first.piece.frenchName)
        InfoRow("Départ → arrivée", "${Sq.upperName(line.first.from)} → ${Sq.upperName(line.first.to)}")
        InfoRow("Capture", line.first.captured?.let { "${it.frenchName} en ${Sq.upperName(line.first.capturedSquare)}" } ?: "Non")
        InfoRow("Profondeur", line.depth.toString())
        Spacer(Modifier.height(6.dp))
        Text("Variante complète", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Text(line.sanLine, style = MaterialTheme.typography.bodyMedium)
        if (line.explanation.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Explication tactique", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
            line.explanation.forEach { Bullet(it) }
        }
        line.motifs.forEach { Bullet("${it.title} : ${it.description}", Palette.CyanSoft) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(
                if (selected) "Masquer" else "Visualiser", { onPreview(if (selected) null else LinePreview(i, 0)) },
                icon = Icons.Rounded.Visibility, secondary = true, modifier = Modifier.weight(1f),
            )
            GlowButton("Jouer", { onPlay(i) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f))
        }
    }
}

/** Analyse des captures : chaque prise possible, jugée par Stockfish (et non considérée bonne par défaut). */
@Composable
fun CapturesPanel(captures: List<CaptureInfo>, loading: Boolean, staticReport: TacticalReport?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Stockfish évalue chaque capture…", color = Palette.TextSecondary)
            }
        }
        val list = captures.ifEmpty { if (!loading) staticReport?.captures.orEmpty() else emptyList() }
        if (list.isEmpty() && !loading) {
            GlassCard(Modifier.fillMaxWidth()) { Text("Aucune capture n'est possible dans cette position.", color = Palette.TextMuted) }
        }
        list.forEach { c ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowForward, null, tint = verdictColor(c.verdict))
                    Spacer(Modifier.width(8.dp))
                    Text(c.description.headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Pill(c.verdict.frenchLabel, verdictColor(c.verdict))
                }
                Spacer(Modifier.height(6.dp))
                InfoRow("Pièce capturée", "${c.description.captured?.frenchName ?: "?"} en ${Sq.upperName(c.description.capturedSquare)}")
                InfoRow("Échange (SEE)", (if (c.see > 0) "+" else "") + c.see + " cp")
                c.engineScore?.let { InfoRow("Évaluation Stockfish", it.format(), valueColor = scoreColor(it)) }
                c.deltaVsBest?.let { InfoRow("Écart avec le meilleur coup", "${-it} cp") }
                Text(c.explanation, color = Palette.TextSecondary)
                c.consequences.forEach { Bullet(it) }
            }
        }
    }
}

/** Motifs tactiques de la position : pièces en prise, menacées, fourchettes, clouages, enfilades… */
@Composable
fun TacticsPanel(report: TacticalReport?) {
    val motifs = report?.motifs.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (motifs.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) { Text("Aucun motif tactique immédiat détecté dans cette position.", color = Palette.TextMuted) }
        }
        motifs.groupBy { it.type }.forEach { (type, list) ->
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(type.frenchLabel)
                list.forEach { m ->
                    Text(m.title, style = MaterialTheme.typography.titleSmall)
                    Text(m.description, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}
