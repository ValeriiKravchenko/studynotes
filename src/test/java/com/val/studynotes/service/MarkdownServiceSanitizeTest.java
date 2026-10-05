package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingsResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class MarkdownServiceSanitizeTest {
    private MarkdownService markdownService;

    @BeforeEach
    void setUp() {
        markdownService = new MarkdownService();
    }

    private String render(String markdown) {
        return markdownService.renderSafe(markdown).html();
    }

    private static Document parse(String html) {
        return Jsoup.parseBodyFragment(html);
    }

    private static List<String> hrefs(String html) {
        return parse(html).select("a[href]").stream().map(a -> a.attr("href")).collect(Collectors.toList());
    }

    /** Общая проверка: в результате нет ничего исполняемого. */
    private static void assertSafe(String html) {
        Document doc = parse(html);
        assertTrue(doc.select("script, style, iframe, svg, object, embed, form, link, meta, base").isEmpty(),
                "опасный тег в: " + html);
        for (Element e : doc.getAllElements()) {
            e.attributes().forEach(attr -> {
                assertFalse(attr.getKey().toLowerCase().startsWith("on"), "обработчик события в: " + html);
                assertNotEquals("style", attr.getKey().toLowerCase(), "style в: " + html);
            });
        }
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href").replaceAll("[\\x00-\\x20]+", "").toLowerCase();
            assertTrue(href.startsWith("#") || href.startsWith("http:") || href.startsWith("https:")
                    || href.startsWith("mailto:"), "недопустимый href в: " + html);
        }
        for (Element img : doc.select("img")) {
            assertTrue(img.attr("src").toLowerCase().matches("^https?:.*"), "недопустимый src в: " + html);
        }
        for (Element input : doc.select("input")) {
            assertEquals("checkbox", input.attr("type").toLowerCase(), "input не чекбокс в: " + html);
        }
    }

    // ---------- XSS-нагрузки ----------

    @Test
    @DisplayName("<script> удаляется")
    void script() {
        String html = render("Текст\n\n<script>alert(1)</script>\n\nещё");
        assertSafe(html);
        assertFalse(html.toLowerCase().contains("<script"));
        assertTrue(html.contains("Текст"));
    }

    @Test
    @DisplayName("onerror у img удаляется")
    void onerror() {
        String html = render("<img src=x onerror=alert(1)>");
        assertSafe(html);
        assertFalse(html.contains("onerror"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[x](javascript:alert(1))",
            "[x](JAVASCRIPT:alert(1))",
            "[x](JaVaScRiPt:alert(1))",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<a href=\"JaVaScRiPt:alert(1)\">x</a>",
            "<a href=\"jav&#x09;ascript:alert(1)\">x</a>",
            "<a href=\" javascript:alert(1)\">x</a>",
            "<a href=\"&#106;avascript:alert(1)\">x</a>",
            "[x](vbscript:msgbox(1))"
    })
    @DisplayName("javascript: и подобные схемы удаляются в любом написании")
    void javascriptLinks(String markdown) {
        String html = render(markdown);
        assertSafe(html);
        assertTrue(hrefs(html).isEmpty(), html);
        assertFalse(html.toLowerCase().contains("javascript"), html);
    }

    @Test
    @DisplayName("data: в ссылке и в картинке удаляется")
    void dataUri() {
        String html = render("[x](data:text/html,<script>alert(1)</script>)\n\n![i](data:image/svg+xml;base64,PHN2Zz4=)");
        assertSafe(html);
        assertFalse(html.contains("data:"));
        assertTrue(parse(html).select("img").isEmpty());
    }

    @Test
    @DisplayName("svg, iframe, style удаляются")
    void svgIframeStyle() {
        String html = render("<svg onload=alert(1)></svg>\n\n<iframe src=\"https://evil.example\"></iframe>\n\n<style>*{display:none}</style>");
        assertSafe(html);
        assertFalse(html.contains("evil.example"));
        assertFalse(html.contains("display:none"));
    }

    @Test
    @DisplayName("ontoggle у details удаляется, open остаётся")
    void ontoggle() {
        String html = render("<details open ontoggle=alert(1)><summary>s</summary>x</details>");
        assertSafe(html);
        assertEquals(1, parse(html).select("details[open]").size());
    }

    @Test
    @DisplayName("onclick у ссылки удаляется")
    void onclick() {
        String html = render("<a href=\"https://example.com\" onclick=\"alert(1)\">l</a>");
        assertSafe(html);
        assertFalse(html.contains("onclick"));
        assertEquals(List.of("https://example.com"), hrefs(html));
    }

    @Test
    @DisplayName("input type=text удаляется, чекбокс остаётся")
    void inputText() {
        String html = render("<input type=\"text\" value=\"x\">\n\n<input type=\"password\">\n\n- [ ] задача");
        assertSafe(html);
        assertEquals(1, parse(html).select("input").size());
        assertEquals(1, parse(html).select("input[type=checkbox]").size());
    }

    @Test
    @DisplayName("onclick у h2 удаляется, чужой id тоже: id ставит только processHeadings")
    void headingAttributes() {
        String html = render("<h2 id=\"location\" onclick=\"alert(1)\">h</h2>\n\n## Настоящий");
        assertSafe(html);
        assertEquals(0, parse(html).select("[id=location]").size());
        assertEquals(1, parse(html).select("h2#настоящий").size());
    }

    @Test
    @DisplayName("id у не-заголовков удаляется")
    void idOnOtherTags() {
        String html = render("<div id=\"x\" class=\"a\">d</div>\n\n<p id=\"y\">p</p>");
        assertTrue(parse(html).select("[id]").isEmpty(), html);
    }

    @Test
    @DisplayName("callout: <img onerror> в заголовке")
    void calloutImgTitle() {
        String html = render("> [!note] <img src=x onerror=alert(1)>\n> тело");
        assertSafe(html);
        assertFalse(html.contains("onerror"));
        assertEquals(1, parse(html).select("div.callout.callout-note").size());
        assertTrue(html.contains("тело"));
    }

    @Test
    @DisplayName("складной callout: <script> в заголовке")
    void calloutScriptTitle() {
        String html = render("> [!tip]- <script>alert(1)</script>\n> тело");
        assertSafe(html);
        assertFalse(html.toLowerCase().contains("script"));
        assertEquals(1, parse(html).select("details.callout.callout-tip").size());
    }

    @Test
    @DisplayName("callout: попытка выйти из разметки и вставить обработчик")
    void calloutBreakout() {
        String html = render("> [!warning] </div><div onmouseover=alert(1)>x\n> тело");
        assertSafe(html);
        assertFalse(html.contains("onmouseover"));
    }

    // ---------- правила для ссылок ----------

    @Test
    @DisplayName("Якорь #x остаётся")
    void anchorKept() {
        assertEquals(List.of("#x"), hrefs(render("[к разделу](#x)")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/path",
            "../up",
            "relative/page",
            "//evil.com",
            "\\\\evil.com",
            "/\\evil.com",
            "\\/evil.com"
    })
    @DisplayName("Относительные пути, //host, \\\\host и /\\host удаляются")
    void relativeLinksRemoved(String href) {
        String html = render("<a href=\"" + href + "\">текст ссылки</a>");
        assertSafe(html);
        assertTrue(hrefs(html).isEmpty(), "href остался: " + html);
        assertTrue(html.contains("текст ссылки"), "текст должен сохраниться: " + html);
    }

    @Test
    @DisplayName("Относительные пути из markdown-ссылок удаляются")
    void relativeMarkdownLinksRemoved() {
        String html = render("[a](/path) [b](//evil.com) [c](../up)");
        assertTrue(hrefs(html).isEmpty(), html);
    }

    @Test
    @DisplayName("https и mailto остаются с rel")
    void httpsAndMailtoKeepRel() {
        String html = render("[a](https://example.com) [b](http://example.com) [c](mailto:me@example.com)");
        Document doc = parse(html);
        assertEquals(3, doc.select("a[href]").size());
        for (Element a : doc.select("a[href]")) {
            assertEquals("noopener noreferrer", a.attr("rel"), a.outerHtml());
        }
        assertEquals(1, doc.select("a[href^=mailto:]").size());
    }

    @Test
    @DisplayName("Картинки: https остаётся, относительные и //host удаляются")
    void images() {
        String html = render("![a](https://example.com/a.png)\n\n![b](/local.png)\n\n![c](//evil.com/c.png)");
        Document doc = parse(html);
        assertEquals(1, doc.select("img").size());
        assertEquals("https://example.com/a.png", doc.select("img").attr("src"));
    }

    // ---------- сохранение нужного ----------

    @Test
    @DisplayName("Таблица сохраняется")
    void tablePreserved() {
        Document doc = parse(render("| Имя | Возраст |\n|-----|--------|\n| Иван | 25 |"));
        assertEquals(1, doc.select("table").size());
        assertEquals(2, doc.select("th").size());
        assertEquals("Иван", doc.select("td").first().text());
    }

    @Test
    @DisplayName("Блок кода сохраняет language-java и экранирование")
    void codeBlockPreserved() {
        String html = render("```java\nif (a < b && c > d) {}\n```");
        assertEquals(1, parse(html).select("pre > code.language-java").size());
        assertTrue(html.contains("a &lt; b &amp;&amp; c &gt; d"), html);
    }

    @Test
    @DisplayName("Чекбоксы задач: checked и без")
    void taskListPreserved() {
        Document doc = parse(render("- [x] сделано\n- [ ] осталось"));
        assertEquals(2, doc.select("input[type=checkbox]").size());
        assertEquals(1, doc.select("input[type=checkbox][checked]").size());
        assertEquals(2, doc.select("input[type=checkbox][disabled]").size());
        assertEquals(2, doc.select("li.task-list-item").size());
    }

    @Test
    @DisplayName("Callouts: обычный и складной")
    void calloutsPreserved() {
        Document doc = parse(render("> [!note] Заголовок\n> текст\n\n> [!tip]+ Совет\n> раскрыт"));
        assertEquals(1, doc.select("div.callout.callout-note > div.callout-title").size());
        assertEquals(1, doc.select("details.callout.callout-tip[open] > summary.callout-title").size());
        assertEquals(2, doc.select("div.callout-content").size());
    }

    @Test
    @DisplayName("id заголовков и список headings")
    void headingIdsPreserved() {
        HeadingsResult result = markdownService.renderSafe("## Stream API\n\n### Подраздел\n\n#### H4");
        Document doc = parse(result.html());
        assertEquals(1, doc.select("h2#stream-api").size());
        assertEquals(1, doc.select("h3#подраздел").size());
        assertEquals(2, result.headings().size());
        assertEquals("stream-api", result.headings().get(0).getSlug());
    }

    @Test
    @DisplayName("Заголовок с $ и \\ проходит через renderSafe")
    void specialCharsInHeading() {
        HeadingsResult result = markdownService.renderSafe("## $1 цена\n\n### Цена в $");
        assertEquals(2, result.headings().size());
        assertTrue(result.html().contains("$1 цена"));
    }

    @Test
    @DisplayName("Заголовок с разметкой внутри не ломает id")
    void headingWithInlineHtml() {
        HeadingsResult result = markdownService.renderSafe("## Заголовок <img src=x onerror=alert(1)>");
        assertSafe(result.html());
        assertEquals(1, parse(result.html()).select("h2[id]").size());
    }

    // ---------- служебный baseUri не должен просачиваться в вывод ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "[к разделу](#x)",
            "[a](https://example.com)",
            "[b](http://example.com)",
            "[c](mailto:me@example.com)",
            "![a](https://example.com/a.png)",
            "![b](/local.png)",
            "![c](//evil.com/c.png)",
            "![d](data:image/svg+xml;base64,PHN2Zz4=)",
            "[a](/path) [b](../up) [c](relative/page)",
            "<a href=\"//evil.com\">x</a>",
            "<a href=\"\\\\evil.com\">x</a>",
            "<a href=\"/\\evil.com\">x</a>",
            "<a href=\"\">пустая</a> <a>без href</a>",
            "[x](javascript:alert(1)) <a href=\"JaVaScRiPt:alert(1)\">y</a>",
            "[x](data:text/html,<script>alert(1)</script>)",
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<svg onload=alert(1)></svg><iframe src=\"https://evil.example\"></iframe><style>*{}</style>",
            "<details open ontoggle=alert(1)><summary>s</summary>x</details>",
            "<a href=\"https://example.com\" onclick=\"alert(1)\">l</a>",
            "<input type=\"text\"> <input type=\"password\">",
            "<h2 id=\"location\" onclick=\"alert(1)\">h</h2>",
            "> [!note] <img src=x onerror=alert(1)>\n> тело",
            "> [!tip]- <script>alert(1)</script>\n> тело",
            "> [!warning] </div><div onmouseover=alert(1)>x\n> тело",
            "- [x] сделано\n- [ ] осталось",
            "| a | b |\n|---|---|\n| [l](#x) | ![i](/p.png) |",
            "```java\nString s = \"x\";\n```",
            "## Раздел [ссылка](#x)\n\n### Подраздел"
    })
    @DisplayName("Служебный baseUri не попадает в вывод renderSafe")
    void baseUriNeverLeaks(String markdown) {
        String html = render(markdown);
        assertFalse(html.contains("base.invalid"), html);
        assertFalse(html.contains("http://base"), html);
    }

    // ---------- границы и идемпотентность ----------

    @Test
    @DisplayName("null и пустая строка → пустой результат")
    void emptyInput() {
        for (String in : new String[]{null, "", "   "}) {
            HeadingsResult result = markdownService.renderSafe(in);
            assertEquals("", result.html());
            assertTrue(result.headings().isEmpty());
        }
    }

    @Test
    @DisplayName("Очистка очищенного HTML ничего не меняет")
    void idempotent() {
        String markdown = """
                ## Раздел

                ### Подраздел

                > [!note] Заметка <img src=x onerror=alert(1)>
                > тело

                > [!tip]- Совет
                > тело

                - [x] готово
                - [ ] нет

                | a | b |
                |---|---|
                | 1 | 2 |

                ```java
                if (a < b) {}
                ```

                [a](https://example.com) [b](#x) [c](/path) <a href="javascript:alert(1)">d</a>

                <script>alert(1)</script>
                """;
        HeadingsResult first = markdownService.renderSafe(markdown);
        java.util.Set<String> ids = first.headings().stream()
                .map(h -> h.getSlug()).collect(Collectors.toSet());
        String second = markdownService.sanitize(first.html(), ids);
        assertEquals(first.html(), second);
    }

    @Test
    @DisplayName("Обычная заметка без опасного содержимого не искажается")
    void plainNoteUnchanged() {
        String markdown = "## Раздел\n\nТекст с **жирным** и *курсивом*.\n\n- один\n- два\n";
        HeadingsResult safe = markdownService.renderSafe(markdown);
        String expected = markdownService.processHeadings(
                markdownService.processCallouts(markdownService.renderToHtml(markdown))).html();
        assertEquals(expected, safe.html());
    }
}
