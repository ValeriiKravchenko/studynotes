package com.val.studynotes.exception;

import com.val.studynotes.dto.ErrorResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Штатные ошибки Spring MVC (400, 404, 405, 406, 415) обрабатывает {@link ResponseEntityExceptionHandler};
 * здесь они приводятся к формату {@link ErrorResponse}. Всё остальное — 500 без деталей исключения.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NoteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoteFound(NoteNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not found",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Произошла непредвиденная ошибка"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status != null ? status.getReasonPhrase() : "Error";
        ErrorResponse error = new ErrorResponse(statusCode.value(), reason, messageFor(ex, statusCode));
        // Content-Type задаём явно: иначе при 406 тело не удалось бы записать
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }

    /** Фиксированные сообщения: текст исключения и значения из запроса наружу не попадают. */
    private String messageFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MissingServletRequestParameterException e) {
            return "Отсутствует обязательный параметр '" + e.getParameterName() + "'";
        }
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            return "Некорректное значение параметра '" + e.getName() + "'";
        }
        if (ex instanceof TypeMismatchException) {
            return "Некорректное значение параметра";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Некорректное тело запроса";
        }
        if (ex instanceof NoResourceFoundException) {
            return "Ресурс не найден";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "Метод не поддерживается";
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return "Неприемлемый формат ответа";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "Неподдерживаемый тип содержимого";
        }
        return statusCode.value() == HttpStatus.NOT_FOUND.value() ? "Ресурс не найден" : "Некорректный запрос";
    }
}
