package com.val.studynotes;

import com.val.studynotes.support.SharedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V4 на данных с NULL в folders.name. Отдельная база в общем контейнере: сначала схема V3 и «старые» строки,
 * затем миграция до последней версии.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationV4Test {

    private static DataSource createDatabase(String name) {
        PGSimpleDataSource admin = new PGSimpleDataSource();
        admin.setUrl(SharedPostgres.CONTAINER.getJdbcUrl());
        admin.setUser(SharedPostgres.CONTAINER.getUsername());
        admin.setPassword(SharedPostgres.CONTAINER.getPassword());
        new JdbcTemplate(admin).execute("DROP DATABASE IF EXISTS " + name);
        new JdbcTemplate(admin).execute("CREATE DATABASE " + name);

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(SharedPostgres.CONTAINER.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"));
        ds.setUser(SharedPostgres.CONTAINER.getUsername());
        ds.setPassword(SharedPostgres.CONTAINER.getPassword());
        return ds;
    }

    @Test
    void v4RenamesNullFoldersUniquelyAndAddsNotNull() {
        DataSource ds = createDatabase("migration_v4_test");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).target("3").load().migrate();
        // Старая схема допускает NULL, в том числе несколько корневых безымянных папок
        Long rootA = jdbc.queryForObject("INSERT INTO folders (name, parent_id) VALUES (NULL, NULL) RETURNING id", Long.class);
        Long rootB = jdbc.queryForObject("INSERT INTO folders (name, parent_id) VALUES (NULL, NULL) RETURNING id", Long.class);
        Long child = jdbc.queryForObject("INSERT INTO folders (name, parent_id) VALUES (NULL, ?) RETURNING id", Long.class, rootA);
        Long named = jdbc.queryForObject("INSERT INTO folders (name, parent_id) VALUES ('Java', NULL) RETURNING id", Long.class);
        jdbc.update("INSERT INTO notes (title, folder_id) VALUES ('n', ?)", rootA);

        Flyway.configure().dataSource(ds).load().migrate();

        List<String> names = jdbc.queryForList("SELECT name FROM folders ORDER BY id", String.class);
        assertThat(names).hasSize(4).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT name FROM folders WHERE id = ?", String.class, named)).isEqualTo("Java");
        assertThat(jdbc.queryForObject("SELECT name FROM folders WHERE id = ?", String.class, rootA))
                .isEqualTo("Без названия " + rootA);
        assertThat(jdbc.queryForObject("SELECT name FROM folders WHERE id = ?", String.class, rootB))
                .isEqualTo("Без названия " + rootB);
        assertThat(jdbc.queryForObject("SELECT name FROM folders WHERE id = ?", String.class, child))
                .isEqualTo("Без названия " + child);
        // связи сохранены
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notes WHERE folder_id = ?", Integer.class, rootA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM folders WHERE id = ?", Long.class, child)).isEqualTo(rootA);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO folders (name, parent_id) VALUES (NULL, NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v4OnDatabaseWithoutNullsChangesNothing() {
        DataSource ds = createDatabase("migration_v4_clean_test");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).target("3").load().migrate();
        jdbc.update("INSERT INTO folders (name, parent_id) VALUES ('A', NULL)");

        Flyway.configure().dataSource(ds).load().migrate();

        assertThat(jdbc.queryForList("SELECT name FROM folders", String.class)).containsExactly("A");
    }
}
