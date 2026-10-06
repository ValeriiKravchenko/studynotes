package com.val.studynotes.controller;

import com.val.studynotes.dto.HeadingsResult;
import com.val.studynotes.dto.MarkdownPreviewRequest;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Рендер markdown через API. Ответ только JSON ({@link HeadingsResult}: {@code html}, {@code headings}).
 * Поле {@code html} уже очищено на сервере: клиент не должен очищать его повторно,
 * но и не должен рендерить {@code content} как HTML сам.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class MarkdownController {
    private final NoteService noteService;
    private final MarkdownService markdownService;

    public MarkdownController(NoteService noteService, MarkdownService markdownService) {
        this.noteService = noteService;
        this.markdownService = markdownService;
    }

    @GetMapping("/api/notes/{id}/render")
    public HeadingsResult renderNote(@PathVariable Long id) {
        return markdownService.renderSafe(noteService.getNoteById(id).getContent());
    }

    /** Предпросмотр без сохранения. */
    @PostMapping("/api/markdown/preview")
    public HeadingsResult preview(@Valid @RequestBody MarkdownPreviewRequest request) {
        return markdownService.renderSafe(request.content());
    }
}
