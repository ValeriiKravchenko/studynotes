package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ImportService {
    private final NoteRepository noteRepository;

    public ImportService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public ImportResult importFromDirectory(String directoryPath) {
        ImportResult result = new ImportResult();
        Path directory = Path.of(directoryPath.trim());
        if (!Files.exists(directory)) {
            result.addError("Директория не найдена: " + directoryPath);
            return result;
        }
        if (!Files.isDirectory(directory)) {
            result.addError("Указанный путь не является директорией: " + directoryPath);
            return result;
        }
        List<Path> mdFiles;
        try (Stream<Path> files = Files.walk(directory)) {
            mdFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            result.addError("Ошибка чтения директории: " + e.getMessage());
            return result;
        }
        if (mdFiles.isEmpty()) {
            result.addError("В директории нет .md файлов");
            return result;
        }
        for (Path file : mdFiles) {
            result.incrementTotal();
            processFile(file, result);
        }
        return result;
    }

    private void processFile(Path file, ImportResult result) {
        try {
            String content = Files.readString(file);
            if (content.isBlank()) {
                result.incrementSkipped();
                return;
            }
            String title = extractTitle(file, content);
            if (noteRepository.existsByTitle(title)) {
                result.incrementSkipped();
                return;
            }
            Note note = new Note();
            note.setTitle(title);
            note.setContent(content);
            noteRepository.save(note);
            result.incrementImported();
        } catch (IOException e) {
            result.addError("Не удалось прочитать файл " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private String extractTitle(Path file, String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        String fileName = file.getFileName().toString();
        int doIndex = fileName.lastIndexOf(".");
        return doIndex > 0 ? fileName.substring(0, doIndex) : fileName;
    }
}
