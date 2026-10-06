package com.val.studynotes.controller;

import com.val.studynotes.dto.ErrorResponse;
import com.val.studynotes.dto.HeadingsResult;
import com.val.studynotes.dto.MarkdownPreviewRequest;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Markdown", description = "Безопасный рендер markdown в HTML")
@ApiResponse(responseCode = "401", description = "Нет входа (нет сессии)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
public class MarkdownController {
    private final NoteService noteService;
    private final MarkdownService markdownService;

    public MarkdownController(NoteService noteService, MarkdownService markdownService) {
        this.noteService = noteService;
        this.markdownService = markdownService;
    }

    @Operation(summary = "Рендер сохранённой заметки",
            description = "HTML уже очищен на сервере; клиент не должен очищать его повторно.")
    @ApiResponse(responseCode = "200", description = "HTML и заголовки")
    @ApiResponse(responseCode = "400", description = "Идентификатор не число",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Заметка не найдена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/notes/{id}/render")
    public HeadingsResult renderNote(@PathVariable Long id) {
        return markdownService.renderSafe(noteService.getNoteById(id).getContent());
    }

    /** Предпросмотр без сохранения. */
    @Operation(summary = "Предпросмотр markdown", description = "Рендер без сохранения. Нужен заголовок X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "200", description = "HTML и заголовки")
    @ApiResponse(responseCode = "400", description = "Ошибки валидации: content отсутствует или длиннее 200000 символов",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Нет или неверный токен CSRF",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/markdown/preview")
    public HeadingsResult preview(@Valid @RequestBody MarkdownPreviewRequest request) {
        return markdownService.renderSafe(request.content());
    }
}
