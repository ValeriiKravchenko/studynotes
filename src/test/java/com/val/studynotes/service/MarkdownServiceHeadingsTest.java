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
}
