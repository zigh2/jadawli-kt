package com.jadawli.kt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** json = كائن الجدول كاملاً كما في نسخة جداولي (columns, rows, keyCols, ...) فلا يضيع أي حقل. */
@Entity(tableName = "jtables")
data class TableEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val json: String,
    val u: Long = 0
)

@Entity(tableName = "cells", indices = [Index("t")])
data class CellEntity(
    @PrimaryKey val k: String,
    val t: Long,
    val r: String,
    val c: String,
    val v: String,
    val u: Long
)

@Entity(tableName = "notes", indices = [Index("t")])
data class NoteEntity(
    @PrimaryKey val k: String,
    val kind: String,
    val t: Long,
    val r: String?,
    val text: String,
    val u: Long
)

fun cellKey(t: Long, r: String, c: String): String = t.toString() + "\u0001" + r + "\u0001" + c
