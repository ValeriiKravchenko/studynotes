package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MarkdownServiceHeadingsTest {
    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    @Test
    @DisplayName("null → пустой список заголовков")
    void nullInput_returnsEmptyList() {
        List<HeadingInfo> headings = markdownService.extractHeadings(null);

        assertTrue(headings.isEmpty());
    }

    @Test
    @DisplayName("Пустая строка → пустой список")
    void emptyInput_returnsEmptyList() {
        assertTrue(markdownService.extractHeadings("").isEmpty());
    }

    @Test
    @DisplayName("Текст без заголовков → пустой список")
    void noHeadings_returnsEmptyList() {
        assertTrue(markdownService.extractHeadings("Просто текст.").isEmpty());
    }

    @Test
    @DisplayName("Один H2 → список из одного элемента")
    void singleH2_returnsOneHeading() {
        List<HeadingInfo> headings = markdownService.extractHeadings("## Stream API\n\nТекст.");

        assertEquals(1, headings.size());
        assertEquals(2, headings.get(0).getLevel());
        assertEquals("Stream API", headings.get(0).getText());
        assertFalse(headings.get(0).getSlug().isEmpty());
    }

    @Test
    @DisplayName("Один H3 → level = 3")
    void singleH3_returnsLevel3() {
        List<HeadingInfo> headings = markdownService.extractHeadings("### Подраздел\n\nТекст.");

        assertEquals(1, headings.size());
        assertEquals(3, headings.get(0).getLevel());
    }

    @Test
    @DisplayName("H1 игнорируется")
    void h1IsIgnored() {
        assertTrue(markdownService.extractHeadings("# Заголовок\n\nТекст.").isEmpty());
    }

    @Test
    @DisplayName("H4 игнорируется")
    void h4IsIgnored() {
        assertTrue(markdownService.extractHeadings("#### Мелкий\n\nТекст.").isEmpty());
    }

    @Test
    @DisplayName("Несколько H2 — в порядке появления")
    void multipleH2_inOrder() {
        String markdown = "## Первый\n\nТекст.\n\n## Второй\n\nТекст.\n\n## Третий";

        List<HeadingInfo> headings = markdownService.extractHeadings(markdown);

        assertEquals(3, headings.size());
        assertEquals("Первый", headings.get(0).getText());
        assertEquals("Второй", headings.get(1).getText());
        assertEquals("Третий", headings.get(2).getText());
    }

    @Test
    @DisplayName("H2 + H3 — оба с правильными уровнями")
    void mixedH2H3() {
        String markdown = "## Раздел\n\n### Подраздел 1\n\n### Подраздел 2";

        List<HeadingInfo> headings = markdownService.extractHeadings(markdown);

        assertEquals(3, headings.size());
        assertEquals(2, headings.get(0).getLevel());
        assertEquals(3, headings.get(1).getLevel());
        assertEquals(3, headings.get(2).getLevel());
    }

    @Test
    @DisplayName("Slug: пробелы → дефисы, нижний регистр")
    void slug_format() {
        List<HeadingInfo> headings = markdownService.extractHeadings("## Stream API Basics");

        assertEquals("stream-api-basics", headings.get(0).getSlug());
    }

    @Test
    @DisplayName("Slug: русский текст")
    void slug_russian() {
        List<HeadingInfo> headings = markdownService.extractHeadings("## Создание стримов");

        assertEquals("создание-стримов", headings.get(0).getSlug());
    }
}
