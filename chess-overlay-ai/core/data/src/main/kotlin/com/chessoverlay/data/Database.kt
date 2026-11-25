package com.chessoverlay.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Partie enregistrée (entraînement, import PGN, analyse). */
@Entity(tableName = "games")
data class SavedGameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val white: String,
    val black: String,
    val result: String,
    val pgn: String,
    val finalFen: String,
    val source: String,
    val plyCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Position enregistrée. [category] : "perso", "erreur" (issue d'une revue de partie), "reconnaissance". */
@Entity(tableName = "positions")
data class SavedPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fen: String,
    val note: String = "",
    val category: String = "perso",
    /** Meilleur coup UCI connu (pour les exercices créés depuis une erreur). */
    val bestMoveUci: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Historique des analyses réalisées. */
@Entity(tableName = "analyses")
data class AnalysisRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fen: String,
    val bestMoveUci: String,
    val bestMoveText: String,
    val evaluation: String,
    val depth: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): SavedGameEntity?

    @Insert suspend fun insert(game: SavedGameEntity): Long
    @Update suspend fun update(game: SavedGameEntity)
    @Delete suspend fun delete(game: SavedGameEntity)

    @Query("DELETE FROM games") suspend fun clear()
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPositionEntity>>

    @Query("SELECT * FROM positions WHERE category = :category ORDER BY createdAt DESC")
    fun observeCategory(category: String): Flow<List<SavedPositionEntity>>

    @Insert suspend fun insert(position: SavedPositionEntity): Long
    @Delete suspend fun delete(position: SavedPositionEntity)

    @Query("DELETE FROM positions") suspend fun clear()
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<AnalysisRecordEntity>>

    @Insert suspend fun insert(record: AnalysisRecordEntity): Long

    @Query("DELETE FROM analyses WHERE id NOT IN (SELECT id FROM analyses ORDER BY createdAt DESC LIMIT 300)")
    suspend fun trim()

    @Query("DELETE FROM analyses") suspend fun clear()
}

@Database(
    entities = [SavedGameEntity::class, SavedPositionEntity::class, AnalysisRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ChessDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun positions(): PositionDao
    abstract fun analyses(): AnalysisDao

    companion object {
        fun create(context: Context): ChessDatabase =
            Room.databaseBuilder(context.applicationContext, ChessDatabase::class.java, "chess_overlay.db").build()
    }
}
