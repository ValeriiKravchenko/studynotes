package com.val.studynotes.controller;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.exception.ImportRejectedException;
import com.val.studynotes.service.ImportService;
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
public class ImportController {
    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** Импорт .md файлов из zip-архива, переданного в поле multipart-формы {@code file}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importZip(@RequestPart("file") MultipartFile file) {
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
