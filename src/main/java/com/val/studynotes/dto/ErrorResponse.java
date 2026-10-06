package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Единый формат ошибки для /api/**")
public class ErrorResponse {
    @Schema(description = "HTTP-статус", example = "404")
    private int status;
    @Schema(description = "Краткое название статуса", example = "Not found")
    private String error;
    @Schema(description = "Сообщение для пользователя; детали исключений и введённые значения не раскрываются")
    private String message;
    @Schema(description = "Время ошибки на сервере")
    private LocalDateTime timestamp;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Ошибки полей запроса; есть только у ошибок валидации")
    private List<FieldError> fieldErrors;

    /** Ошибка одного поля запроса: имя поля и фиксированный текст, без введённого значения. */
    @Schema(description = "Ошибка одного поля запроса")
    public record FieldError(String field, String message) {
    }

    public ErrorResponse(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String error, String message, List<FieldError> fieldErrors) {
        this(status, error, message);
        this.fieldErrors = fieldErrors;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}
