package com.val.studynotes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkdownPreviewRequest(
        @NotNull(message = "Содержимое обязательно")
        @Size(max = 200000, message = "Содержимое не длиннее {max} символов")
        String content) {
}
