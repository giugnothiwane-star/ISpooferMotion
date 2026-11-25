package com.chessoverlay.analysis

import com.chessoverlay.chess.Move
import com.chessoverlay.chess.Position

/**
 * Exercice tactique. [solution] contient, en UCI, les coups du joueur et les réponses adverses en alternance
 * (le premier coup est celui du joueur, qui a le trait dans [fen]).
 */
data class Puzzle(
    val id: String,
    val title: String,
    val theme: String,
    /** 1 = facile, 2 = moyen, 3 = difficile. */
    val difficulty: Int,
    val fen: String,
    val solution: List<String>,
    val hint: String,
) {
    /** Nombre de coups que le joueur doit trouver. */
    val playerMoves: Int get() = (solution.size + 1) / 2
}

/**
 * Exercices intégrés. Chaque solution a été calculée et vérifiée avec Stockfish 17.1 : chaque coup du joueur
 * est le meilleur coup du moteur avec une marge nette sur la deuxième possibilité (ou un mat forcé), et
 * chaque réponse adverse est la meilleure défense du moteur. Le test PuzzleValidationTest le revérifie.
 */
object PuzzleSet {
    val puzzles: List<Puzzle> = listOf(
        Puzzle("couloir-1", "Mat du couloir", "Mat du couloir", 1, "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", listOf("d1d8"), "Le roi noir est enfermé derrière ses propres pions."),
        Puzzle("couloir-2", "La dernière rangée", "Mat du couloir", 1, "1r4k1/5ppp/8/8/8/8/1Q3PPP/6K1 w - - 0 1", listOf("b2b8"), "Une pièce noire est seule à garder la 8e rangée."),
        Puzzle("couloir-3", "Éliminer le défenseur", "Mat du couloir", 1, "3r2k1/5ppp/8/8/8/8/1Q3PPP/3R2K1 w - - 0 1", listOf("d1d8"), "Supprimez la seule pièce qui protège la dernière rangée."),
        Puzzle("berger", "Le coup du berger", "Mat rapide", 1, "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", listOf("h5f7"), "Le pion f7 n'est défendu que par le roi."),
        Puzzle("sot", "Le mat du sot", "Mat rapide", 1, "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2", listOf("d8h4"), "La diagonale e1-h4 est grande ouverte."),
        Puzzle("etouffe-1", "Mat étouffé", "Mat étouffé", 1, "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1", listOf("g5f7"), "Le roi noir n'a aucune case libre : un cavalier suffit."),
        Puzzle("anastasie", "Mat d'Anastasie", "Mat d'Anastasie", 2, "5r2/4Nppk/8/8/8/3R4/8/6K1 w - - 0 1", listOf("d3h3"), "Le cavalier contrôle g8 et g6 ; il reste la colonne h."),
        Puzzle("arabe", "Mat arabe", "Mat arabe", 1, "7k/R7/5N2/8/8/8/8/6K1 w - - 0 1", listOf("a7h7"), "La tour, protégée par le cavalier, peut approcher le roi."),
        Puzzle("epaulettes", "Mat des épaulettes", "Mat des épaulettes", 1, "3rkr2/8/8/8/8/8/Q7/6K1 w - - 0 1", listOf("a2e6"), "Les deux tours noires bloquent leur propre roi."),
        Puzzle("legal", "Mat de Légal", "Mat de Légal", 2, "rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P3/2N5/PPPP1PPP/R1BbK2R w KQkq - 0 6", listOf("c4f7", "e8e7", "c3d5"), "La dame est perdue, mais le roi noir l'est aussi."),
        Puzzle("opera", "La partie de l'Opéra (Morphy, 1858)", "Sacrifice de dame", 3, "4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4 w k - 1 16", listOf("b3b8", "d7b8", "d1d8"), "Un sacrifice de dame ouvre la colonne d."),
        Puzzle("reti", "Réti – Tartakower (Vienne, 1910)", "Sacrifice de dame", 3, "rnb1kb1r/pp3ppp/2p5/4q3/4n3/3Q4/PPPB1PPP/2KR1BNR w kq - 0 9", listOf("d3d8", "e8d8", "d2g5", "d8c7", "g5d8"), "Le roi noir est encore au centre sur une colonne ouverte."),
        Puzzle("fourchette-roi", "Fourchette royale", "Fourchette", 1, "q3k3/pp4pp/8/1N6/8/8/PP4PP/4K3 w - - 0 1", listOf("b5c7", "e8d7", "c7a8"), "Un cavalier peut attaquer le roi et la dame en même temps."),
        Puzzle("fourchette-noire", "Fourchette du cavalier noir", "Fourchette", 1, "4k3/pp4pp/8/8/1n6/8/PP4PP/Q3K3 b - - 0 1", listOf("b4c2", "e1d2", "c2a1"), "Visez le roi et la dame blanche d'un seul saut."),
        Puzzle("enfilade-1", "Enfilade sur la 6e rangée", "Enfilade", 1, "8/8/2k4q/8/8/8/8/R3K3 w - - 0 1", listOf("a1a6", "c6d5", "a6h6"), "Faites un échec : la dame est derrière le roi."),
        Puzzle("enfilade-2", "Enfilade du fou", "Enfilade", 2, "q7/8/2k5/8/8/8/5PPP/1B4K1 w - - 0 1", listOf("b1e4", "c6c5", "e4a8"), "Le fou peut attaquer le roi et ce qui se trouve derrière."),
        Puzzle("decouverte-1", "Échec double", "Attaque découverte", 2, "2q1k3/8/8/8/4N3/8/8/4R1K1 w - - 0 1", listOf("e4d6", "e8d7", "d6c8"), "Le cavalier masque la tour : en bougeant, il peut aussi attaquer."),
        Puzzle("sous-promotion", "Sous-promotion", "Promotion", 2, "8/2q1P1k1/6p1/8/8/8/5PPP/6K1 w - - 0 1", listOf("e7e8n", "g7f7", "e8c7"), "La dame n'est pas toujours la meilleure promotion."),
        Puzzle("coup-grec", "Le sacrifice grec", "Sacrifice", 3, "rnbq1rk1/pppn1ppp/4p3/3pP3/1b1P4/2NB1N2/PPP2PPP/R1BQK2R w KQ - 5 7", listOf("d3h7"), "Le pion h7 n'est défendu que par le roi."),
        Puzzle("dame-piegee", "Dame en prise", "Vigilance", 1, "r3k2r/ppp2ppp/2n5/3q4/8/2N5/PPP2PPP/R3KB1R w KQkq - 0 1", listOf("c3d5"), "Vérifiez toujours quelles pièces adverses sont attaquées."),
        Puzzle("couloir-noir", "Mat du couloir (Noirs)", "Mat du couloir", 1, "3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1", listOf("d8d1"), "Le roi blanc n'a pas de case de fuite."),
        Puzzle("dame-roi", "Mat avec la dame", "Mat", 1, "k7/8/1K6/8/8/8/7Q/8 w - - 0 1", listOf("h2h8"), "Le roi blanc soutient la dame."),
        Puzzle("boden", "Mat de Boden", "Mat de Boden", 2, "2kr4/p2n4/2p5/8/5B2/8/4B3/6K1 w - - 0 1", listOf("e2a6"), "Deux fous sur des diagonales croisées."),
        Puzzle("baiser", "Le baiser de la mort", "Mat", 1, "6k1/5p1p/6p1/8/3Q4/8/1B3PPP/6K1 w - - 0 1", listOf("d4g7"), "La dame, soutenue par le fou, se colle au roi."),
        Puzzle("couloir-sacrifice", "Le défenseur isolé", "Mat du couloir", 1, "3r2k1/5ppp/8/8/7Q/8/5PPP/4R1K1 w - - 0 1", listOf("h4d8"), "La tour noire garde la 8e rangée, mais qui la protège ?"),
    )

    /** Vrai si [move] est le bon coup à l'étape [step] (un autre mat est accepté au dernier coup). */
    fun isCorrectMove(puzzle: Puzzle, step: Int, pos: Position, move: Move): Boolean {
        val expected = puzzle.solution.getOrNull(step) ?: return false
        val m = pos.normalize(move) ?: return false
        if (m.uci == expected) return true
        val last = step >= puzzle.solution.size - 1
        return last && pos.play(m).isCheckmate
    }

    fun byTheme(): Map<String, List<Puzzle>> = puzzles.groupBy { it.theme }
}
