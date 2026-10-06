package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Заметка")
public class NoteResponse {
    private Long id;
    private String title;
    @Schema(description = "Содержимое в markdown (исходный текст, не HTML)")
    private String content;
    @Schema(description = "Имя папки; null, если заметка без папки", nullable = true)
    private String folderName;
    @Schema(description = "Полный путь папки; null, если заметка без папки", nullable = true)
    private String folderPath;
    @Schema(description = "Идентификатор папки; null, если заметка без папки", nullable = true)
    private Long folderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoteResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
