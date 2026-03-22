package com.val.studynotes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarkdownServiceTest {
    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    @Test
    @DisplayName("null → пустая строка")
    void nullInput_returnsEmptyString() {
        assertEquals("", markdownService.renderToHtml(null));
    }

    @Test
    @DisplayName("Пустая строка → пустая строка")
    void emptyInput_returnsEmptyString() {
        assertEquals("", markdownService.renderToHtml(""));
    }

    @Test
    @DisplayName("Пробелы → пустая строка")
    void blankInput_returnsEmptyString() {
        assertEquals("", markdownService.renderToHtml("   "));
    }

    @Test
    @DisplayName("Простой текст → параграф")
    void plainText_wrappedInParagraph() {
        String html = markdownService.renderToHtml("Простой текст");
        assertTrue(html.contains("<p>Простой текст</p>"));
    }

    @Test
    @DisplayName("H2 заголовок")
    void h2Header() {
        String html = markdownService.renderToHtml("## Раздел");
        assertTrue(html.contains("<h2>Раздел</h2>"));
    }

    @Test
    @DisplayName("H3 заголовок")
    void h3Header() {
        String html = markdownService.renderToHtml("### Подраздел");
        assertTrue(html.contains("<h3>Подраздел</h3>"));
    }

    @Test
    @DisplayName("Жирный текст")
    void boldText() {
        String html = markdownService.renderToHtml("**жирный**");
        assertTrue(html.contains("<strong>жирный</strong>"));
    }

    @Test
    @DisplayName("Курсив")
    void italicText() {
        String html = markdownService.renderToHtml("*курсив*");
        assertTrue(html.contains("<em>курсив</em>"));
    }

    @Test
    @DisplayName("Инлайн-код")
    void inlineCode() {
        String html = markdownService.renderToHtml("`System.out.println()`");
        assertTrue(html.contains("<code>System.out.println()</code>"));
    }

    @Test
    @DisplayName("Блок кода с языком")
    void codeBlock() {
        String markdown = "```java\npublic class Main {}\n```";
        String html = markdownService.renderToHtml(markdown);
        assertTrue(html.contains("<code class=\"language-java\">"));
        assertTrue(html.contains("public class Main {}"));
    }

    @Test
    @DisplayName("Маркированный список")
    void unorderedList() {
        String markdown = "- Первый\n- Второй\n- Третий";
        String html = markdownService.renderToHtml(markdown);

        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>Первый</li>"));
    }

    @Test
    @DisplayName("Ссылка")
    void link() {
        String html = markdownService.renderToHtml("[GitHub](https://github.com)");
        assertTrue(html.contains("<a href=\"https://github.com\">GitHub</a>"));
    }

    @Test
    @DisplayName("Зачёркнутый текст")
    void strikethrough() {
        String html = markdownService.renderToHtml("~~зачёркнутый~~");
        assertTrue(html.contains("<del>зачёркнутый</del>"));
    }

    @Test
    @DisplayName("Таблица")
    void table() {
        String markdown = "| Имя | Возраст |\n|-----|--------|\n| Иван | 25 |";
        String html = markdownService.renderToHtml(markdown);
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<td>Иван</td>"));
    }

    @Test
    @DisplayName("Чек-лист с незавершённой задачей")
    void taskListUnchecked() {
        String markdown = "- [ ] Незавершённая задача";
        String html = markdownService.renderToHtml(markdown);

        assertTrue(html.contains("<input type=\"checkbox\""));
        assertTrue(html.contains("Незавершённая задача"));
    }

    @Test
    @DisplayName("Чек-лист с завершённой задачей")
    void taskListChecked() {
        String markdown = "- [x] Завершённая задача";
        String html = markdownService.renderToHtml(markdown);

        assertTrue(html.contains("checked"));
        assertTrue(html.contains("Завершённая задача"));
    }

    @Test
    @DisplayName("Полная заметка с заголовком, текстом, кодом и таблицей")
    void fullNote() {
        String markdown = """
            ## Stream API
            
            Стримы позволяют обрабатывать коллекции.
            
            ```java
            list.stream().filter(x -> x > 0)
            ```
            
            | Метод | Описание |
            |-------|----------|
            | filter | Фильтрация |
            | map | Преобразование |
            """;
        String html = markdownService.renderToHtml(markdown);
        assertTrue(html.contains("<h2>Stream API</h2>"));
        assertTrue(html.contains("<p>Стримы позволяют обрабатывать коллекции.</p>"));
        assertTrue(html.contains("<code class=\"language-java\">"));
        assertTrue(html.contains("<table>"));
    }
}
