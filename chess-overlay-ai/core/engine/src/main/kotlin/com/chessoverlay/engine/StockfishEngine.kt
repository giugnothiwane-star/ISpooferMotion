package com.chessoverlay.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Interface du moteur, pour permettre des doublures dans les tests. */
interface ChessEngine {
    val name: String
    suspend fun start()
    suspend fun configure(options: EngineOptions)
    suspend fun newGame()
    /** Analyse une position ; émet des instantanés successifs puis un instantané final ([AnalysisSnapshot.finished]). */
    fun analyze(fen: String, limits: SearchLimits, moves: List<String> = emptyList()): Flow<AnalysisSnapshot>
    suspend fun search(fen: String, limits: SearchLimits, moves: List<String> = emptyList()): AnalysisSnapshot =
        analyze(fen, limits, moves).last()
    /** Demande l'arrêt de la recherche en cours (l'analyse se termine avec le meilleur coup trouvé). */
    fun stop()
    fun close()
}

/**
 * Pilote un exécutable Stockfish via le protocole UCI (entrée/sortie standard du processus).
 * Une seule recherche s'exécute à la fois ; les appels concurrents sont mis en file d'attente.
 *
 * @param command commande de lancement, par ex. listOf("/data/app/.../lib/arm64/libstockfish.so").
 */
class StockfishEngine(
    private val command: List<String>,
    private val workDir: File? = null,
    private val throttleMs: Long = 120,
) : ChessEngine {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var lines = Channel<String>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private var currentOptions: EngineOptions? = null
    private var currentMultiPv = -1
    @Volatile private var searching = false

    override var name: String = "Stockfish"
        private set

    val isRunning: Boolean get() = process?.isAlive == true

    override suspend fun start() = withContext(Dispatchers.IO) {
        mutex.withLock { ensureStarted() }
    }

    private suspend fun ensureStarted() {
        if (process?.isAlive == true) return
        val pb = ProcessBuilder(command).redirectErrorStream(true)
        workDir?.let { pb.directory(it) }
        val p = try {
            pb.start()
        } catch (e: Exception) {
            throw EngineException("Impossible de lancer Stockfish (${command.firstOrNull()}) : ${e.message}", e)
        }
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        val channel = Channel<String>(Channel.UNLIMITED)
        lines = channel
        Thread({
            try {
                p.inputStream.bufferedReader().useLines { seq -> seq.forEach { channel.trySend(it) } }
            } catch (_: Exception) {
            } finally {
                channel.close()
            }
        }, "stockfish-reader").apply { isDaemon = true }.start()
        currentMultiPv = -1
        send("uci")
        waitFor { line ->
            if (line.startsWith("id name ")) name = line.removePrefix("id name ").trim()
            line == "uciok"
        }
        currentOptions?.let { applyOptions(it) }
        sync()
    }

    private fun send(cmd: String) {
        val w = writer ?: throw EngineException("Le moteur n'est pas démarré.")
        synchronized(w) {
            try {
                w.write(cmd); w.newLine(); w.flush()
            } catch (e: Exception) {
                throw EngineException("Communication avec Stockfish interrompue : ${e.message}", e)
            }
        }
    }

    private suspend fun readLine(): String = try {
        lines.receive()
    } catch (e: ClosedReceiveChannelException) {
        throw EngineException("Le moteur Stockfish s'est arrêté de façon inattendue.", e)
    }

    private suspend fun waitFor(timeoutMs: Long = 15_000, predicate: (String) -> Boolean) {
        withTimeout(timeoutMs) {
            while (true) if (predicate(readLine())) break
        }
    }

    /** « isready » / « readyok » : vide les messages en attente et garantit que le moteur est disponible. */
    private suspend fun sync() {
        send("isready")
        waitFor { it == "readyok" }
    }

    private fun applyOptions(o: EngineOptions) {
        send("setoption name Threads value ${o.threads.coerceIn(1, 64)}")
        send("setoption name Hash value ${o.hashMb.coerceIn(16, 2048)}")
        if (o.limitElo != null) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${o.limitElo.coerceIn(1320, 3190)}")
        } else {
            send("setoption name UCI_LimitStrength value false")
            send("setoption name Skill Level value ${o.skillLevel.coerceIn(0, 20)}")
        }
    }

    override suspend fun configure(options: EngineOptions) = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentOptions = options
            if (process?.isAlive == true) {
                applyOptions(options)
                sync()
            }
        }
    }

    override suspend fun newGame() = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureStarted()
            send("ucinewgame")
            sync()
        }
    }

    override fun analyze(fen: String, limits: SearchLimits, moves: List<String>): Flow<AnalysisSnapshot> = flow {
        mutex.withLock {
            ensureStarted()
            sync()
            val multi = limits.multiPv.coerceIn(1, 10)
            if (multi != currentMultiPv) {
                send("setoption name MultiPV value $multi")
                currentMultiPv = multi
            }
            val pos = buildString {
                append("position fen ").append(fen)
                if (moves.isNotEmpty()) append(" moves ").append(moves.joinToString(" "))
            }
            send(pos)
            send(limits.toGoCommand())
            searching = true
            val byPv = sortedMapOf<Int, PvLine>()
            var lastEmit = 0L
            var finished = false
            try {
                while (true) {
                    val line = readLine()
                    val info = UciParser.parseInfo(line)
                    if (info != null) {
                        val existing = byPv[info.multipv]
                        val bounded = info.score.lowerBound || info.score.upperBound
                        if (!bounded || existing == null) byPv[info.multipv] = info
                        val now = System.currentTimeMillis()
                        val depthComplete = info.multipv == multi
                        if (depthComplete || now - lastEmit >= throttleMs) {
                            lastEmit = now
                            emit(AnalysisSnapshot(fen, byPv.values.toList()))
                        }
                        continue
                    }
                    val best = UciParser.parseBestMove(line)
                    if (best != null) {
                        finished = true
                        searching = false
                        emit(AnalysisSnapshot(fen, byPv.values.toList(), best, finished = true))
                        break
                    }
                }
            } finally {
                if (!finished) {
                    // Annulation par l'appelant : on arrête proprement la recherche.
                    withContext(NonCancellable) {
                        runCatching {
                            send("stop")
                            waitFor(10_000) { it.startsWith("bestmove") }
                        }
                        searching = false
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        if (searching) runCatching { send("stop") }
    }

    override fun close() {
        runCatching { send("quit") }
        runCatching { writer?.close() }
        process?.let { p ->
            runCatching { if (!p.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroy() }
        }
        process = null
        writer = null
    }
}
