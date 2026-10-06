package com.val.studynotes.exception;

import com.val.studynotes.dto.ErrorResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

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

    /** Нарушение ограничений хранилища, прошедшее мимо валидации: 400 с общим текстом, детали не раскрываются. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Данные нарушают ограничения хранилища"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /** Архив для импорта отклонён (не zip, пустой, превышены лимиты): 400 с понятным сообщением. */
    @ExceptionHandler(ImportRejectedException.class)
    public ResponseEntity<ErrorResponse> handleImportRejected(ImportRejectedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
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

    /** В fieldErrors попадают только имя поля и текст ограничения; отклонённое значение не читается. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(),
                        e.getDefaultMessage() != null ? e.getDefaultMessage() : "Некорректное значение"))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field)
                        .thenComparing(ErrorResponse.FieldError::message))
                .toList();
        ErrorResponse body = new ErrorResponse(status.value(), reasonPhrase(status),
                "Некорректные данные запроса", fieldErrors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        ErrorResponse body = new ErrorResponse(status.value(), reasonPhrase(status),
                "Некорректное значение параметра запроса");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        // Готовое тело (ошибки валидации) отдаётся как есть, остальные исключения собираются из ex
        ErrorResponse error = body instanceof ErrorResponse prebuilt
                ? prebuilt
                : new ErrorResponse(statusCode.value(), reasonPhrase(statusCode), messageFor(ex, statusCode));
        // Content-Type задаём явно: иначе при 406 тело не удалось бы записать
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : "Error";
    }

    /** Фиксированные сообщения: текст исключения и значения из запроса наружу не попадают. */
    private String messageFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MissingServletRequestParameterException e) {
            return "Отсутствует обязательный параметр '" + e.getParameterName() + "'";
        }
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            return "Некорректное значение параметра '" + e.getName() + "'";
        }
        if (ex instanceof MissingServletRequestPartException e) {
            return "Отсутствует обязательная часть запроса '" + e.getRequestPartName() + "'";
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return "Размер загружаемого файла превышает допустимый";
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
