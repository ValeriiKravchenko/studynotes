package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingInfo;
import com.val.studynotes.dto.HeadingsResult;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownService {
    private final Parser parser;
    private final HtmlRenderer renderer;
    private static final Pattern CALLOUT_PATTERN = Pattern.compile(
            "<blockquote>\\s*<p>\\[!(\\w+)]([-+])?\\s*([^\\n]*)\\n?(.*?)</p>\\s*</blockquote>",
            Pattern.DOTALL
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile("<h([23])>(.*?)</h\\1>");

    private static final Pattern ALLOWED_LINK = Pattern.compile("^(#|https?:|mailto:).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALLOWED_IMAGE = Pattern.compile("^https?:.*", Pattern.CASE_INSENSITIVE);
    // без baseUri jsoup не оставляет относительные ссылки даже при preserveRelativeLinks(true); в вывод он не попадает
    private static final String BASE_URI = "http://base.invalid/";
    private static final Pattern URL_NOISE = Pattern.compile("[\\x00-\\x20]+");

    private static final Safelist SAFELIST = new Safelist()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "hr", "ul", "ol", "li",
                    "blockquote", "pre", "code", "em", "strong", "del", "a", "img",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "div", "details", "summary", "input")
            .addAttributes("code", "class")
            .addAttributes("div", "class")
            .addAttributes("details", "class", "open")
            .addAttributes("summary", "class")
            .addAttributes("li", "class")
            .addAttributes("input", "type", "class", "checked", "disabled")
            .addAttributes("h2", "id")
            .addAttributes("h3", "id")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("th", "align")
            .addAttributes("td", "align")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            // относительные ссылки пропускаем сюда, чтобы уцелели якоря #..., остальное снимает sanitize()
            .preserveRelativeLinks(true)
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    public MarkdownService() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                StrikethroughExtension.create(),
                TablesExtension.create(),
                TaskListExtension.create()
        ));
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    public String renderToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /**
     * Markdown → HTML, безопасный для вставки в страницу: callouts и id заголовков
     * расставляются до очистки, сама очистка идёт последней.
     */
    public HeadingsResult renderSafe(String markdown) {
        String html = renderToHtml(markdown);
        if (html.isBlank()) {
            return new HeadingsResult("", List.of());
        }
        HeadingsResult processed = processHeadings(processCallouts(html));
        Set<String> headingIds = processed.headings().stream()
                .map(HeadingInfo::getSlug)
                .collect(Collectors.toSet());
        return new HeadingsResult(sanitize(processed.html(), headingIds), processed.headings());
    }

    /**
     * headingIds: id, которые поставил processHeadings. Любой другой id у h2/h3
     * (из сырого HTML заметки) снимается, чтобы автор не мог перекрыть глобальные имена страницы.
     */
    String sanitize(String html, Set<String> headingIds) {
        Document dirty = Jsoup.parseBodyFragment(html, BASE_URI);
        dirty.select("input:not([type=checkbox])").remove();

        Document clean = new Cleaner(SAFELIST).clean(dirty);
        clean.outputSettings().prettyPrint(false);

        for (Element a : clean.select("a[href]")) {
            if (!ALLOWED_LINK.matcher(URL_NOISE.matcher(a.attr("href")).replaceAll("")).matches()) {
                a.removeAttr("href");
            }
        }
        for (Element img : clean.select("img[src]")) {
            if (!ALLOWED_IMAGE.matcher(URL_NOISE.matcher(img.attr("src")).replaceAll("")).matches()) {
                img.removeAttr("src");
            }
        }
        clean.select("img:not([src])").remove();
        clean.select("a:not([href])").forEach(Element::unwrap);
        for (Element h : clean.select("h2[id], h3[id]")) {
            if (!headingIds.contains(h.id())) {
                h.removeAttr("id");
            }
        }
        return clean.body().html();
    }

    public HeadingsResult processHeadings(String html) {
        if (html == null || html.isBlank()) {
            return new HeadingsResult(html, List.of());
        }

        List<HeadingInfo> headings = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String text = matcher.group(2).replaceAll("<[^>]+>", "");
            String slug = text.toLowerCase()
                    .replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s-]", "")
                    .replaceAll("\\s+", "-");
            headings.add(new HeadingInfo(level, text, slug));
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(
                            "<h" + level + " id=\"" + slug + "\">" + matcher.group(2) + "</h" + level + ">"));
        }
        matcher.appendTail(result);

        return new HeadingsResult(result.toString(), headings);
    }

    private static final Map<String, String[]> CALLOUT_TYPES = Map.of(
            "note",    new String[]{"📝", "callout-note"},
            "tip",     new String[]{"💡", "callout-tip"},
            "warning", new String[]{"⚠️", "callout-warning"},
            "danger",  new String[]{"🔴", "callout-danger"},
            "info",    new String[]{"ℹ️", "callout-info"}
    );

    public String processCallouts(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Matcher matcher = CALLOUT_PATTERN.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String type = matcher.group(1).toLowerCase();
            String foldSign = matcher.group(2);
            String title = matcher.group(3).trim();
            String content = matcher.group(4).trim();

            String[] config = CALLOUT_TYPES.getOrDefault(type,
                    new String[]{"📌", "callout-note"});
            String icon = config[0];
            String cssClass = config[1];

            if (title.isEmpty()) {
                title = type.substring(0, 1).toUpperCase() + type.substring(1);
            }

            String replacement;

            if (foldSign != null) {
                String openAttr = "+".equals(foldSign) ? " open" : "";
                replacement = "<details class=\"callout " + cssClass + "\"" + openAttr + ">"
                        + "<summary class=\"callout-title\">" + icon + " " + title + "</summary>"
                        + "<div class=\"callout-content\">" + content + "</div>"
                        + "</details>";
            } else {
                replacement = "<div class=\"callout " + cssClass + "\">"
                        + "<div class=\"callout-title\">" + icon + " " + title + "</div>"
                        + "<div class=\"callout-content\">" + content + "</div>"
                        + "</div>";
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
