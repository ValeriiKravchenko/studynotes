package com.val.studynotes.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Один PostgreSQL на весь прогон тестов (тот же образ, что в docker-compose.yml).
 * Контейнер запускается при первом обращении к классу и останавливается Ryuk после прогона.
 * Без Docker контейнер не стартует, а тесты, которые его используют, пропускаются
 * через {@code @Testcontainers(disabledWithoutDocker = true)}.
 */
public final class SharedPostgres {

    public static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            CONTAINER.start();
        }
    }

    private SharedPostgres() {
    }
}
