package com.val.studynotes.exception;

/**
 * Поле запроса ссылается на несуществующую запись (например, folderId).
 * Отдаётся клиенту как 400 с fieldErrors на это поле. Сообщение фиксированное, без значений из запроса.
 */
public class InvalidReferenceException extends RuntimeException {
    private final String field;

    public InvalidReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
