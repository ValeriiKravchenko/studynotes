package com.val.studynotes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Учётные данные в application.properties берутся из окружения, поэтому задаём тестовые
@SpringBootTest(properties = {
		"app.security.username=test-user",
		"app.security.password=test-password"
})
@Testcontainers(disabledWithoutDocker = true)
class StudynotesApplicationTests {

	// Тот же образ и подход, что в support.PostgresDataJpaTest: без Docker тест пропускается
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			POSTGRES.start();
		}
	}

	@Test
	void contextLoads() {
	}

}
