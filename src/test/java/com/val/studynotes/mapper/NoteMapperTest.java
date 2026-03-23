package com.val.studynotes.mapper;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoteMapperTest {
    private NoteMapper noteMapper;

    @BeforeEach
    void setUp() {
        noteMapper = new NoteMapper();
    }

    private Note createNote(Long id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setCreatedAt(LocalDateTime.of(2026, 3, 21, 10, 0));
        note.setUpdatedAt(LocalDateTime.of(2026, 3, 22, 15, 30));
        return note;
    }

    private Folder createFolder(Long id, String name, Folder parent) {
        Folder folder = new Folder(name, parent);
        folder.setId(id);
        return folder;
    }

    @Test
    @DisplayName("toEntity: title и content переносятся из NoteRequest")
    void toEntity_copiesTitleAndContent() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Stream API");
        request.setContent("Текст заметки о стримах");
        Note note = noteMapper.toEntity(request);
        assertEquals("Stream API", note.getTitle());
        assertEquals("Текст заметки о стримах", note.getContent());
    }

    @Test
    @DisplayName("toEntity: id не устанавливается (генерируется базой)")
    void toEntity_idIsNull() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Test");
        request.setContent("Content");
        Note note = noteMapper.toEntity(request);
        assertNull(note.getId());
    }

    @Test
    @DisplayName("toEntity: createdAt и updatedAt не устанавливаются (генерируются Hibernate)")
    void toEntity_datesAreNull() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Test");
        request.setContent("Content");
        Note note = noteMapper.toEntity(request);
        assertNull(note.getCreatedAt());
        assertNull(note.getUpdatedAt());
    }

    @Test
    @DisplayName("toEntity: folder не устанавливается")
    void toEntity_folderIsNull() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Test");
        request.setContent("Content");
        Note note = noteMapper.toEntity(request);
        assertNull(note.getFolder());
    }

    @Test
    @DisplayName("toResponse: все основные поля переносятся")
    void toResponse_copiesAllFields() {
        Note note = createNote(42L, "Stream API", "Текст о стримах");
        NoteResponse response = noteMapper.toResponse(note);
        assertEquals(42L, response.getId());
        assertEquals("Stream API", response.getTitle());
        assertEquals("Текст о стримах", response.getContent());
        assertEquals(LocalDateTime.of(2026, 3, 21, 10, 0),
                response.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 3, 22, 15, 30),
                response.getUpdatedAt());
    }

    @Test
    @DisplayName("toResponse: без папки — folderName, folderPath, folderId null")
    void toResponse_withoutFolder_folderFieldsNull() {
        Note note = createNote(1L, "Test", "Content");
        NoteResponse response = noteMapper.toResponse(note);
        assertNull(response.getFolderId());
        assertNull(response.getFolderName());
        assertNull(response.getFolderPath());
    }

    @Test
    @DisplayName("toResponse: с корневой папкой — folderPath = имя папки")
    void toResponse_withRootFolder_folderPathIsName() {
        Note note = createNote(1L, "Test", "Content");
        Folder folder = createFolder(10L, "Java", null);
        note.setFolder(folder);
        NoteResponse response = noteMapper.toResponse(note);
        assertEquals(10L, response.getFolderId());
        assertEquals("Java", response.getFolderName());
        assertEquals("Java", response.getFolderPath());
    }

    @Test
    @DisplayName("toResponse: с вложенной папкой — folderPath = полный путь")
    void toResponse_withNestedFolder_folderPathIsFullPath() {
        Note note = createNote(1L, "Test", "Content");
        Folder parent = createFolder(10L, "Java", null);
        Folder child = createFolder(20L, "Core", parent);
        note.setFolder(child);
        NoteResponse response = noteMapper.toResponse(note);
        assertEquals(20L, response.getFolderId());
        assertEquals("Core", response.getFolderName());
        assertEquals("Java / Core", response.getFolderPath());
    }

    @Test
    @DisplayName("toResponse: глубокая вложенность — три уровня")
    void toResponse_deepNesting_fullPathBuilt() {
        Folder level1 = createFolder(1L, "A", null);
        Folder level2 = createFolder(2L, "B", level1);
        Folder level3 = createFolder(3L, "C", level2);
        Note note = createNote(1L, "Test", "Content");
        note.setFolder(level3);
        NoteResponse response = noteMapper.toResponse(note);
        assertEquals("A / B / C", response.getFolderPath());
    }

    @Test
    @DisplayName("updateEntity: title и content обновляются")
    void updateEntity_updatesTitleAndContent() {
        Note existingNote = createNote(42L, "Старый заголовок", "Старое содержимое");
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый заголовок");
        request.setContent("Новое содержимое");
        noteMapper.updateEntity(existingNote, request);
        assertEquals("Новый заголовок", existingNote.getTitle());
        assertEquals("Новое содержимое", existingNote.getContent());
    }

    @Test
    @DisplayName("updateEntity: id не меняется")
    void updateEntity_idUnchanged() {
        Note existingNote = createNote(42L, "Старый", "Старое");
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый");
        request.setContent("Новое");
        noteMapper.updateEntity(existingNote, request);
        assertEquals(42L, existingNote.getId());
    }

    @Test
    @DisplayName("updateEntity: даты не затрагиваются")
    void updateEntity_datesUnchanged() {
        Note existingNote = createNote(42L, "Старый", "Старое");
        LocalDateTime originalCreated = existingNote.getCreatedAt();
        LocalDateTime originalUpdated = existingNote.getUpdatedAt();
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый");
        request.setContent("Новое");
        noteMapper.updateEntity(existingNote, request);
        assertEquals(originalCreated, existingNote.getCreatedAt());
        assertEquals(originalUpdated, existingNote.getUpdatedAt());
    }

    @Test
    @DisplayName("updateEntity: folder не затрагивается")
    void updateEntity_folderUnchanged() {
        Note existingNote = createNote(42L, "Старый", "Старое");
        Folder folder = createFolder(10L, "Java", null);
        existingNote.setFolder(folder);
        NoteRequest request = new NoteRequest();
        request.setTitle("Новый");
        request.setContent("Новое");
        noteMapper.updateEntity(existingNote, request);
        assertEquals(folder, existingNote.getFolder());
    }
}
