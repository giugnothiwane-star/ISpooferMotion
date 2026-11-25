package com.chessoverlay.analysis

/**
 * Position de finale à jouer contre Stockfish. Le joueur a le trait dans [fen].
 * [goal] vaut « Gagner » ou « Annuler ». Les objectifs ont été vérifiés avec Stockfish 17.1
 * (évaluation gagnante ou nulle à profondeur 24) ; le test EndgameValidationTest le revérifie.
 */
data class Endgame(
    val id: String,
    val title: String,
    val fen: String,
    val goal: String,
    val description: String,
)

object EndgameSet {
    const val WIN = "Gagner"
    const val DRAW = "Annuler"

    val endgames: List<Endgame> = listOf(
        Endgame("kqk", "Roi et Dame contre Roi", "8/8/8/4k3/8/8/8/3QK3 w - - 0 1", WIN,
            "Repoussez le roi adverse vers un bord avec la dame, rapprochez votre roi, puis matez. Attention au pat !"),
        Endgame("krk", "Roi et Tour contre Roi", "8/8/8/4k3/8/8/8/R3K3 w - - 0 1", WIN,
            "Réduisez la « boîte » du roi adverse avec la tour et utilisez l'opposition des rois pour le mater sur un bord."),
        Endgame("krrk", "Deux tours : le mat de l'escalier", "8/8/8/4k3/8/8/8/R3K2R w - - 0 1", WIN,
            "Les deux tours avancent à tour de rôle, rangée après rangée, sans l'aide du roi."),
        Endgame("kbbk", "Les deux fous", "8/8/8/4k3/8/8/8/2B1KB2 w - - 0 1", WIN,
            "Les fous côte à côte forment une barrière ; poussez le roi dans un coin avec l'aide de votre roi."),
        Endgame("kbnk", "Fou et Cavalier", "8/8/8/4k3/8/8/8/1N2KB2 w - - 0 1", WIN,
            "La finale élémentaire la plus difficile : le mat n'est possible que dans un coin de la couleur du fou (ici h1 ou a8)."),
        Endgame("kpk-gain", "Roi et Pion : le temps de réserve", "8/8/4k3/8/4K3/8/4P3/8 w - - 0 1", WIN,
            "Votre roi est devant son pion : utilisez le coup de pion de réserve pour gagner l'opposition."),
        Endgame("kpk-nulle", "Roi et Pion : défendre", "4k3/8/8/4P3/4K3/8/8/8 b - - 0 1", DRAW,
            "Restez devant le pion et reprenez l'opposition au bon moment pour obtenir la nulle."),
        Endgame("carre", "La règle du carré", "8/8/8/8/8/k7/6P1/7K w - - 0 1", WIN,
            "Le roi noir est hors du carré du pion : poussez-le jusqu'à la promotion."),
        Endgame("lucena", "Position de Lucena", "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1", WIN,
            "Coupez le roi adverse, sortez votre roi de devant le pion, puis « construisez le pont » avec la tour."),
        Endgame("philidor", "Position de Philidor", "3k4/8/r7/3PK3/8/8/8/7R b - - 0 1", DRAW,
            "Gardez la tour sur la 6e rangée tant que le pion n'y est pas, puis donnez des échecs par derrière."),
        Endgame("dame-pion", "Dame contre pion en 7e", "8/8/8/8/8/5K2/1pk5/4Q3 w - - 0 1", WIN,
            "Forcez le roi noir à bloquer son pion par des échecs, puis rapprochez votre roi."),
    )
}
