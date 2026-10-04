package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private FolderResolver folderResolver;

    @TempDir
    Path tempDir;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        // TitleExtractor и PathParser — чистые компоненты без зависимостей, используем настоящие
        importService = new ImportService(noteRepository, new TitleExtractor(), new PathParser(), folderResolver);
    }

    @Test
    @DisplayName("несуществующая директория — ошибка, ничего не сохраняется")
    void import_directoryMissing_returnsError() {
        ImportResult result = importService.importFromDirectory(tempDir.resolve("nope").toString());

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("Директория не найдена"));
        assertEquals(0, result.getTotal());
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("путь указывает на файл — ошибка «не является директорией»")
    void import_pathIsFile_returnsError() throws IOException {
        Path file = Files.writeString(tempDir.resolve("a.md"), "# A");

        ImportResult result = importService.importFromDirectory(file.toString());

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("не является директорией"));
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("директория без .md файлов — ошибка")
    void import_noMarkdownFiles_returnsError() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "text");

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("нет .md файлов"));
        assertEquals(0, result.getTotal());
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("путь с пробелами по краям обрезается")
    void import_pathIsTrimmed() throws IOException {
        Files.writeString(tempDir.resolve("a.md"), "# Заметка");

        ImportResult result = importService.importFromDirectory("  " + tempDir + "  ");

        assertEquals(1, result.getImported());
    }

    @Test
    @DisplayName("файл в корне: заголовок из '# ', содержимое сохраняется, папка null")
    void import_rootFile_savesNoteWithoutFolder() throws IOException {
        String content = "# Stream API\n\nТекст";
        Files.writeString(tempDir.resolve("streams.md"), content);
        when(noteRepository.existsByTitle("Stream API")).thenReturn(false);
        when(folderResolver.resolveFolder(List.of())).thenReturn(null);

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(0, result.getSkipped());
        assertFalse(result.hasErrors());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        Note saved = captor.getValue();
        assertEquals("Stream API", saved.getTitle());
        assertEquals(content, saved.getContent());
        assertNull(saved.getFolder());
    }

    @Test
    @DisplayName("нет заголовка '# ' — название берётся из имени файла без расширения")
    void import_noHeading_usesFilenameAsTitle() throws IOException {
        Files.writeString(tempDir.resolve("my.notes.md"), "просто текст");
        when(noteRepository.existsByTitle("my.notes")).thenReturn(false);

        importService.importFromDirectory(tempDir.toString());

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("my.notes", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("вложенный файл: имена папок передаются в FolderResolver, результат кладётся в заметку")
    void import_nestedFile_resolvesFolderChain() throws IOException {
        Path sub = Files.createDirectories(tempDir.resolve("Java").resolve("Collections"));
        Files.writeString(sub.resolve("list.md"), "# List");
        Folder folder = new Folder("Collections");
        when(noteRepository.existsByTitle("List")).thenReturn(false);
        when(folderResolver.resolveFolder(List.of("Java", "Collections"))).thenReturn(folder);

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(1, result.getImported());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertSame(folder, captor.getValue().getFolder());
    }

    @Test
    @DisplayName("заметка с таким названием уже есть — пропускается, не сохраняется, папки не создаются")
    void import_duplicateTitle_isSkipped() throws IOException {
        Files.writeString(tempDir.resolve("a.md"), "# Дубликат");
        when(noteRepository.existsByTitle("Дубликат")).thenReturn(true);

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getImported());
        assertEquals(1, result.getSkipped());
        verify(noteRepository, never()).save(any());
        verify(folderResolver, never()).resolveFolder(anyList());
    }

    @Test
    @DisplayName("пустой и пробельный файл — пропускается")
    void import_blankFile_isSkipped() throws IOException {
        Files.writeString(tempDir.resolve("empty.md"), "");
        Files.writeString(tempDir.resolve("spaces.md"), "  \n\t\n");

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getSkipped());
        assertEquals(0, result.getImported());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("не-.md файлы игнорируются и не попадают в total")
    void import_nonMarkdownIgnored() throws IOException {
        Files.writeString(tempDir.resolve("a.md"), "# A");
        Files.writeString(tempDir.resolve("b.txt"), "# B");

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getImported());
    }

    @Test
    @DisplayName("несколько файлов, включая вложенные: счётчики сходятся")
    void import_multipleFiles_countersAreConsistent() throws IOException {
        Files.writeString(tempDir.resolve("one.md"), "# One");
        Files.writeString(tempDir.resolve("two.md"), "# Two");
        Files.writeString(tempDir.resolve("blank.md"), " ");
        Files.writeString(Files.createDirectories(tempDir.resolve("sub")).resolve("three.md"), "# Three");
        when(noteRepository.existsByTitle("One")).thenReturn(true);
        when(noteRepository.existsByTitle("Two")).thenReturn(false);
        when(noteRepository.existsByTitle("Three")).thenReturn(false);

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(4, result.getTotal());
        assertEquals(2, result.getImported());
        assertEquals(2, result.getSkipped());
        assertEquals(result.getTotal(), result.getImported() + result.getSkipped());
        verify(noteRepository, times(2)).save(any(Note.class));
    }

    @Test
    @DisplayName("файл в невалидной UTF-8 кодировке — ошибка по файлу, остальные импортируются")
    void import_unreadableFile_reportsErrorAndContinues() throws IOException {
        Files.write(tempDir.resolve("bad.md"), new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xFF});
        Files.writeString(tempDir.resolve("good.md"), "# Good");

        ImportResult result = importService.importFromDirectory(tempDir.toString());

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("bad.md"));
    }
}
