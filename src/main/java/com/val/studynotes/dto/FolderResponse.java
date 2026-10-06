package com.val.studynotes.dto;

/** Папка в плоском списке: дерево собирает клиент по parentId. noteCount считает только прямые заметки папки. */
public record FolderResponse(Long id, String name, Long parentId, long noteCount) {
}
