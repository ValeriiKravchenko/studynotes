package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.exception.ImportRejectedException;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionOperations;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static com.val.studynotes.service.ZipTestSupport.entries;
import static com.val.studynotes.service.ZipTestSupport.zip;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceZipTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private FolderResolver folderResolver;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ImportService(noteRepository, new TitleExtractor(), folderResolver,
                TransactionOperations.withoutTransaction());
    }

    private ImportResult run(byte[] archive) {
        return importService.importFromZip(new ByteArrayInputStream(archive));
    }

    private ImportRejectedException rejected(byte[] archive) {
        return assertThrows(ImportRejectedException.class, () -> run(archive));
    }

    // ---------- прежняя логика папок, заголовков, дубликатов ----------

    @Test
    @DisplayName("файл в корне архива: заголовок из '# ', папка не создаётся")
    void rootFile_savedWithTitleAndNoFolder() {
        when(folderResolver.resolveFolder(List.of())).thenReturn(null);

        ImportResult result = run(zip(entries("streams.md", "# Stream API\n\nТекст")));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getImported());
        assertFalse(result.hasErrors());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("Stream API", captor.getValue().getTitle());
        assertEquals("# Stream API\n\nТекст", captor.getValue().getContent());
        assertNull(captor.getValue().getFolder());
    }

    @Test
    @DisplayName("нет заголовка: название из имени файла без расширения")
    void noHeading_usesFilename() {
        run(zip(entries("dir/my.notes.md", "просто текст")));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("my.notes", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("вложенные папки передаются в FolderResolver по порядку")
    void nestedFile_resolvesFolderChain() {
        run(zip(entries("Java/Collections/list.md", "# List")));

        verify(folderResolver).resolveFolder(List.of("Java", "Collections"));
    }

    @Test
    @DisplayName("дубликат по названию пропускается, папки не создаются")
    void duplicateTitle_skipped() {
        when(noteRepository.existsByTitle("Dup")).thenReturn(true);

        ImportResult result = run(zip(entries("a/dup.md", "# Dup")));

        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getImported());
        verify(noteRepository, never()).save(any());
        verifyNoInteractions(folderResolver);
    }

    @Test
    @DisplayName("пустой и пробельный .md пропускаются")
    void blankFile_skipped() {
        ImportResult result = run(zip(entries("empty.md", "", "blank.md", "  \n ")));

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getSkipped());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("несколько файлов, включая вложенные: счётчики сходятся")
    void multipleFiles_countersAreConsistent() {
        when(noteRepository.existsByTitle("One")).thenReturn(true);

        ImportResult result = run(zip(entries(
                "one.md", "# One",
                "two.md", "# Two",
                "blank.md", " ",
                "sub/three.md", "# Three")));

        assertEquals(4, result.getTotal());
        assertEquals(2, result.getImported());
        assertEquals(2, result.getSkipped());
        assertEquals(result.getTotal(), result.getImported() + result.getSkipped());
        verify(noteRepository, times(2)).save(any(Note.class));
    }

    @Test
    @DisplayName("записи-каталоги не учитываются")
    void directoryEntries_ignored() {
        ImportResult result = run(zip(entries("Java/", null, "Java/a.md", "# A")));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(0, result.getIgnored());
    }

    // ---------- только .md ----------

    @Test
    @DisplayName("не-.md файлы игнорируются, считаются в ignored и не попадают в total")
    void nonMarkdown_ignoredAndCounted() {
        ImportResult result = run(zip(entries("a.md", "# A", "b.txt", "x", "img/c.png", "y")));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(2, result.getIgnored());
        verify(noteRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("архив без .md: ошибка в результате, ничего не сохраняется")
    void noMarkdown_reportsError() {
        ImportResult result = run(zip(entries("a.txt", "x")));

        assertTrue(result.hasErrors());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getIgnored());
        verifyNoInteractions(noteRepository);
    }

    // ---------- zip-slip ----------

    @Test
    @DisplayName("zip-slip: '..', абсолютный путь, обратные слэши, диск — запись пропущена с ошибкой, остальные импортируются")
    void unsafeNames_skippedWithError() {
        Map<String, byte[]> archive = entries(
                "../evil.md", "# Evil1",
                "a/../../evil.md", "# Evil2",
                "/etc/evil.md", "# Evil3",
                "..\\evil.md", "# Evil4",
                "dir\\evil.md", "# Evil5",
                "C:/evil.md", "# Evil6",
                "ok.md", "# Ok");

        ImportResult result = run(zip(archive));

        assertEquals(1, result.getImported());
        assertEquals(6, result.getErrors().size());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("Ok", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("zip-slip: имя '..' внутри сегмента (например 'a..b.md') безопасно и импортируется")
    void dotsInsideSegment_allowed() {
        ImportResult result = run(zip(entries("a..b.md", "# T")));

        assertEquals(1, result.getImported());
    }

    @Test
    @DisplayName("isSafeName: NUL в имени отклоняется")
    void nulInName_rejected() {
        assertFalse(ImportService.isSafeName("a\0b.md"));
        assertFalse(ImportService.isSafeName("a/../b.md"));
        assertTrue(ImportService.isSafeName("a/b/c.md"));
    }

    // ---------- строгий UTF-8 ----------

    @Test
    @DisplayName("некорректный UTF-8: запись даёт ошибку и не сохраняется, без подмены символов; остальные импортируются")
    void invalidUtf8_errorAndContinue() {
        byte[] bad = {(byte) 0xC3, (byte) 0x28, (byte) 0xFF};

        ImportResult result = run(zip(entries("bad.md", bad, "good.md", "# Good")));

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("bad.md"));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("Good", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("кириллица в содержимом и именах читается корректно")
    void cyrillic_roundTrip() {
        run(zip(entries("Папка/файл.md", "текст без заголовка")));

        verify(folderResolver).resolveFolder(List.of("Папка"));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("файл", captor.getValue().getTitle());
    }

    // ---------- не zip / пустой ----------

    @Test
    @DisplayName("не-zip данные: ImportRejectedException")
    void notZip_rejected() {
        assertTrue(rejected(ZipTestSupport.text("это просто текст, не архив")).getMessage().contains("zip"));
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("пустой поток: ImportRejectedException")
    void emptyStream_rejected() {
        rejected(new byte[0]);
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("zip без записей: ImportRejectedException")
    void zipWithoutEntries_rejected() {
        rejected(zip(Map.of()));
    }

    // ---------- zip-bomb ----------

    @Test
    @DisplayName("лимит записей: 5000 допустимо, 5001 — отклонение до первого сохранения")
    void entryCountLimit() {
        Map<String, byte[]> ok = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ImportService.MAX_ENTRIES; i++) {
            ok.put("f" + i + ".txt", new byte[0]);
        }
        assertDoesNotThrow(() -> run(zip(ok)));

        Map<String, byte[]> tooMany = new java.util.LinkedHashMap<>(ok);
        tooMany.put("a.md", ZipTestSupport.text("# A"));
        tooMany.put("last.txt", new byte[0]);
        ImportRejectedException ex = rejected(zip(tooMany));
        assertTrue(ex.getMessage().contains("5000"));
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("лимит размера записи: ровно 2 МБ допустимо, на байт больше — отклонение")
    void entrySizeLimit() {
        byte[] atLimit = new byte[(int) ImportService.MAX_ENTRY_BYTES];
        java.util.Arrays.fill(atLimit, (byte) 'a');
        assertDoesNotThrow(() -> run(zip(entries("big.md", atLimit))));
        clearInvocations(noteRepository);

        byte[] over = new byte[(int) ImportService.MAX_ENTRY_BYTES + 1];
        java.util.Arrays.fill(over, (byte) 'a');
        ImportRejectedException ex = rejected(zip(entries("ok.md", "# Ok", "big.md", over)));
        assertTrue(ex.getMessage().contains("2 МБ"));
        // отклонение целиком: даже корректная запись до проблемной не сохранена
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("лимит размера записи действует и на не-.md файлы")
    void entrySizeLimit_appliesToNonMarkdown() {
        byte[] over = new byte[(int) ImportService.MAX_ENTRY_BYTES + 1];
        rejected(zip(entries("big.bin", over)));
    }

    @Test
    @DisplayName("суммарный лимит: 26 записей по 2 МБ (> 50 МБ) — отклонение, ничего не сохранено")
    void totalSizeLimit() {
        Map<String, byte[]> archive = new java.util.LinkedHashMap<>();
        archive.put("first.md", ZipTestSupport.text("# First"));
        byte[] chunk = new byte[(int) ImportService.MAX_ENTRY_BYTES];
        for (int i = 0; i < 26; i++) {
            archive.put("blob" + i + ".bin", chunk);
        }

        ImportRejectedException ex = rejected(zip(archive));

        assertTrue(ex.getMessage().contains("50 МБ"));
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("сильно сжимаемые данные считаются по реально распакованным байтам, а не по размеру архива")
    void compressionRatio_countsRealBytes() {
        byte[] zeros = new byte[(int) ImportService.MAX_ENTRY_BYTES];
        Map<String, byte[]> archive = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 26; i++) {
            archive.put("z" + i + ".bin", zeros);
        }
        byte[] zipped = zip(archive);
        assertTrue(zipped.length < 1024 * 1024, "архив должен быть мал, иначе тест не проверяет степень сжатия");

        rejected(zipped);
    }

    // ---------- длина заголовка и имени папки (колонки VARCHAR(255)) ----------

    @Test
    @DisplayName("заголовок ровно 255 символов импортируется")
    void title255_imported() {
        String title = "a".repeat(255);

        ImportResult result = run(zip(entries("x.md", "# " + title)));

        assertEquals(1, result.getImported());
        assertFalse(result.hasErrors());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals(title, captor.getValue().getTitle());
    }

    @Test
    @DisplayName("заголовок 256 символов: файл пропущен с причиной, остальные импортируются")
    void title256_skippedWithReason() {
        ImportResult result = run(zip(entries("long.md", "# " + "a".repeat(256), "ok.md", "# Ok")));

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("long.md"));
        assertTrue(result.getErrors().get(0).contains("255"));
        verify(noteRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("заголовок из имени файла длиннее 255 символов тоже пропускается")
    void fallbackTitle256_skippedWithReason() {
        ImportResult result = run(zip(entries("a".repeat(256) + ".md", "без заголовка")));

        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getErrors().size());
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("имя папки ровно 255 символов: файл импортируется")
    void folderName255_imported() {
        String folder = "d".repeat(255);

        ImportResult result = run(zip(entries(folder + "/x.md", "# X")));

        assertEquals(1, result.getImported());
        verify(folderResolver).resolveFolder(List.of(folder));
    }

    @Test
    @DisplayName("имя папки 256 символов (в любом звене пути): файл пропущен с причиной, папки не создаются")
    void folderName256_skippedWithReason() {
        ImportResult result = run(zip(entries("ok/" + "d".repeat(256) + "/x.md", "# X", "y.md", "# Y")));

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("x.md"));
        verify(folderResolver, never()).resolveFolder(List.of("ok", "d".repeat(256)));
        verify(folderResolver).resolveFolder(List.of());
    }

    @Test
    @DisplayName("файл, пропущенный из-за длинного имени папки, не блокирует следующий файл с тем же заголовком")
    void skippedFileDoesNotReserveTitle() {
        ImportResult result = run(zip(entries(
                "d".repeat(256) + "/x.md", "# Same",
                "y.md", "# Same")));

        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        verify(noteRepository, times(1)).save(any());
    }
}
