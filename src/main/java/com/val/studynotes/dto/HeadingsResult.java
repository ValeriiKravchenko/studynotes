package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Результат рендера markdown")
public record HeadingsResult(
        @Schema(description = "HTML, уже очищенный на сервере") String html,
        @Schema(description = "Заголовки документа по порядку") List<HeadingInfo> headings) {
}
