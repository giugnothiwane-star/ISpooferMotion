package com.chessoverlay.chess

/** Lecture et écriture du format PGN (ligne principale ; les variantes et commentaires sont ignorés). */
object Pgn {

    /** Lit la première partie d'un texte PGN. */
    fun parse(text: String): Game = parseAll(text).firstOrNull()
        ?: throw ChessFormatException("Aucune partie trouvée dans le PGN.")

    /** Lit toutes les parties d'un fichier PGN. */
    fun parseAll(text: String): List<Game> {
        val games = mutableListOf<Game>()
        val chunks = splitGames(text)
        for (chunk in chunks) games += parseSingle(chunk)
        return games
    }

    private fun splitGames(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inMoves = false
        for (line in text.replace("\r", "").lines()) {
            val t = line.trim()
            if (t.startsWith("[") && inMoves && current.isNotBlank()) {
                out += current.toString(); current.clear(); inMoves = false
            }
            if (t.isNotEmpty() && !t.startsWith("[") && !t.startsWith("%")) inMoves = true
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) out += current.toString()
        return out
    }

    private val tagRegex = Regex("""^\s*\[(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]\s*$""")
    private val resultTokens = setOf("1-0", "0-1", "1/2-1/2", "½-½", "*")

    private fun parseSingle(chunk: String): Game {
        val tags = linkedMapOf<String, String>()
        val moveText = StringBuilder()
        for (line in chunk.lines()) {
            val m = tagRegex.matchEntire(line)
            if (m != null) tags[m.groupValues[1]] = m.groupValues[2].replace("\\\"", "\"")
            else if (!line.trimStart().startsWith("%")) moveText.append(line).append('\n')
        }
        val start = tags["FEN"]?.let { Position.fromFen(it) } ?: Position.initial()
        var game = Game.new(start, tags)
        for (token in tokenize(moveText.toString())) {
            if (token in resultTokens) {
                if (tags["Result"] == null) game = game.withTags(tags + ("Result" to token.replace("½-½", "1/2-1/2")))
                break
            }
            val move = San.parse(game.current, token)
                ?: throw ChessFormatException("Coup « $token » illégal ou illisible au demi-coup ${game.ply + 1}.")
            game = game.play(move)
        }
        return game
    }

    /** Découpe le texte des coups en éliminant numéros, commentaires, variantes et NAG. */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        var depth = 0
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                var t = sb.toString()
                sb.clear()
                if (depth > 0) return
                t = t.replace(Regex("^\\d+\\.+"), "")
                if (t.isEmpty() || t.matches(Regex("\\d+\\.*")) || t.startsWith("$")) return
                if (t == "...") return
                tokens += t
            }
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '{' -> { flush(); val end = text.indexOf('}', i); i = if (end < 0) text.length else end }
                c == ';' -> { flush(); val end = text.indexOf('\n', i); i = if (end < 0) text.length else end }
                c == '(' -> { flush(); depth++ }
                c == ')' -> { flush(); depth = (depth - 1).coerceAtLeast(0) }
                c.isWhitespace() -> flush()
                else -> sb.append(c)
            }
            i++
        }
        flush()
        return tokens
    }

    fun write(game: Game): String = buildString {
        val tags = linkedMapOf(
            "Event" to "Chess Overlay AI",
            "Site" to "Local",
            "Date" to "????.??.??",
            "Round" to "-",
            "White" to "?",
            "Black" to "?",
        )
        tags.putAll(game.tags)
        tags["Result"] = game.result
        if (game.initial.toFen() != Position.START_FEN) {
            tags["SetUp"] = "1"; tags["FEN"] = game.initial.toFen()
        }
        for ((k, v) in tags) append("[").append(k).append(" \"").append(v.replace("\"", "\\\"")).append("\"]\n")
        append('\n')
        val line = StringBuilder()
        var moveNo = game.initial.fullmoveNumber
        var white = game.initial.sideToMove == PieceColor.WHITE
        for ((i, san) in game.sans.withIndex()) {
            val token = when {
                white -> "$moveNo. $san"
                i == 0 -> "$moveNo... $san"
                else -> san
            }
            if (line.length + token.length > 78) { append(line.toString().trimEnd()).append('\n'); line.clear() }
            line.append(token).append(' ')
            if (!white) moveNo++
            white = !white
        }
        line.append(game.result)
        append(line.toString().trim()).append('\n')
    }
}
