package com.val.studynotes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteRequest {
    @NotBlank(message = "Заголовок обязателен")
    @Size(max = 255, message = "Заголовок не длиннее {max} символов")
    private String title;

    @Size(max = 200000, message = "Содержимое не длиннее {max} символов")
    private String content;

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
}
