package com.val.studynotes.exception;

/**
 * Архив отклонён целиком: не zip, пустой, повреждён или превысил лимиты.
 * Сообщение безопасно для показа клиенту (не содержит данных из архива).
 */
public class ImportRejectedException extends RuntimeException {

    public ImportRejectedException(String message) {
        super(message);
    }
}
