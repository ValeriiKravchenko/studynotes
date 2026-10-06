package com.val.studynotes;

import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Миграции на пустом PostgreSQL. Контекст поднимается с ddl-auto=validate из application.properties:
 * если схема из миграций расходится с сущностями, контекст не стартует и тест падает.
 */
class FlywayMigrationTest extends PostgresDataJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    @Test
    void hibernateRunsInValidateMode() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void migrationsAreAppliedSuccessfully() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(rows).extracting(r -> r.get("version")).containsExactly("1", "2", "3", "4");
        assertThat(rows).extracting(r -> r.get("success")).containsOnly(true);
    }

    @Test
    void noFailedMigrationsInHistory() {
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);

        assertThat(failed).isZero();
    }

    @Test
    void baselineCreatesTables() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN ('notes', 'folders')",
                Integer.class);

        assertThat(tables).isEqualTo(2);
    }

    @Test
    void folderNameIsNotNull() {
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'folders' AND column_name = 'name'", String.class);

        assertThat(nullable).isEqualTo("NO");
    }
}
