package com.val.studynotes.repository;

import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteFullTextIndexTest extends PostgresDataJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NoteRepository noteRepository;

    @Test
    void ginIndexExists() {
        List<String> definitions = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'notes' AND indexname = 'idx_notes_fulltext'",
                String.class);

        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0)).containsIgnoringCase("USING gin");
    }

    @Test
    void searchExpressionUsesTheIndex() {
        jdbc.update("INSERT INTO notes (title, content) VALUES ('Транзакции', 'изоляция')");
        // на маленькой таблице планировщик выбрал бы последовательный скан
        jdbc.execute("SET enable_seqscan = off");

        // то же выражение, что в NoteRepository.fullTextSearch
        List<String> plan = jdbc.queryForList("""
                EXPLAIN SELECT * FROM notes
                WHERE to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(content, ''))
                   @@ plainto_tsquery('russian', 'транзакция')
                """, String.class);

        assertThat(String.join("\n", plan)).contains("idx_notes_fulltext");
    }

    @Test
    void realRepositoryQueryUsesTheIndex() throws NoSuchMethodException {
        jdbc.update("INSERT INTO notes (title, content) VALUES ('Транзакции', 'изоляция')");
        // Статистика idx_scan обновляется только после завершения транзакции, а тест идёт в откатываемой
        // транзакции, поэтому сравнивать счётчики нельзя. Берём SQL прямо из @Query репозитория и смотрим план.
        String sql = NoteRepository.class.getMethod("fullTextSearch", String.class)
                .getAnnotation(Query.class).value().replace(":query", "?");
        jdbc.execute("SET enable_seqscan = off");

        List<String> plan = jdbc.queryForList("EXPLAIN " + sql, String.class, "транзакция", "транзакция");

        assertThat(String.join("\n", plan)).contains("idx_notes_fulltext");
        // и сам метод на тех же данных работает
        assertThat(noteRepository.fullTextSearch("транзакция")).hasSize(1);
    }

    @Test
    void foreignKeyAndTitleIndexesExist() {
        List<String> names = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexname IN "
                        + "('idx_notes_folder_id', 'idx_folders_parent_id', 'idx_notes_title')",
                String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "idx_notes_folder_id", "idx_folders_parent_id", "idx_notes_title");
    }
}
