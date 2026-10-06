package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Данные для создания или изменения заметки")
public class NoteRequest {
    @NotBlank(message = "Заголовок обязателен")
    @Size(max = 255, message = "Заголовок не длиннее {max} символов")
    @Schema(description = "Заголовок", example = "Spring Security")
    private String title;

    @Size(max = 200000, message = "Содержимое не длиннее {max} символов")
    @Schema(description = "Содержимое в markdown")
    private String content;

    /** Необязательное поле: null означает «без папки». */
    @Schema(description = "Идентификатор папки; null означает «без папки»", nullable = true)
    private Long folderId;

    public NoteRequest() {
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

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }
}
