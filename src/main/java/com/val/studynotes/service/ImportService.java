package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Folder;
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
    private final TitleExtractor titleExtractor;
    private final PathParser pathParser;
    private final FolderResolver folderResolver;

    public ImportService(NoteRepository noteRepository, TitleExtractor titleExtractor, PathParser pathParser, FolderResolver folderResolver) {
        this.noteRepository = noteRepository;
        this.titleExtractor = titleExtractor;
        this.pathParser = pathParser;
        this.folderResolver = folderResolver;
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
            processFile(file, directory, result);
        }
        return result;
    }

    private void processFile(Path file, Path rootDirectory, ImportResult result) {
        try {
            String content = Files.readString(file);
            if (content.isBlank()) {
                result.incrementSkipped();
                return;
            }
            String filename = file.getFileName().toString();
            int doIndex = filename.lastIndexOf(".");
            String fallbackName = doIndex > 0 ? filename.substring(0, doIndex) : filename;
            String title = titleExtractor.extract(content, fallbackName);
            if (noteRepository.existsByTitle(title)) {
                result.incrementSkipped();
                return;
            }
            List<String> folderNames = pathParser.extractFolderNames(rootDirectory, file);
            Folder folder = folderResolver.resolveFolder(folderNames);
            Note note = new Note();
            note.setTitle(title);
            note.setContent(content);
            note.setFolder(folder);
            noteRepository.save(note);
            result.incrementImported();
        } catch (IOException e) {
            result.addError("Не удалось прочитать файл " + file.getFileName() + ": " + e.getMessage());
        }
    }
}
