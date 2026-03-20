package com.val.studynotes.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImportResultTest {
    private ImportResult result;

    @BeforeEach
    void setUp() {
        result = new ImportResult();
    }

    @Test
    @DisplayName("Новый результат имеет нулевые счётчики")
    void newResultHasZeroCounts() {
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getImported());
        assertEquals(0, result.getSkipped());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("incrementTotal увеличивает счётчик total")
    void incrementTotal_increasesCount() {
        result.incrementTotal();
        result.incrementTotal();
        result.incrementTotal();
        assertEquals(3, result.getTotal());
    }

    @Test
    @DisplayName("incrementImported увеличивает счётчик imported")
    void incrementImported_increasesCount() {
        result.incrementImported();
        assertEquals(1, result.getImported());
    }

    @Test
    @DisplayName("incrementSkipped увеличивает счётчик skipped")
    void incrementSkipped_increasesCount() {
        result.incrementSkipped();
        result.incrementSkipped();
        assertEquals(2, result.getSkipped());
    }

    @Test
    @DisplayName("addError добавляет ошибку в список")
    void addError_addsToErrorList() {
        result.addError("Файл повреждён");
        result.addError("Нет доступа");

        assertEquals(2, result.getErrors().size());
        assertEquals("Файл повреждён", result.getErrors().get(0));
        assertEquals("Нет доступа", result.getErrors().get(1));
    }

    @Test
    @DisplayName("hasErrors возвращает false для нового результата")
    void hasErrors_returnsFalse_whenNoErrors() {
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("hasErrors возвращает true после добавления ошибки")
    void hasErrors_returnsTrue_afterAddingError() {
        result.addError("Ошибка");

        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("Счётчики работают независимо друг от друга")
    void counters_areIndependent() {
        result.incrementTotal();
        result.incrementTotal();
        result.incrementTotal();
        result.incrementImported();
        result.incrementSkipped();
        result.addError("Ошибка");

        assertEquals(3, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getErrors().size());
    }
}