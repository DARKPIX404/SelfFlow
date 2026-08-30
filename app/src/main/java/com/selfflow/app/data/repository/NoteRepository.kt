package com.selfflow.app.data.repository

import com.selfflow.app.data.local.dao.NoteDao
import com.selfflow.app.data.local.entity.NoteEntity
import com.selfflow.app.domain.model.Note
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class NoteRepository constructor(
    private val noteDao: NoteDao
) {
    fun getAll(): Flow<List<Note>> =
        noteDao.getAll().map { entities -> entities.map { it.toDomain() } }

    fun getById(id: Long): Flow<Note?> =
        flow { emit(noteDao.getById(id)?.toDomain()) }

    fun search(query: String): Flow<List<Note>> =
        noteDao.search(query).map { entities -> entities.map { it.toDomain() } }

    suspend fun insert(note: Note): Long =
        noteDao.insert(note.toEntity())

    suspend fun update(note: Note): Int {
        noteDao.update(note.toEntity())
        return 1
    }

    suspend fun delete(note: Note) {
        noteDao.delete(note.toEntity())
    }

    suspend fun deleteById(id: Long) {
        noteDao.deleteById(id)
    }

    private fun NoteEntity.toDomain(): Note =
        Note(
            id = id,
            title = title,
            content = content,
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun Note.toEntity(): NoteEntity =
        NoteEntity(
            id = id,
            title = title,
            content = content,
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
