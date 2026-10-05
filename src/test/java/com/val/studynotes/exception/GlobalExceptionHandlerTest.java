package com.val.studynotes.exception;

import com.val.studynotes.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

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

    @Test
    @DisplayName("handleMethodArgumentNotValid: 400, fieldErrors отсортированы, отклонённое значение не попадает в тело")
    void handleMethodArgumentNotValid_returnsSortedFieldErrorsWithoutRejectedValue() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "noteRequest");
        binding.addError(new FieldError("noteRequest", "title", "СЕКРЕТ", false, null, null, "Заголовок обязателен"));
        binding.addError(new FieldError("noteRequest", "content", "СЕКРЕТ", false, null, null, null));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", Object.class), 0);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, binding),
                new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Bad Request", body.getError());
        assertEquals("Некорректные данные запроса", body.getMessage());
        assertEquals(2, body.getFieldErrors().size());
        assertEquals("content", body.getFieldErrors().get(0).field());
        assertEquals("Некорректное значение", body.getFieldErrors().get(0).message());
        assertEquals("title", body.getFieldErrors().get(1).field());
        assertEquals("Заголовок обязателен", body.getFieldErrors().get(1).message());
        assertFalse(body.getFieldErrors().toString().contains("СЕКРЕТ"));
    }

    @Test
    @DisplayName("handleHandlerMethodValidationException: 400 с общим текстом и без fieldErrors")
    void handleHandlerMethodValidation_returns400() {
        ResponseEntity<Object> response = handler.handleHandlerMethodValidationException(
                org.mockito.Mockito.mock(HandlerMethodValidationException.class),
                new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Некорректное значение параметра запроса", body.getMessage());
        assertNull(body.getFieldErrors());
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 400, текст исключения наружу не попадает")
    void handleDataIntegrity_returns400WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("внутренняя деталь"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.getStatus());
        assertEquals("Bad Request", body.getError());
        assertEquals("Данные нарушают ограничения хранилища", body.getMessage());
        assertNull(body.getFieldErrors());
        assertFalse(body.getMessage().contains("внутренняя деталь"));
    }

    @SuppressWarnings("unused")
    private void sample(Object arg) {
    }
}
