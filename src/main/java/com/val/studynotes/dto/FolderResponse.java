package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/** Папка в плоском списке: дерево собирает клиент по parentId. noteCount считает только прямые заметки папки. */
@Schema(description = "Папка в плоском списке")
public record FolderResponse(
        Long id,
        String name,
        @Schema(description = "Идентификатор родительской папки; null для корневой", nullable = true)
        Long parentId,
        @Schema(description = "Число заметок непосредственно в этой папке")
        long noteCount) {
}
