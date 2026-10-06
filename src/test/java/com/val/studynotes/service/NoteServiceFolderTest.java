package com.val.studynotes.service;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.FolderNotFoundException;
import com.val.studynotes.exception.InvalidReferenceException;
import com.val.studynotes.exception.NoteNotFoundException;
import com.val.studynotes.mapper.NoteMapper;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Работа NoteService с папкой при создании и обновлении. Маппер настоящий, чтобы проверять NoteResponse. */
@ExtendWith(MockitoExtension.class)
class NoteServiceFolderTest {

    @Mock
    private NoteRepository noteRepository;

    @Spy
    private NoteMapper noteMapper = new NoteMapper();

    @Mock
    private FolderService folderService;

    @InjectMocks
    private NoteService noteService;

    private static NoteRequest request(Long folderId) {
        NoteRequest request = new NoteRequest();
        request.setTitle("Заголовок");
        request.setContent("Текст");
        request.setFolderId(folderId);
        return request;
    }

    private static Folder folder(Long id, String name, Folder parent) {
        Folder folder = new Folder(name, parent);
        folder.setId(id);
        return folder;
    }

    @Test
    @DisplayName("createNote с folderId: папка ставится, в ответе folderId, folderName и folderPath")
    void createNote_withFolder_setsFolder() {
        Folder root = folder(1L, "Java", null);
        Folder child = folder(2L, "Collections", root);
        when(folderService.getById(2L)).thenReturn(child);
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponse result = noteService.createNote(request(2L));

        assertEquals(2L, result.getFolderId());
        assertEquals("Collections", result.getFolderName());
        assertEquals("Java / Collections", result.getFolderPath());
        verify(noteRepository).save(argThat(n -> n.getFolder() == child));
    }

    @Test
    @DisplayName("createNote без folderId: заметка без папки, FolderService не вызывается")
    void createNote_nullFolder_noFolder() {
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponse result = noteService.createNote(request(null));

        assertNull(result.getFolderId());
        verifyNoInteractions(folderService);
    }

    @Test
    @DisplayName("createNote с несуществующей папкой: InvalidReferenceException на folderId, заметка не сохраняется")
    void createNote_unknownFolder_throws() {
        when(folderService.getById(999L)).thenThrow(new FolderNotFoundException(999L));

        InvalidReferenceException ex = assertThrows(InvalidReferenceException.class,
                () -> noteService.createNote(request(999L)));

        assertEquals("folderId", ex.getField());
        assertEquals("Папка не найдена", ex.getMessage());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateNote с folderId: папка меняется")
    void updateNote_withFolder_setsFolder() {
        Folder target = folder(5L, "SQL", null);
        Note existing = new Note();
        existing.setId(1L);
        existing.setFolder(folder(1L, "Старая", null));
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(folderService.getById(5L)).thenReturn(target);
        when(noteRepository.save(existing)).thenReturn(existing);

        NoteResponse result = noteService.updateNote(1L, request(5L));

        assertSame(target, existing.getFolder());
        assertEquals(5L, result.getFolderId());
        assertEquals("SQL", result.getFolderPath());
    }

    @Test
    @DisplayName("updateNote с folderId = null: папка снимается (PUT заменяет заметку целиком)")
    void updateNote_nullFolder_clearsFolder() {
        Note existing = new Note();
        existing.setId(1L);
        existing.setFolder(folder(1L, "Старая", null));
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(existing)).thenReturn(existing);

        NoteResponse result = noteService.updateNote(1L, request(null));

        assertNull(existing.getFolder());
        assertNull(result.getFolderId());
        verifyNoInteractions(folderService);
    }

    @Test
    @DisplayName("updateNote с несуществующей папкой: InvalidReferenceException, заметка не сохраняется")
    void updateNote_unknownFolder_throws() {
        Note existing = new Note();
        existing.setId(1L);
        Folder old = folder(1L, "Старая", null);
        existing.setFolder(old);
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(folderService.getById(999L)).thenThrow(new FolderNotFoundException(999L));

        InvalidReferenceException ex = assertThrows(InvalidReferenceException.class,
                () -> noteService.updateNote(1L, request(999L)));

        assertEquals("folderId", ex.getField());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateNote несуществующей заметки: NoteNotFoundException раньше проверки папки")
    void updateNote_unknownNote_throwsNoteNotFound() {
        when(noteRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(NoteNotFoundException.class, () -> noteService.updateNote(404L, request(999L)));

        verifyNoInteractions(folderService);
    }
}
