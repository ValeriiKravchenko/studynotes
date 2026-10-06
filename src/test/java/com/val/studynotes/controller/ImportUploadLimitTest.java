package com.val.studynotes.controller;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.exception.GlobalExceptionHandler;
import com.val.studynotes.service.ImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Лимит размера загрузки проверяется на настоящем встроенном сервере: MockMvc multipart-лимиты не применяет.
 * Контекст минимальный (без БД и Spring Security). Лимит понижен до 100KB, а тело 200KB: так сервер успевает
 * вычитать запрос целиком и ответить 413, а не рвёт соединение посреди большой загрузки.
 */
@SpringBootTest(classes = ImportUploadLimitTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.servlet.multipart.max-file-size=100KB",
                "spring.servlet.multipart.max-request-size=100KB"
        })
class ImportUploadLimitTest {

    @Configuration
    @ImportAutoConfiguration({
            TomcatServletWebServerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            MultipartAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class
    })
    @Import({ImportController.class, GlobalExceptionHandler.class})
    static class TestApp {
    }

    @LocalServerPort
    private int port;

    @MockitoBean
    private ImportService importService;

    private HttpResponse<String> upload(int fileBytes) throws Exception {
        String boundary = "testboundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.zip\"\r\n"
                + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[fileBytes]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("лимиты multipart заданы в application.properties (10MB)")
    void limitsConfigured() throws Exception {
        // Читаем сам файл: в этом тесте лимит понижен свойством, чтобы тело запроса было небольшим
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        assertEquals("10MB", props.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("10MB", props.getProperty("spring.servlet.multipart.max-request-size"));
    }

    @Test
    @DisplayName("загрузка больше лимита: 413 в формате ErrorResponse, сервис не вызывается")
    void oversizedUpload_returns413() throws Exception {
        HttpResponse<String> response = upload(200 * 1024);

        assertEquals(413, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":413"), response.body());
        assertTrue(response.body().contains("Размер загружаемого файла превышает допустимый"), response.body());
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("загрузка в пределах лимита доходит до сервиса")
    void uploadWithinLimit_reachesService() throws Exception {
        when(importService.importFromZip(any())).thenReturn(new ImportResult());

        HttpResponse<String> response = upload(1024);

        assertEquals(200, response.statusCode(), response.body());
    }
}
