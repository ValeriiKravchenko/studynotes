package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.InvalidReferenceException;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Фильтр по папке, folderId в теле запроса и поиск (шаги 3-5); дополняет NoteControllerTest. */
@WebMvcTest(NoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoteControllerFolderSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    private static NoteResponse response(Long id, String title) {
        NoteResponse r = new NoteResponse();
        r.setId(id);
        r.setTitle(title);
        return r;
    }

    // ---- folderId в списке ----

    @Test
    @DisplayName("GET /api/notes?folderId=5: вызывает getNotesByFolder(5), getAllNotes не вызывается")
    void getAll_withFolderId_filtersByFolder() throws Exception {
        when(noteService.getNotesByFolder(5L)).thenReturn(List.of(response(1L, "В папке")));

        mockMvc.perform(get("/api/notes").param("folderId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("В папке"));

        verify(noteService).getNotesByFolder(5L);
        verify(noteService, never()).getAllNotes();
    }

    @Test
    @DisplayName("GET /api/notes без folderId: getAllNotes, getNotesByFolder не вызывается")
    void getAll_withoutFolderId_returnsAll() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of());

        mockMvc.perform(get("/api/notes")).andExpect(status().isOk());

        verify(noteService).getAllNotes();
        verify(noteService, never()).getNotesByFolder(any());
    }

    @Test
    @DisplayName("GET /api/notes?folderId=abc: 400, сервис не вызывается")
    void getAll_nonNumericFolderId_returns400() throws Exception {
        mockMvc.perform(get("/api/notes").param("folderId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное значение параметра 'folderId'"));

        verifyNoInteractions(noteService);
    }

    // ---- folderId в теле ----

    @Test
    @DisplayName("POST /api/notes с folderId: значение уходит в сервис")
    void create_withFolderId_passesToService() throws Exception {
        when(noteService.createNote(any(NoteRequest.class))).thenReturn(response(10L, "Новая"));

        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая\",\"content\":\"Тело\",\"folderId\":7}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).createNote(captor.capture());
        assertEquals(7L, captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("POST /api/notes без folderId: в сервис уходит null")
    void create_withoutFolderId_passesNull() throws Exception {
        when(noteService.createNote(any(NoteRequest.class))).thenReturn(response(10L, "Новая"));

        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая\",\"content\":\"Тело\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).createNote(captor.capture());
        assertNull(captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с folderId: значение уходит в сервис")
    void update_withFolderId_passesToService() throws Exception {
        when(noteService.updateNote(eq(3L), any(NoteRequest.class))).thenReturn(response(3L, "Изм"));

        mockMvc.perform(put("/api/notes/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Изм\",\"folderId\":9}"))
                .andExpect(status().isOk());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).updateNote(eq(3L), captor.capture());
        assertEquals(9L, captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с \"folderId\": null: в сервис уходит null (снять папку)")
    void update_withNullFolderId_passesNull() throws Exception {
        when(noteService.updateNote(eq(3L), any(NoteRequest.class))).thenReturn(response(3L, "Изм"));

        mockMvc.perform(put("/api/notes/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Изм\",\"folderId\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).updateNote(eq(3L), captor.capture());
        assertNull(captor.getValue().getFolderId());
    }

    @Test
    @DisplayName("POST /api/notes с несуществующим folderId: 400 с fieldErrors на folderId")
    void create_unknownFolder_returns400WithFieldError() throws Exception {
        when(noteService.createNote(any(NoteRequest.class)))
                .thenThrow(new InvalidReferenceException("folderId", "Папка не найдена"));

        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая\",\"folderId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Некорректные данные запроса"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("folderId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Папка не найдена"));
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с несуществующим folderId: 400 с fieldErrors на folderId")
    void update_unknownFolder_returns400WithFieldError() throws Exception {
        when(noteService.updateNote(eq(3L), any(NoteRequest.class)))
                .thenThrow(new InvalidReferenceException("folderId", "Папка не найдена"));

        mockMvc.perform(put("/api/notes/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Изм\",\"folderId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("folderId"));
    }

    @Test
    @DisplayName("POST /api/notes с нечисловым folderId: 400, сервис не вызывается")
    void create_nonNumericFolderId_returns400() throws Exception {
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая\",\"folderId\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(noteService);
    }

    // ---- поиск ----

    @Test
    @DisplayName("GET /api/notes/search?query=java: 200 и результат сервиса")
    void search_returnsServiceResult() throws Exception {
        when(noteService.searchNotes("java")).thenReturn(List.of(response(1L, "Java"), response(2L, "JVM")));

        mockMvc.perform(get("/api/notes/search").param("query", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Java"));

        verify(noteService).searchNotes("java");
        verify(noteService, never()).getNoteById(any());
    }

    @Test
    @DisplayName("GET /api/notes/search не перехватывается GET /api/notes/{id}")
    void search_isNotCapturedByGetById() throws Exception {
        when(noteService.searchNotes("x")).thenReturn(List.of());

        mockMvc.perform(get("/api/notes/search").param("query", "x"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(noteService, never()).getNoteById(any());
    }

    @Test
    @DisplayName("GET /api/notes/search без query: 400")
    void search_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/notes/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Отсутствует обязательный параметр 'query'"));

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("GET /api/notes/search?query= (пустой): 200 и []")
    void search_emptyQuery_returnsEmptyArray() throws Exception {
        when(noteService.searchNotes("")).thenReturn(List.of());

        mockMvc.perform(get("/api/notes/search").param("query", ""))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/notes/search с пробельным query: 200 и []")
    void search_blankQuery_returnsEmptyArray() throws Exception {
        when(noteService.searchNotes("   ")).thenReturn(List.of());

        mockMvc.perform(get("/api/notes/search").param("query", "   "))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /api/notes/search: ровно 200 символов проходит")
    void search_query200Chars_isAccepted() throws Exception {
        String query = "a".repeat(200);
        when(noteService.searchNotes(query)).thenReturn(List.of());

        mockMvc.perform(get("/api/notes/search").param("query", query))
                .andExpect(status().isOk());

        verify(noteService).searchNotes(query);
    }

    @Test
    @DisplayName("GET /api/notes/search: 201 символ — 400, сервис не вызывается, значение в ответ не попадает")
    void search_query201Chars_returns400() throws Exception {
        String query = "a".repeat(201);

        mockMvc.perform(get("/api/notes/search").param("query", query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(query))));

        verifyNoInteractions(noteService);
    }
}
