package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Заголовок markdown-документа")
public class HeadingInfo {
    private final int level;
    private final String text;
    private final String slug;

    public HeadingInfo(int level, String text, String slug) {
        this.level = level;
        this.text = text;
        this.slug = slug;
    }

    public int getLevel() {
        return level;
    }

    public String getText() {
        return text;
    }

    public String getSlug() {
        return slug;
    }
}
