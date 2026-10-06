package com.val.studynotes.controller;

import com.val.studynotes.dto.ErrorResponse;
import com.val.studynotes.dto.FolderResponse;
import com.val.studynotes.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@Tag(name = "Папки", description = "Дерево папок")
@ApiResponse(responseCode = "401", description = "Нет входа (нет сессии)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
public class FolderController {
    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @Operation(summary = "Плоский список папок",
            description = "Дерево собирает клиент по parentId; noteCount считает только прямые заметки папки.")
    @ApiResponse(responseCode = "200", description = "Список папок")
    @GetMapping
    public List<FolderResponse> getAllFolders() {
        return folderService.getAllFolders();
    }
}
