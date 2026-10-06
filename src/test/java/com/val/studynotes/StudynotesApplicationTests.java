package com.val.studynotes;

import com.val.studynotes.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Учётные данные в application.properties берутся из окружения, поэтому задаём тестовые
@SpringBootTest(properties = {
		"app.security.username=test-user",
		"app.security.password=test-password"
})
@Testcontainers(disabledWithoutDocker = true)
class StudynotesApplicationTests {

	// Общий контейнер на весь прогон: без Docker тест пропускается
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;

	@Test
	void contextLoads() {
	}

}
