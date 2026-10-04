package com.val.studynotes.exception;

import com.val.studynotes.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/** Прямые юнит-тесты обработчика; интеграция через MVC покрыта в NoteControllerTest. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NoteNotFoundException → 404, сообщение исключения в теле")
    void handleNoteFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleNoteFound(new NoteNotFoundException(42L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.getStatus());
        assertEquals("Not found", body.getError());
        assertEquals("Note not found with id: 42", body.getMessage());
        assertNotNull(body.getTimestamp());
    }

    @Test
    @DisplayName("Любое другое исключение → 500, сообщение исключения наружу не попадает")
    void handleGeneral_returns500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(new RuntimeException("внутренняя деталь"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        assertEquals("Произошла непредвиденная ошибка", body.getMessage());
        assertFalse(body.getMessage().contains("внутренняя деталь"));
    }
}
