package com.val.studynotes.controller;

import com.val.studynotes.dto.ErrorResponse;
import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "Заметки", description = "Создание, чтение, изменение, удаление и поиск заметок")
@ApiResponse(responseCode = "401", description = "Нет входа (нет сессии)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
public class NoteController {
    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @Operation(summary = "Список заметок",
            description = "Все заметки или, если задан folderId, заметки этой папки (без вложенных папок).")
    @ApiResponse(responseCode = "200", description = "Список заметок")
    @ApiResponse(responseCode = "400", description = "Некорректный folderId (не число)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<NoteResponse> getAllNotes(
            @Parameter(description = "Идентификатор папки; без него возвращаются все заметки")
            @RequestParam(required = false) Long folderId) {
        if (folderId != null) {
            return noteService.getNotesByFolder(folderId);
        }
        return noteService.getAllNotes();
    }

    /** Литеральный путь выбирается раньше шаблона /{id}. Пустой и пробельный запрос даёт пустой список. */
    @Operation(summary = "Полнотекстовый поиск",
            description = "Поиск по заголовку и содержимому. Пустой или пробельный запрос даёт пустой список.")
    @ApiResponse(responseCode = "200", description = "Найденные заметки")
    @ApiResponse(responseCode = "400", description = "Параметр query отсутствует или длиннее 200 символов",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/search")
    public List<NoteResponse> searchNotes(
            @RequestParam @Size(max = 200, message = "Запрос не длиннее {max} символов") String query) {
        return noteService.searchNotes(query);
    }

    @Operation(summary = "Заметка по идентификатору")
    @ApiResponse(responseCode = "200", description = "Заметка")
    @ApiResponse(responseCode = "400", description = "Идентификатор не число",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Заметка не найдена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public NoteResponse getNoteById(@PathVariable Long id) {
        return noteService.getNoteById(id);
    }

    @Operation(summary = "Создать заметку", description = "Нужен заголовок X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "201", description = "Заметка создана")
    @ApiResponse(responseCode = "400", description = "Ошибки валидации (fieldErrors) или несуществующая папка в folderId",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Нет или неверный токен CSRF",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<NoteResponse> createNote(@Valid @RequestBody NoteRequest request) {
        NoteResponse created = noteService.createNote(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Изменить заметку", description = "Нужен заголовок X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "200", description = "Заметка обновлена")
    @ApiResponse(responseCode = "400", description = "Ошибки валидации (fieldErrors) или несуществующая папка в folderId",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Нет или неверный токен CSRF",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Заметка не найдена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/{id}")
    public NoteResponse updateNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return noteService.updateNote(id, request);
    }

    @Operation(summary = "Удалить заметку", description = "Нужен заголовок X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "204", description = "Заметка удалена")
    @ApiResponse(responseCode = "403", description = "Нет или неверный токен CSRF",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Заметка не найдена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
