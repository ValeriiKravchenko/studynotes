package com.val.studynotes.service;

import com.val.studynotes.dto.HeadingInfo;
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
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
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

    public List<HeadingInfo> extractHeadings(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        Node document = parser.parse(markdown);
        List<HeadingInfo> headings = new ArrayList<>();
        Node node = document.getFirstChild();
        while (node != null) {
            if (node instanceof Heading heading
                    && heading.getLevel() >= 2
                    && heading.getLevel() <= 3) {
                String text = heading.getText().toString();
                String slug = generateSlug(text);
                headings.add(new HeadingInfo(heading.getLevel(), text, slug));
            }
            node = node.getNext();
        }
        return headings;
    }

    private String generateSlug(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
