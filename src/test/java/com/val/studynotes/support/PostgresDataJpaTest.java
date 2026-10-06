package com.val.studynotes.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Основа для тестов на настоящем PostgreSQL (тот же образ, что в docker-compose.yml).
 * Без Docker тесты пропускаются. Схему создаёт Flyway, как в проде.
 * <p>
 * Контейнер один на весь прогон: Spring кеширует контекст между классами, и контейнер,
 * остановленный после первого класса, оставил бы в кеше контекст с мёртвым подключением.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresDataJpaTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
