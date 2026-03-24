package com.val.studynotes.service;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}