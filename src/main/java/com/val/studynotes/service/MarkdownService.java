package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingInfo;
import com.val.studynotes.dto.HeadingsResult;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownService {
    private final Parser parser;
    private final HtmlRenderer renderer;

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

    public HeadingsResult processHeadings(String html) {
        if (html == null || html.isBlank()) {
            return new HeadingsResult(html, List.of());
        }

        List<HeadingInfo> headings = new ArrayList<>();
        Pattern pattern = Pattern.compile("<h([23])>(.*?)</h\\1>");
        Matcher matcher = pattern.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String text = matcher.group(2).replaceAll("<[^>]+>", "");
            String slug = text.toLowerCase()
                    .replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s-]", "")
                    .replaceAll("\\s+", "-");
            headings.add(new HeadingInfo(level, text, slug));
            matcher.appendReplacement(result,
                    "<h" + level + " id=\"" + slug + "\">" + matcher.group(2) + "</h" + level + ">");
        }
        matcher.appendTail(result);

        return new HeadingsResult(result.toString(), headings);
    }
}
