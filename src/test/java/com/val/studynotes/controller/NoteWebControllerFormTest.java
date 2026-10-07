package com.val.studynotes.controller;

import com.val.studynotes.config.SecurityConfig;
import com.val.studynotes.config.WebConfig;
import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.val.studynotes.testsupport.RestoreCsrfTokenRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Скрытое поле folderId в форме редактирования (HTTP-уровень с цепочкой безопасности, сервисы замоканы). */
@WebMvcTest(NoteWebController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
@ExtendWith(RestoreCsrfTokenRepository.class)
class NoteWebControllerFormTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;
    @MockitoBean
    private FolderService folderService;
    @MockitoBean
    private MarkdownService markdownService;

    @Test
    @DisplayName("GET /notes/{id}/edit: форма содержит скрытое поле folderId с папкой заметки")
    void editForm_hasHiddenFolderId() throws Exception {
        NoteResponse note = new NoteResponse();
        note.setId(4L);
        note.setTitle("Заметка");
        note.setContent("Текст");
        note.setFolderId(12L);
        when(noteService.getNoteById(4L)).thenReturn(note);

        mockMvc.perform(get("/notes/4/edit").with(user("any-user")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*<input[^>]*type=\"hidden\"[^>]*name=\"folderId\"[^>]*value=\"12\".*"
                                + "|(?s).*<input[^>]*name=\"folderId\"[^>]*value=\"12\"[^>]*type=\"hidden\".*")));
    }

    @Test
    @DisplayName("POST /notes/{id}: folderId из скрытого поля уходит в updateNote")
    void submit_passesFolderIdToService() throws Exception {
        mockMvc.perform(post("/notes/4").with(user("any-user")).with(csrf())
                        .param("title", "Заметка").param("content", "Текст").param("folderId", "12"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).updateNote(eq(4L), captor.capture());
        assertEquals(12L, captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("POST /notes/{id}: пустое скрытое поле folderId (заметка без папки) даёт null")
    void submit_emptyFolderId_isNull() throws Exception {
        mockMvc.perform(post("/notes/4").with(user("any-user")).with(csrf())
                        .param("title", "Заметка").param("content", "Текст").param("folderId", ""))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).updateNote(eq(4L), captor.capture());
        assertNull(captor.getValue().getFolderId());
    }
}
