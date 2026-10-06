package com.val.studynotes.repository;

import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteFullTextIndexTest extends PostgresDataJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

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
}
