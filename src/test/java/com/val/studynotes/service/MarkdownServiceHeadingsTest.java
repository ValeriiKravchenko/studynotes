package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MarkdownServiceHeadingsTest {
    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    @Test
    @DisplayName("H2 заголовок извлекается и получает id")
    void h2_extractedWithId() {
        String html = markdownService.renderToHtml("## Stream API\n\nТекст.");
        HeadingsResult result = markdownService.processHeadings(html);

        assertEquals(1, result.headings().size());
        assertEquals(2, result.headings().get(0).getLevel());
        assertEquals("Stream API", result.headings().get(0).getText());
        assertTrue(result.html().contains("id=\"stream-api\""));
    }

    @Test
    @DisplayName("null → пустой список")
    void nullInput_returnsEmptyList() {
        HeadingsResult result = markdownService.processHeadings(null);

        assertTrue(result.headings().isEmpty());
    }

    @Test
    @DisplayName("H1 и H4 игнорируются")
    void h1AndH4_ignored() {
        String html = markdownService.renderToHtml("# H1\n\n#### H4\n\nТекст.");
        HeadingsResult result = markdownService.processHeadings(html);

        assertTrue(result.headings().isEmpty());
    }

    @Test
    @DisplayName("H2 + H3 — оба с правильными уровнями")
    void mixedH2H3() {
        String html = markdownService.renderToHtml("## Раздел\n\n### Подраздел");
        HeadingsResult result = markdownService.processHeadings(html);

        assertEquals(2, result.headings().size());
        assertEquals(2, result.headings().get(0).getLevel());
        assertEquals(3, result.headings().get(1).getLevel());
    }

    @Test
    @DisplayName("id добавлен в HTML")
    void idAddedToHtml() {
        String html = markdownService.renderToHtml("## Мой раздел\n\nТекст.");
        HeadingsResult result = markdownService.processHeadings(html);

        assertTrue(result.html().contains("<h2 id=\"мой-раздел\">"));
    }

    @Test
    @DisplayName("Заголовок с $1 не подменяется ссылкой на группу")
    void dollarWithDigit_keptLiterally() {
        HeadingsResult result = markdownService.processHeadings("<h2>$1 цена</h2>");

        assertEquals("<h2 id=\"1-цена\">$1 цена</h2>", result.html());
        assertEquals("$1 цена", result.headings().get(0).getText());
    }

    @Test
    @DisplayName("Заголовок с $ без цифры не бросает исключение")
    void dollarWithoutDigit_noException() {
        HeadingsResult result = assertDoesNotThrow(
                () -> markdownService.processHeadings("<h2>Цена в $</h2><h3>$abc</h3>"));

        assertEquals("<h2 id=\"цена-в-\">Цена в $</h2><h3 id=\"abc\">$abc</h3>", result.html());
        assertEquals(2, result.headings().size());
    }

    @Test
    @DisplayName("Заголовок с \\ в конце не бросает исключение")
    void trailingBackslash_noException() {
        HeadingsResult result = assertDoesNotThrow(
                () -> markdownService.processHeadings("<h2>Путь C:\\</h2>"));

        assertEquals("<h2 id=\"путь-c\">Путь C:\\</h2>", result.html());
    }

    @Test
    @DisplayName("Markdown-заголовки с $ и \\ проходят весь путь без искажений")
    void specialCharsFromMarkdown() {
        String html = markdownService.renderToHtml("## $1 цена\n\n### Цена в $\n\n## Каталог C:\\\\");
        HeadingsResult result = assertDoesNotThrow(() -> markdownService.processHeadings(html));

        assertEquals(3, result.headings().size());
        assertTrue(result.html().contains(">$1 цена</h2>"));
        assertTrue(result.html().contains(">Цена в $</h3>"));
        assertTrue(result.html().contains(">Каталог C:\\</h2>"));
    }
}
