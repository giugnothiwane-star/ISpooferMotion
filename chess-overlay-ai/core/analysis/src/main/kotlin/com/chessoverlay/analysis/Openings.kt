package com.chessoverlay.analysis

import com.chessoverlay.chess.Game

/**
 * Ouverture de la base « lichess-org/chess-openings » (domaine public, CC0).
 * [name] est le nom affiché (famille traduite en français quand une traduction existe),
 * [originalName] le nom anglais d'origine.
 */
data class Opening(
    val eco: String,
    val name: String,
    val pgn: String,
    val uciMoves: List<String>,
    val originalName: String = name,
) {
    /** Famille d'ouverture (partie avant les deux-points), ex. « Défense sicilienne ». */
    val family: String get() = name.substringBefore(':').trim()
    val variation: String? get() = name.substringAfter(':', "").trim().ifEmpty { null }
}

class OpeningBook(openings: List<Opening>) {
    val all: List<Opening> = openings
    private val byMoves: Map<String, Opening> = openings.associateBy { it.uciMoves.joinToString(" ") }
    private val prefixes: Set<String> by lazy {
        val s = HashSet<String>()
        for (o in openings) for (n in 1..o.uciMoves.size) s += o.uciMoves.take(n).joinToString(" ")
        s
    }

    /** Ouverture la plus longue dont les coups forment un préfixe de la partie. */
    fun identify(game: Game): Opening? = identifyMoves(game.uciMoves)

    fun identifyMoves(uciMoves: List<String>): Opening? {
        for (n in minOf(uciMoves.size, 40) downTo 1) {
            byMoves[uciMoves.take(n).joinToString(" ")]?.let { return it }
        }
        return null
    }

    /** Vrai si la séquence fait partie (au moins en préfixe) d'une ligne théorique connue. */
    fun isBookPrefix(uciMoves: List<String>): Boolean = uciMoves.joinToString(" ") in prefixes

    fun families(): List<String> = all.groupingBy { it.family }.eachCount().entries
        .sortedByDescending { it.value }.map { it.key }

    fun byFamily(f: String): List<Opening> = all.filter { it.family == f }.sortedBy { it.uciMoves.size }

    fun search(q: String): List<Opening> {
        val n = normalize(q)
        if (n.isBlank()) return emptyList()
        return all.filter { normalize(it.name).contains(n) || normalize(it.originalName).contains(n) || it.eco.equals(q.trim(), true) }
            .sortedBy { it.uciMoves.size }
    }

    /** Continuations théoriques connues après [uciMoves] (coup suivant → ouvertures). */
    fun continuations(uciMoves: List<String>): Map<String, List<Opening>> {
        val prefix = uciMoves
        return all.filter { it.uciMoves.size > prefix.size && it.uciMoves.take(prefix.size) == prefix }
            .groupBy { it.uciMoves[prefix.size] }
    }

    companion object {
        @Volatile private var cached: OpeningBook? = null

        private fun normalize(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")

        /** Charge la base intégrée (ressource Java « openings/openings.tsv »). */
        fun loadDefault(): OpeningBook {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val stream = OpeningBook::class.java.getResourceAsStream("/openings/openings.tsv")
                    ?: error("Base d'ouvertures introuvable")
                val list = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.drop(1).mapNotNull { line ->
                        val c = line.split('\t')
                        if (c.size < 4) null else Opening(c[0], translate(c[1]), c[2], c[3].split(' ').filter { it.isNotEmpty() }, c[1])
                    }.toList()
                }
                return OpeningBook(list).also { cached = it }
            }
        }

        /** Traduit le nom de famille quand une traduction française usuelle existe. */
        fun translate(name: String): String {
            val family = name.substringBefore(':').trim()
            val rest = name.substringAfter(':', "")
            // Les familles avec suffixe (« Accepted », « Declined ») sont traduites en entier.
            val fr = FAMILY_FR[family] ?: return name
            return if (rest.isEmpty()) fr else "$fr:$rest"
        }

        private val FAMILY_FR = mapOf(
            "Sicilian Defense" to "Défense sicilienne",
            "Ruy Lopez" to "Partie espagnole",
            "French Defense" to "Défense française",
            "Queen's Gambit Declined" to "Gambit dame refusé",
            "Queen's Gambit Accepted" to "Gambit dame accepté",
            "Queen's Gambit" to "Gambit dame",
            "Italian Game" to "Partie italienne",
            "English Opening" to "Ouverture anglaise",
            "King's Gambit Accepted" to "Gambit du roi accepté",
            "King's Gambit Declined" to "Gambit du roi refusé",
            "King's Gambit" to "Gambit du roi",
            "King's Indian Defense" to "Défense est-indienne",
            "King's Indian Attack" to "Attaque est-indienne",
            "Caro-Kann Defense" to "Défense Caro-Kann",
            "Nimzo-Indian Defense" to "Défense nimzo-indienne",
            "Queen's Pawn Game" to "Partie du pion dame",
            "King's Pawn Game" to "Partie du pion roi",
            "Semi-Slav Defense" to "Défense semi-slave",
            "Slav Defense" to "Défense slave",
            "Dutch Defense" to "Défense hollandaise",
            "Benoni Defense" to "Défense Benoni",
            "Grünfeld Defense" to "Défense Grünfeld",
            "Neo-Grünfeld Defense" to "Défense néo-Grünfeld",
            "Queen's Indian Defense" to "Défense ouest-indienne",
            "Queen's Indian Defense, with e3" to "Défense ouest-indienne, avec e3",
            "Bogo-Indian Defense" to "Défense bogo-indienne",
            "Old Indian Defense" to "Ancienne défense indienne",
            "Indian Defense" to "Défense indienne",
            "Alekhine Defense" to "Défense Alekhine",
            "Scotch Game" to "Partie écossaise",
            "Petrov's Defense" to "Défense Petrov (russe)",
            "Four Knights Game" to "Partie des quatre cavaliers",
            "Three Knights Opening" to "Partie des trois cavaliers",
            "Scandinavian Defense" to "Défense scandinave",
            "Philidor Defense" to "Défense Philidor",
            "Réti Opening" to "Ouverture Réti",
            "Zukertort Opening" to "Ouverture Zukertort",
            "Nimzowitsch Defense" to "Défense Nimzowitsch",
            "Vienna Game" to "Partie viennoise",
            "Vienna Gambit, with Max Lange Defense" to "Gambit viennois, défense Max Lange",
            "Bishop's Opening" to "Ouverture du fou",
            "Modern Defense" to "Défense moderne",
            "Pirc Defense" to "Défense Pirc",
            "Catalan Opening" to "Ouverture catalane",
            "Bird Opening" to "Ouverture Bird",
            "Tarrasch Defense" to "Défense Tarrasch",
            "Polish Opening" to "Ouverture polonaise",
            "Hungarian Opening" to "Ouverture hongroise",
            "Grob Opening" to "Ouverture Grob",
            "Center Game" to "Partie du centre",
            "Latvian Gambit" to "Gambit letton",
            "Latvian Gambit Accepted" to "Gambit letton accepté",
            "Ponziani Opening" to "Ouverture Ponziani",
            "Trompowsky Attack" to "Attaque Trompowsky",
            "Benko Gambit" to "Gambit Benko",
            "Benko Gambit Accepted" to "Gambit Benko accepté",
            "Englund Gambit" to "Gambit Englund",
            "Blackmar-Diemer Gambit" to "Gambit Blackmar-Diemer",
            "Blackmar-Diemer Gambit Accepted" to "Gambit Blackmar-Diemer accepté",
            "Blackmar-Diemer Gambit Declined" to "Gambit Blackmar-Diemer refusé",
            "London System" to "Système de Londres",
            "Colle System" to "Système Colle",
            "Owen Defense" to "Défense Owen",
            "Rat Defense" to "Défense du rat",
            "Elephant Gambit" to "Gambit de l'éléphant",
            "Danish Gambit" to "Gambit danois",
            "Danish Gambit Accepted" to "Gambit danois accepté",
            "Evans Gambit" to "Gambit Evans",
            "Petrov Defense" to "Défense Petrov (russe)",
            "Robatsch (Modern) Defense" to "Défense Robatsch (moderne)",
            "Richter-Veresov Attack" to "Attaque Richter-Veresov",
            "Nimzo-Larsen Attack" to "Attaque Nimzo-Larsen",
            "Van Geet Opening" to "Ouverture Van Geet",
            "Pterodactyl Defense" to "Défense ptérodactyle",
            "Kádas Opening" to "Ouverture Kádas",
            "Amar Opening" to "Ouverture Amar",
            "Saragossa Opening" to "Ouverture Saragosse",
            "Mieses Opening" to "Ouverture Mieses",
            "Ware Opening" to "Ouverture Ware",
            "Clemenz Opening" to "Ouverture Clemenz",
            "Anderssen Opening" to "Ouverture Anderssen",
            "Durkin Opening" to "Ouverture Durkin",
            "Barnes Opening" to "Ouverture Barnes",
            "Sodium Attack" to "Attaque Sodium",
            "Torre Attack" to "Attaque Torre",
            "Budapest Defense" to "Gambit de Budapest",
            "Czech Defense" to "Défense tchèque",
            "Hippopotamus Defense" to "Défense hippopotame",
            "Lion Defense" to "Défense du lion",
            "Portuguese Opening" to "Ouverture portugaise",
            "Lemming Defense" to "Défense Lemming",
        )
    }
}
