package com.val.studynotes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TitleExtractorTest {
    private TitleExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TitleExtractor();
    }

    @Test
    @DisplayName("Извлекает заголовок H1 из начала текста")
    void extractsH1FromBeginning() {
        String content = "# Stream API\n\nТекст заметки о стримах.";
        String title = extractor.extract(content, "fallback-name");
        assertEquals("Stream API", title);
    }

    @Test
    @DisplayName("Убирает лишние пробелы из заголовка")
    void trimWhitespaceFromTitle() {
        String content = "#   Stream API   \n\nТекст";
        String title = extractor.extract(content, "fallback");
        assertEquals("Stream API", title);
    }

    @Test
    @DisplayName("Возвращает fallback, если заголовок H1 отсутствует")
    void returnsFallbackWhenNoH1() {
        String content = "Просто текст без заголовка.\n\nЕщё абзац.";
        String title = extractor.extract(content, "my-note");
        assertEquals("my-note", title);
    }

    @Test
    @DisplayName("H2 не считается заголовком заметки")
    void h2IsNotTreatedAsTitle() {
        String content = "## Это раздел, не заголовок\n\nТекст.";
        String title = extractor.extract(content, "fallback-name");
        assertEquals("fallback-name", title);
    }

    @Test
    @DisplayName("Пустая строка — возвращает fallback")
    void emptyContent_returnsFallback() {
        String title = extractor.extract("", "fallback-name");
        assertEquals("fallback-name", title);
    }

    @Test
    @DisplayName("null — возвращает fallback")
    void nullContent_returnsFallback() {
        String title = extractor.extract(null, "fallback-name");
        assertEquals("fallback-name", title);
    }

    @Test
    @DisplayName("Находит H1, даже если он не в первой строке")
    void findsH1NotOnFirstLine() {
        String content = "\n\nКакой-то текст\n\n# Настоящий заголовок\n\nТело";
        String title = extractor.extract(content, "fallback");
        assertEquals("Настоящий заголовок", title);
    }

    @Test
    @DisplayName("Если несколько H1 — берёт первый")
    void multipleH1_takesFirst() {
        String content = "# Первый\n\n# Второй\n\n# Третий";
        String title = extractor.extract(content, "fallback");
        assertEquals("Первый", title);
    }
}
