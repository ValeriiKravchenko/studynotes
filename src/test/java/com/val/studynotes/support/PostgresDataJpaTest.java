package com.val.studynotes.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Основа для тестов на настоящем PostgreSQL (тот же образ, что в docker-compose.yml).
 * Без Docker тесты пропускаются. Схему создаёт Flyway, как в проде.
 * <p>
 * Контейнер общий для всего прогона (см. {@link SharedPostgres}), в том числе для StudynotesApplicationTests:
 * Spring кеширует контекст между классами, и остановленный контейнер оставил бы в кеше мёртвое подключение.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresDataJpaTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;
}
