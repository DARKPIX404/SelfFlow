package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.repository.NoteRepository
import com.selfflow.app.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val notes: StateFlow<List<Note>> = noteRepository.getAll()
        .combine(_searchQuery) { allNotes, query ->
            if (query.isBlank()) {
                allNotes
            } else {
                val normalizedQuery = query.trim().lowercase()
                allNotes.filter { note ->
                    note.title.lowercase().contains(normalizedQuery) ||
                        note.content.lowercase().contains(normalizedQuery) ||
                        note.tags.any { it.lowercase().contains(normalizedQuery) }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            if (note.id == 0L) {
                noteRepository.insert(note)
            } else {
                noteRepository.update(note)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.delete(note)
        }
    }
}
