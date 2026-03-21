package com.val.studynotes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathParserTest {
    private PathParser parser;

    @BeforeEach
    void setUp() {
        parser = new PathParser();
    }

    @Test
    @DisplayName("Файл в корне — пустой список папок")
    void fileInRoot_returnsEmptyList() {
        Path root = Path.of("C:/vault");
        Path file = Path.of("C:/vault/Docker.md");
        List<String> folders = parser.extractFolderNames(root, file);
        assertTrue(folders.isEmpty());
    }

    @Test
    @DisplayName("Файл в одной папке — список из одного элемента")
    void fileInOneFolder_returnsSingleFolder() {
        Path root = Path.of("C:/vault");
        Path file = Path.of("C:/vault/Git/Команды.md");
        List<String> folders = parser.extractFolderNames(root, file);
        assertEquals(1, folders.size());
        assertEquals("Git", folders.get(0));
    }

    @Test
    @DisplayName("Файл во вложенных папках — полный путь")
    void fileInNestedFolders_returnsFullPath() {
        Path root = Path.of("C:/vault");
        Path file = Path.of("C:/vault/Java/Core/Collections.md");
        List<String> folders = parser.extractFolderNames(root, file);
        assertEquals(2, folders.size());
        assertEquals("Java", folders.get(0));
        assertEquals("Core", folders.get(1));
    }

    @Test
    @DisplayName("Глубокая вложенность — три уровня")
    void deepNesting_returnsAllLevels() {
        Path root = Path.of("C:/vault");
        Path file = Path.of("C:/vault/A/B/C/note.md");
        List<String> folders = parser.extractFolderNames(root, file);
        assertEquals(List.of("A", "B", "C"), folders);
    }
}
