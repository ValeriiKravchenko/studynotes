package com.val.studynotes;

import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

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
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true AND version IS NOT NULL",
                Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void baselineCreatesTables() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN ('notes', 'folders')",
                Integer.class);

        assertThat(tables).isEqualTo(2);
    }
}
