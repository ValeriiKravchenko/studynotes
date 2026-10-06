package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Markdown для предпросмотра")
public record MarkdownPreviewRequest(
        @NotNull(message = "Содержимое обязательно")
        @Size(max = 200000, message = "Содержимое не длиннее {max} символов")
        String content) {
}
