package com.val.studynotes.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Основа для тестов с настоящими транзакциями и потоками на общем PostgreSQL (см. {@link SharedPostgres}).
 * Данные здесь коммитятся, поэтому после каждого теста таблицы чистятся: остальные тесты
 * (с откатом транзакции) рассчитывают на пустую базу.
 */
@SpringBootTest(properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresSpringBootTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;

    @Autowired
    protected JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM notes");
        jdbc.execute("DELETE FROM folders WHERE parent_id IS NOT NULL");
        jdbc.execute("DELETE FROM folders");
    }
}
