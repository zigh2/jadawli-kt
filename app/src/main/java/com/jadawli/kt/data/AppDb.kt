package com.jadawli.kt.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JDao {
    @Query("SELECT * FROM jtables ORDER BY id")
    fun tables(): Flow<List<TableEntity>>

    @Query("SELECT * FROM cells WHERE t = :t")
    fun cells(t: Long): Flow<List<CellEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    fun noteCount(): Flow<Int>

    @Upsert
    suspend fun upsertCell(c: CellEntity)

    @Query("DELETE FROM cells WHERE k = :k")
    suspend fun deleteCell(k: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(l: List<TableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(l: List<CellEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(l: List<NoteEntity>)

    @Query("DELETE FROM jtables")
    suspend fun clearTables()

    @Query("DELETE FROM cells")
    suspend fun clearCells()

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    @Query("SELECT * FROM notes WHERE t = :t AND kind = 'row'")
    fun rowNotes(t: Long): Flow<List<NoteEntity>>

    @Query("SELECT v FROM cells WHERE k = :k")
    suspend fun cellValue(k: String): String?

    @Query("SELECT text FROM notes WHERE t = :t AND kind = 'row' AND r = :r LIMIT 1")
    suspend fun rowNoteText(t: Long, r: String): String?

    @Query("DELETE FROM notes WHERE t = :t AND kind = 'row' AND r = :r")
    suspend fun deleteRowNote(t: Long, r: String)

    @Upsert
    suspend fun upsertNote(n: NoteEntity)

    @Query("SELECT * FROM jtables WHERE id = :id")
    suspend fun tableById(id: Long): TableEntity?

    @Query("UPDATE cells SET c = :nw, k = t || char(1) || r || char(1) || :nw WHERE t = :t AND c = :old")
    suspend fun renameColumn(t: Long, old: String, nw: String)

    @Query("DELETE FROM cells WHERE t = :t AND c = :c")
    suspend fun deleteColumnCells(t: Long, c: String)

    @Query("DELETE FROM cells WHERE t = :t AND r = :r")
    suspend fun deleteRowCells(t: Long, r: String)

    @Query("DELETE FROM cells WHERE t = :t")
    suspend fun deleteCellsOfTable(t: Long)

    @Query("DELETE FROM notes WHERE t = :t")
    suspend fun deleteNotesOfTable(t: Long)

    @Query("DELETE FROM jtables WHERE id = :id")
    suspend fun deleteTable(id: Long)

    @Query("SELECT * FROM jtables ORDER BY id")
    suspend fun allTables(): List<TableEntity>

    @Query("SELECT * FROM cells")
    suspend fun allCells(): List<CellEntity>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT * FROM cells WHERE t = :t")
    suspend fun cellsList(t: Long): List<CellEntity>

    @Query("SELECT COALESCE(MAX(id), 0) FROM jtables")
    suspend fun maxTableId(): Long
}

@Database(entities = [TableEntity::class, CellEntity::class, NoteEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): JDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "jadawli.db").build().also { inst = it }
        }
    }
}
