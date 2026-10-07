package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import com.val.studynotes.support.PostgresSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static com.val.studynotes.service.ZipTestSupport.entries;
import static com.val.studynotes.service.ZipTestSupport.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Импорт на настоящем PostgreSQL: транзакционность, дубликаты внутри архива, границы длины. */
class ImportServiceDatabaseTest extends PostgresSpringBootTest {

    @Autowired
    private ImportService importService;

    // Подмена нужна только чтобы вызвать сбой внутри транзакции заметок; остальные тесты работают с настоящим поведением
    @MockitoSpyBean
    private NoteRepository noteRepository;

    private ImportResult run(Map<String, byte[]> archive) {
        return importService.importFromZip(new ByteArrayInputStream(zip(archive)));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // ---------- пункт 2: всё или ничего ----------

    @Test
    @DisplayName("сбой внутри транзакции заметок после сохранения первой: ни одна заметка не остаётся")
    void failureInTheMiddle_leavesNoNotes() {
        // Заметка A сохраняется по-настоящему, на заметке B save падает уже внутри транзакции заметок
        doThrow(new IllegalStateException("сбой посреди сохранения"))
                .when(noteRepository).save(argThat((Note n) -> "B".equals(n.getTitle())));
        Map<String, byte[]> archive = entries(
                "a.md", "# A",
                "dir/b.md", "# B",
                "c.md", "# C");

        assertThatThrownBy(() -> run(archive))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("сбой посреди сохранения");

        // сбой случился именно после сохранения одной заметки, а не до транзакции
        verify(noteRepository, times(2)).save(any(Note.class));
        assertThat(count("notes")).isZero();
    }

    @Test
    @DisplayName("БД отклоняет текст с NUL при вставке (заголовок чистый): ни одна заметка не остаётся")
    void dbRejectsNulInContent_leavesNoNotes() {
        Map<String, byte[]> archive = entries(
                "a.md", "# A",
                "dir/b.md", "# B",
                "bad.md", "# Bad\nтекст с NUL \u0000",
                "c.md", "# C");

        assertThatThrownBy(() -> run(archive)).isInstanceOf(RuntimeException.class);

        assertThat(count("notes")).isZero();
    }

    @Test
    @DisplayName("после неудачного импорта тот же архив без проблемной записи импортируется полностью")
    void retryAfterFailure_works() {
        assertThatThrownBy(() -> run(entries("dir/a.md", "# A", "bad.md", "# Bad\u0000")))
                .isInstanceOf(RuntimeException.class);

        ImportResult result = run(entries("dir/a.md", "# A", "b.md", "# B"));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(count("notes")).isEqualTo(2);
        // папка dir, созданная в отдельной транзакции, переиспользована, а не продублирована
        assertThat(count("folders")).isEqualTo(1);
    }

    @Test
    @DisplayName("компромисс: после отката заметок созданные папки остаются пустыми")
    void failureInTheMiddle_leavesCreatedFoldersEmpty() {
        assertThatThrownBy(() -> run(entries("dir/a.md", "# A", "other/bad.md", "# Bad\u0000")))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("notes")).isZero();
        assertThat(count("folders")).isEqualTo(2);
    }

    @Test
    @DisplayName("успешный импорт сохраняет заметки и папки")
    void success_commits() {
        ImportResult result = run(entries("Java/Core/a.md", "# A", "b.md", "# B"));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(count("notes")).isEqualTo(2);
        assertThat(count("folders")).isEqualTo(2);
    }

    // ---------- пункт 5: дубликаты заголовков внутри архива ----------

    @Test
    @DisplayName("два файла с одним заголовком в одном архиве: вторая заметка пропускается, дубликата в БД нет")
    void duplicateTitlesInsideArchive_secondSkipped() {
        ImportResult result = run(entries(
                "one.md", "# Same\nпервая",
                "other/two.md", "# Same\nвторая",
                "three.md", "# Different"));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notes WHERE title = 'Same'", Integer.class)).isEqualTo(1);
        // сохраняется первая; папка пропущенного файла не создаётся
        assertThat(jdbc.queryForObject("SELECT content FROM notes WHERE title = 'Same'", String.class))
                .isEqualTo("# Same\nпервая");
        assertThat(count("folders")).isZero();
    }

    @Test
    @DisplayName("повторный импорт того же архива: все заметки пропускаются")
    void secondImportOfSameArchive_allSkipped() {
        Map<String, byte[]> archive = entries("a.md", "# A", "d/b.md", "# B");
        run(archive);

        ImportResult second = run(archive);

        assertThat(second.getImported()).isZero();
        assertThat(second.getSkipped()).isEqualTo(2);
        assertThat(count("notes")).isEqualTo(2);
    }

    // ---------- пункт 4: границы 255 / 256 на настоящей БД ----------

    @Test
    @DisplayName("заголовок 255 сохраняется, 256 пропускается с причиной, остальное импортируется")
    void titleBoundaries() {
        ImportResult result = run(entries(
                "t255.md", "# " + "a".repeat(255),
                "t256.md", "# " + "b".repeat(256),
                "ok.md", "# Ok"));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("t256.md");
        assertThat(count("notes")).isEqualTo(2);
    }

    @Test
    @DisplayName("имя папки 255 сохраняется, 256 пропускает файл с причиной, остальное импортируется")
    void folderNameBoundaries() {
        ImportResult result = run(entries(
                "d".repeat(255) + "/in255.md", "# In255",
                "e".repeat(256) + "/in256.md", "# In256",
                "ok.md", "# Ok"));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("in256.md");
        assertThat(count("folders")).isEqualTo(1);
        assertThat(count("notes")).isEqualTo(2);
    }

    @Test
    @DisplayName("кириллица ровно 255 символов в заголовке и в имени папки проходит (VARCHAR считает символы, не байты)")
    void cyrillic255_fits() {
        String name = "я".repeat(255);

        ImportResult result = run(entries(name + "/x.md", "# " + name));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
    }
}
