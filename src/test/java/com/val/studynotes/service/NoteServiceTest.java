package com.val.studynotes.service;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.NoteNotFoundException;
import com.val.studynotes.mapper.NoteMapper;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private NoteService noteService;

    @Test
    @DisplayName("getNoteById: заметка найдена — возвращает NoteResponse")
    void getNoteById_found_returnsResponse() {
        Note note = new Note();
        note.setId(1L);
        note.setTitle("Stream API");
        note.setContent("Текст о стримах");
        NoteResponse response = new NoteResponse();
        response.setId(1L);
        response.setTitle("Stream API");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteMapper.toResponse(note)).thenReturn(response);
        NoteResponse result = noteService.getNoteById(1L);
        assertEquals(1L, result.getId());
        assertEquals("Stream API", result.getTitle());
        verify(noteRepository).findById(1L);
        verify(noteMapper).toResponse(note);
    }

    @Test
    @DisplayName("getNoteById: заметка не найдена — бросает NoteNotFoundException")
    void getNoteById_notFound_throwsException() {
        when(noteRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class, () -> {
            noteService.getNoteById(999L);
        });
        verify(noteRepository).findById(999L);          // репозиторий был вызван
        verify(noteMapper, never()).toResponse(any());   // маппер НЕ был вызван
    }

    @Test
    @DisplayName("getAllNotes: возвращает список NoteResponse")
    void getAllNotes_returnsList() {
        Note note1 = new Note();
        note1.setId(1L);
        note1.setTitle("Stream API");
        Note note2 = new Note();
        note2.setId(2L);
        note2.setTitle("Spring Boot");
        NoteResponse response1 = new NoteResponse();
        response1.setId(1L);
        response1.setTitle("Stream API");
        NoteResponse response2 = new NoteResponse();
        response2.setId(2L);
        response2.setTitle("Spring Boot");
        when(noteRepository.findAll()).thenReturn(List.of(note1, note2));
        when(noteMapper.toResponse(note1)).thenReturn(response1);
        when(noteMapper.toResponse(note2)).thenReturn(response2);
        List<NoteResponse> result = noteService.getAllNotes();
        assertEquals(2, result.size());
        assertEquals("Stream API", result.get(0).getTitle());
        assertEquals("Spring Boot", result.get(1).getTitle());
        verify(noteRepository).findAll();
        verify(noteMapper).toResponse(note1);
        verify(noteMapper).toResponse(note2);
    }

    @Test
    @DisplayName("getAllNotes: нет заметок — пустой список")
    void getAllNotes_empty_returnsEmptyList() {
        when(noteRepository.findAll()).thenReturn(List.of());
        List<NoteResponse> result = noteService.getAllNotes();
        assertTrue(result.isEmpty());
        verify(noteRepository).findAll();
        verify(noteMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("createNote: создаёт заметку и возвращает NoteResponse")
    void createNote_savesAndReturnsResponse() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Новая заметка");
        request.setContent("Содержимое");
        Note note = new Note();
        note.setTitle("Новая заметка");
        note.setContent("Содержимое");
        Note savedNote = new Note();
        savedNote.setId(10L);
        savedNote.setTitle("Новая заметка");
        savedNote.setContent("Содержимое");
        NoteResponse response = new NoteResponse();
        response.setId(10L);
        response.setTitle("Новая заметка");
        when(noteMapper.toEntity(request)).thenReturn(note);
        when(noteRepository.save(note)).thenReturn(savedNote);
        when(noteMapper.toResponse(savedNote)).thenReturn(response);
        NoteResponse result = noteService.createNote(request);
        assertEquals(10L, result.getId());
        assertEquals("Новая заметка", result.getTitle());
        verify(noteMapper).toEntity(request);
        verify(noteRepository).save(note);
        verify(noteMapper).toResponse(savedNote);
    }

    @Test
    @DisplayName("updateNote: обновляет и возвращает NoteResponse")
    void updateNote_updatesAndReturnsResponse() {
        Note existingNote = new Note();
        existingNote.setId(1L);
        existingNote.setTitle("Старый заголовок");
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый заголовок");
        request.setContent("Новое содержимое");
        Note savedNote = existingNote;
        NoteResponse response = new NoteResponse();
        response.setId(1L);
        response.setTitle("Новый заголовок");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existingNote));
        when(noteRepository.save(existingNote)).thenReturn(savedNote);
        when(noteMapper.toResponse(savedNote)).thenReturn(response);
        NoteResponse result = noteService.updateNote(1L, request);
        assertEquals(1L, result.getId());
        assertEquals("Новый заголовок", result.getTitle());
        verify(noteRepository).findById(1L);
        verify(noteMapper).updateEntity(existingNote, request);  // void — но verify работает
        verify(noteRepository).save(existingNote);
        verify(noteMapper).toResponse(savedNote);
    }

    @Test
    @DisplayName("updateNote: заметка не найдена — бросает NoteNotFoundException")
    void updateNote_notFound_throwsException() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый");
        when(noteRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class, () -> {
            noteService.updateNote(999L, request);
        });

        verify(noteRepository).findById(999L);
        verify(noteMapper, never()).updateEntity(any(), any());
        verify(noteRepository, never()).save(any());
        verify(noteMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("deleteNote: заметка найдена — удаляет")
    void deleteNote_found_deletes() {
        Note note = new Note();
        note.setId(1L);
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        noteService.deleteNote(1L);
        verify(noteRepository).findById(1L);  // нашёл заметку
        verify(noteRepository).delete(note);  // удалил именно эту заметку
    }

    @Test
    @DisplayName("deleteNote: заметка не найдена — бросает NoteNotFoundException")
    void deleteNote_notFound_throwsException() {
        when(noteRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class, () -> {
            noteService.deleteNote(999L);
        });
        verify(noteRepository).findById(999L);
        verify(noteRepository, never()).delete(any());  // delete НЕ вызван
    }

    @Test
    @DisplayName("getNotesByFolder: folderId не null — вызывает findByFolderId")
    void getNotesByFolder_withFolderId_callsFindByFolderId() {
        Note note = new Note();
        note.setId(1L);
        NoteResponse response = new NoteResponse();
        response.setId(1L);
        when(noteRepository.findByFolderId(5L)).thenReturn(List.of(note));
        when(noteMapper.toResponse(note)).thenReturn(response);
        List<NoteResponse> result = noteService.getNotesByFolder(5L);
        assertEquals(1, result.size());
        verify(noteRepository).findByFolderId(5L);                    // этот вызван
        verify(noteRepository, never()).findByFolderIsNull();          // этот НЕ вызван
    }

    @Test
    @DisplayName("getNotesByFolder: folderId null — вызывает findByFolderIsNull")
    void getNotesByFolder_nullFolderId_callsFindByFolderIsNull() {
        Note note = new Note();
        note.setId(1L);
        NoteResponse response = new NoteResponse();
        response.setId(1L);
        when(noteRepository.findByFolderIsNull()).thenReturn(List.of(note));
        when(noteMapper.toResponse(note)).thenReturn(response);
        List<NoteResponse> result = noteService.getNotesByFolder(null);
        assertEquals(1, result.size());
        verify(noteRepository).findByFolderIsNull();                  // этот вызван
        verify(noteRepository, never()).findByFolderId(anyLong());     // этот НЕ вызван
    }

    @Test
    @DisplayName("searchNotes: пустой запрос — пустой список, репозиторий не вызывается")
    void searchNotes_blankQuery_returnsEmptyWithoutCallingRepo() {
        List<NoteResponse> result = noteService.searchNotes("   ");
        assertTrue(result.isEmpty());
        verify(noteRepository, never()).fullTextSearch(anyString());
        verify(noteMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("searchNotes: null — пустой список")
    void searchNotes_null_returnsEmpty() {
        List<NoteResponse> result = noteService.searchNotes(null);
        assertTrue(result.isEmpty());
        verify(noteRepository, never()).fullTextSearch(anyString());
    }

    @Test
    @DisplayName("searchNotes: запрос с результатами — возвращает список")
    void searchNotes_withResults_returnsList() {
        Note note = new Note();
        note.setId(1L);
        note.setTitle("Stream API");
        NoteResponse response = new NoteResponse();
        response.setId(1L);
        response.setTitle("Stream API");
        when(noteRepository.fullTextSearch("Stream")).thenReturn(List.of(note));
        when(noteMapper.toResponse(note)).thenReturn(response);
        List<NoteResponse> result = noteService.searchNotes("Stream");
        assertEquals(1, result.size());
        assertEquals("Stream API", result.get(0).getTitle());
        verify(noteRepository).fullTextSearch("Stream");
    }

    @Test
    @DisplayName("searchNotes: запрос с пробелами — trim перед поиском")
    void searchNotes_withSpaces_trimmed() {
        when(noteRepository.fullTextSearch("Spring")).thenReturn(List.of());
        noteService.searchNotes("  Spring  ");
        verify(noteRepository).fullTextSearch("Spring");
    }
}