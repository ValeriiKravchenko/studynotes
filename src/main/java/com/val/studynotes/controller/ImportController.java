package com.val.studynotes.controller;

import com.val.studynotes.dto.ErrorResponse;
import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.exception.ImportRejectedException;
import com.val.studynotes.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/import")
@Tag(name = "Импорт", description = "Загрузка заметок из архива")
@ApiResponse(responseCode = "401", description = "Нет входа (нет сессии)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** Импорт .md файлов из zip-архива, переданного в поле multipart-формы {@code file}. */
    @Operation(summary = "Импорт .md из zip-архива",
            description = "Тело запроса multipart/form-data с полем file (zip). Максимальный размер загрузки 10 МБ. "
                    + "Нужен заголовок X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "200", description = "Итог импорта",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImportResult.class)))
    @ApiResponse(responseCode = "400", description = "Нет части file, файл пустой, не zip или превышены лимиты архива",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Нет или неверный токен CSRF",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "Размер загрузки превышает лимит",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importZip(
            @Parameter(description = "Zip-архив с .md файлами") @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ImportRejectedException("Файл пустой");
        }
        try (InputStream in = file.getInputStream()) {
            return importService.importFromZip(in);
        } catch (IOException e) {
            throw new ImportRejectedException("Не удалось прочитать загруженный файл");
        }
    }
}
