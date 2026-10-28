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
