package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.NoteNotFoundException;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Слой MVC без Spring Security (addFilters = false): безопасность проверяется в SecurityTest.
 * Сервис замокан.
 */
@WebMvcTest(NoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    private static NoteResponse response(Long id, String title, String content) {
        NoteResponse r = new NoteResponse();
        r.setId(id);
        r.setTitle(title);
        r.setContent(content);
        return r;
    }

    @Test
    @DisplayName("GET /api/notes: 200 и JSON-массив заметок")
    void getAll_returnsJsonArray() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of(
                response(1L, "Первая", "a"), response(2L, "Вторая", "b")));

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Первая"))
                .andExpect(jsonPath("$[1].title").value("Вторая"));
    }

    @Test
    @DisplayName("GET /api/notes: заметок нет — 200 и пустой массив")
    void getAll_empty_returnsEmptyArray() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of());

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/notes/{id}: 200 и тело заметки")
    void getById_found_returnsNote() throws Exception {
        when(noteService.getNoteById(5L)).thenReturn(response(5L, "Stream API", "Текст"));

        mockMvc.perform(get("/api/notes/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Stream API"))
                .andExpect(jsonPath("$.content").value("Текст"));
    }

    @Test
    @DisplayName("GET /api/notes/{id}: не найдена — 404 и ErrorResponse")
    void getById_notFound_returns404WithErrorBody() throws Exception {
        when(noteService.getNoteById(999L)).thenThrow(new NoteNotFoundException(999L));

        mockMvc.perform(get("/api/notes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value("Note not found with id: 999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/notes: 201 и созданная заметка; сервису уходит содержимое запроса")
    void create_returns201AndPassesRequestToService() throws Exception {
        when(noteService.createNote(any(NoteRequest.class))).thenReturn(response(10L, "Новая", "Тело"));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая\",\"content\":\"Тело\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Новая"));

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).createNote(captor.capture());
        assertEquals("Новая", captor.getValue().getTitle());
        assertEquals("Тело", captor.getValue().getContent());
    }

    @Test
    @DisplayName("PUT /api/notes/{id}: 200 и обновлённая заметка")
    void update_found_returnsUpdated() throws Exception {
        when(noteService.updateNote(eq(3L), any(NoteRequest.class))).thenReturn(response(3L, "Изм", "Новое"));

        mockMvc.perform(put("/api/notes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Изм\",\"content\":\"Новое\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("Изм"));

        ArgumentCaptor<NoteRequest> captor = ArgumentCaptor.forClass(NoteRequest.class);
        verify(noteService).updateNote(eq(3L), captor.capture());
        assertEquals("Изм", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("PUT /api/notes/{id}: не найдена — 404")
    void update_notFound_returns404() throws Exception {
        when(noteService.updateNote(eq(404L), any(NoteRequest.class))).thenThrow(new NoteNotFoundException(404L));

        mockMvc.perform(put("/api/notes/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("DELETE /api/notes/{id}: 204 без тела")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/notes/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(noteService).deleteNote(7L);
    }

    @Test
    @DisplayName("DELETE /api/notes/{id}: не найдена — 404")
    void delete_notFound_returns404() throws Exception {
        doThrow(new NoteNotFoundException(8L)).when(noteService).deleteNote(8L);

        mockMvc.perform(delete("/api/notes/8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Note not found with id: 8"));
    }

    @Test
    @DisplayName("Непредвиденная ошибка сервиса — 500 и обезличенное сообщение без деталей исключения")
    void unexpectedServiceError_returns500WithGenericBody() throws Exception {
        when(noteService.getAllNotes()).thenThrow(new IllegalStateException("секретная деталь: пароль БД"));

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Произошла непредвиденная ошибка"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("секретная деталь"))));
    }

    @Test
    @DisplayName("POST /api/notes: невалидный JSON — 400")
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{не json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("GET /api/notes/abc: нечисловой id — 400")
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/notes/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/notes/1: метод не поддерживается — 405")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(patch("/api/notes/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /api/nope: несуществующий путь — 404 в формате ErrorResponse")
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ресурс не найден"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/notes: Content-Type text/plain — 415, заголовок Accept и JSON-тело")
    void create_unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists("Accept"))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("Неподдерживаемый тип содержимого"))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("GET /api/notes с Accept: application/xml — 406 с JSON-телом")
    void getAll_unacceptableAccept_returns406() throws Exception {
        mockMvc.perform(get("/api/notes").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("Not Acceptable"))
                .andExpect(jsonPath("$.message").value("Неприемлемый формат ответа"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("PATCH /api/notes/1: 405 с заголовком Allow и телом ErrorResponse")
    void unsupportedMethod_returnsAllowHeaderAndBody() throws Exception {
        mockMvc.perform(patch("/api/notes/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("GET"),
                        org.hamcrest.Matchers.containsString("PUT"),
                        org.hamcrest.Matchers.containsString("DELETE"))))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value("Метод не поддерживается"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- Валидация тела запроса ----

    private static final String TITLE_REQUIRED = "Заголовок обязателен";
    private static final String TITLE_TOO_LONG = "Заголовок не длиннее 255 символов";
    private static final String CONTENT_TOO_LONG = "Содержимое не длиннее 200000 символов";

    private ResultActions send(boolean create, String json) throws Exception {
        return mockMvc.perform((create ? post("/api/notes") : put("/api/notes/3"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertRejected(boolean create, String json, String field, String message) throws Exception {
        send(create, json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Некорректные данные запроса"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(message));
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("POST /api/notes без title — 400 с fieldErrors, сервис не вызывается")
    void create_missingTitle_returns400() throws Exception {
        assertRejected(true, "{\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("POST /api/notes с пустым title — 400")
    void create_emptyTitle_returns400() throws Exception {
        assertRejected(true, "{\"title\":\"\",\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("POST /api/notes с пробельным title — 400")
    void create_blankTitle_returns400() throws Exception {
        assertRejected(true, "{\"title\":\"   \",\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("POST /api/notes с title длиннее 255 — 400")
    void create_titleTooLong_returns400() throws Exception {
        assertRejected(true, "{\"title\":\"" + "a".repeat(256) + "\"}", "title", TITLE_TOO_LONG);
    }

    @Test
    @DisplayName("POST /api/notes с content длиннее 200 000 — 400")
    void create_contentTooLong_returns400() throws Exception {
        assertRejected(true, "{\"title\":\"t\",\"content\":\"" + "a".repeat(200_001) + "\"}",
                "content", CONTENT_TOO_LONG);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} без title — 400")
    void update_missingTitle_returns400() throws Exception {
        assertRejected(false, "{\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с пустым title — 400")
    void update_emptyTitle_returns400() throws Exception {
        assertRejected(false, "{\"title\":\"\",\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с пробельным title — 400")
    void update_blankTitle_returns400() throws Exception {
        assertRejected(false, "{\"title\":\" \\t \",\"content\":\"c\"}", "title", TITLE_REQUIRED);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с title длиннее 255 — 400")
    void update_titleTooLong_returns400() throws Exception {
        assertRejected(false, "{\"title\":\"" + "a".repeat(256) + "\"}", "title", TITLE_TOO_LONG);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с content длиннее 200 000 — 400")
    void update_contentTooLong_returns400() throws Exception {
        assertRejected(false, "{\"title\":\"t\",\"content\":\"" + "a".repeat(200_001) + "\"}",
                "content", CONTENT_TOO_LONG);
    }

    @Test
    @DisplayName("Ошибки двух полей сразу — оба в fieldErrors, по порядку имён полей")
    void create_bothFieldsInvalid_returnsSortedFieldErrors() throws Exception {
        send(true, "{\"title\":\"\",\"content\":\"" + "a".repeat(200_001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("title"));
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Граничные значения проходят: title 255, content 200 000 и content = null")
    void boundaryValues_pass() throws Exception {
        when(noteService.createNote(any(NoteRequest.class))).thenReturn(response(1L, "t", "c"));
        when(noteService.updateNote(eq(3L), any(NoteRequest.class))).thenReturn(response(3L, "t", "c"));

        send(true, "{\"title\":\"" + "a".repeat(255) + "\",\"content\":\"" + "a".repeat(200_000) + "\"}")
                .andExpect(status().isCreated());
        send(true, "{\"title\":\"t\",\"content\":null}").andExpect(status().isCreated());
        send(false, "{\"title\":\"t\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Сообщения об ошибках не содержат введённых значений")
    void validationErrors_doNotEchoInput() throws Exception {
        String secretTitle = "СЕКРЕТ".repeat(60);
        String body = send(true, "{\"title\":\"" + secretTitle + "\",\"content\":\"" + "x".repeat(200_001) + "\"}")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertTrue(!body.contains("СЕКРЕТ"), "значение title попало в ответ");
        assertTrue(!body.contains("xxxx"), "значение content попало в ответ");
        assertTrue(body.length() < 1000, "тело ответа неожиданно большое");
    }

    @Test
    @DisplayName("Формат без ошибок полей не изменился: в 404 нет ключа fieldErrors")
    void notFound_hasNoFieldErrorsKey() throws Exception {
        when(noteService.getNoteById(999L)).thenThrow(new NoteNotFoundException(999L));

        mockMvc.perform(get("/api/notes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("DataIntegrityViolationException из сервиса — 400 с общим текстом, без деталей исключения")
    void dataIntegrityViolation_returns400WithGenericBody() throws Exception {
        when(noteService.createNote(any(NoteRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key 'секретная деталь'"));

        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Данные нарушают ограничения хранилища"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("секретная деталь"))));
    }
}
