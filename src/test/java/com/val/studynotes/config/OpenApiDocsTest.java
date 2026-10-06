package com.val.studynotes.config;

import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Документ OpenAPI и Swagger UI на реальной цепочке безопасности: доступ только после входа, состав контракта. */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class, OpenApiConfig.class})
@ImportAutoConfiguration({
        SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocSpecPropertiesConfiguration.class,
        SpringDocSecurityConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class,
        SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class
})
@TestPropertySource(properties = {
        "app.security.username=docs-user-name",
        "app.security.password=docs-secret-value-123"
})
class OpenApiDocsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern EXTERNAL = Pattern.compile("(?i)(https?:)?//[a-z0-9.-]+\\.[a-z]{2,}");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;
    @MockitoBean
    private ImportService importService;
    @MockitoBean
    private FolderService folderService;
    @MockitoBean
    private MarkdownService markdownService;

    private String docsText() throws Exception {
        return mockMvc.perform(get("/v3/api-docs").with(user("docs-user-name")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode docs() throws Exception {
        return MAPPER.readTree(docsText());
    }

    // ---------- доступ ----------

    @Test
    @DisplayName("Аноним: GET /v3/api-docs редирект на /login, документа в ответе нет")
    void anonymous_apiDocs_redirectsToLogin() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("openapi"));
    }

    @Test
    @DisplayName("Аноним: Swagger UI (/swagger-ui.html, /swagger-ui/index.html) редирект на /login")
    void anonymous_swaggerUi_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Со входом: Swagger UI отдаётся, index.html без ссылок на внешние хосты")
    void authenticated_swaggerUi_hasNoExternalResources() throws Exception {
        mockMvc.perform(get("/swagger-ui.html").with(user("docs-user-name")))
                .andExpect(status().is3xxRedirection());

        String index = mockMvc.perform(get("/swagger-ui/index.html").with(user("docs-user-name")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String initializer = mockMvc.perform(get("/swagger-ui/swagger-initializer.js").with(user("docs-user-name")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // index.html целиком без внешних адресов
        assertFalse(EXTERNAL.matcher(index).find(), "внешние адреса в index.html");
        // В initializer остаётся адрес по умолчанию из webjar (petstore); springdoc добавляет configUrl,
        // из которого берутся url=/v3/api-docs и validatorUrl="" (внешний валидатор отключён)
        List<String> found = new ArrayList<>();
        Matcher m = EXTERNAL.matcher(initializer);
        while (m.find()) {
            found.add(m.group());
        }
        assertEquals(List.of("https://petstore.swagger.io"), found);
        assertTrue(initializer.contains("\"configUrl\" : \"/v3/api-docs/swagger-config\""));
        assertTrue(initializer.contains("\"validatorUrl\" : \"\""));
        String config = mockMvc.perform(get("/v3/api-docs/swagger-config").with(user("docs-user-name")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("/v3/api-docs", MAPPER.readTree(config).get("url").asString());
        assertTrue(index.contains("swagger-ui-bundle.js"));
    }

    // ---------- состав документа ----------

    @Test
    @DisplayName("Со входом: 200, openapi 3.x, все пути /api/**")
    void authenticated_apiDocs_containsAllPaths() throws Exception {
        JsonNode doc = docs();
        assertTrue(doc.get("openapi").asString().startsWith("3."));
        JsonNode paths = doc.get("paths");
        for (String path : List.of("/api/notes", "/api/notes/{id}", "/api/notes/search", "/api/notes/{id}/render",
                "/api/folders", "/api/import", "/api/markdown/preview",
                "/api/auth/login", "/api/auth/me", "/api/auth/logout")) {
            assertTrue(paths.has(path), "нет пути " + path);
        }
    }

    @Test
    @DisplayName("Выход описан вручную как POST /api/auth/logout: 204 и 403, сессия и CSRF")
    void logoutOperation_isPostWith204() throws Exception {
        JsonNode logout = docs().at("/paths/~1api~1auth~1logout/post");
        assertFalse(logout.isMissingNode());
        assertTrue(logout.at("/responses/204").isObject());
        assertTrue(logout.at("/responses/403").isObject());
        assertTrue(logout.at("/security/0").has("sessionCookie") && logout.at("/security/0").has("csrfHeader"));
    }

    @Test
    @DisplayName("В документе только пути /api/**: нет /notes, /login, /logout, /search, /")
    void apiDocs_hasNoWebUiPaths() throws Exception {
        JsonNode paths = docs().get("paths");
        List<String> names = new ArrayList<>();
        paths.propertyNames().forEach(names::add);
        for (String name : names) {
            assertTrue(name.startsWith("/api/"), "путь вне /api: " + name);
        }
    }

    @Test
    @DisplayName("В документе нет значений из конфигурации, хешей и текста вроде bcrypt")
    void apiDocs_hasNoConfigValuesOrHashes() throws Exception {
        String text = docsText();
        assertFalse(text.contains("docs-secret-value-123"));
        assertFalse(text.contains("docs-user-name"));
        assertFalse(text.contains("$2a$"));
        assertFalse(text.contains("localhost:5432"));
        assertFalse(text.toLowerCase().contains("jdbc"));
    }

    @Test
    @DisplayName("Схемы ErrorResponse, NoteRequest, NoteResponse, FolderResponse есть; LoginRequest.password writeOnly")
    void apiDocs_hasSchemas() throws Exception {
        JsonNode schemas = docs().at("/components/schemas");
        for (String name : List.of("ErrorResponse", "NoteRequest", "NoteResponse", "FolderResponse",
                "LoginRequest", "UserResponse", "ImportResult", "HeadingsResult")) {
            assertTrue(schemas.has(name), "нет схемы " + name);
        }
        JsonNode password = schemas.at("/LoginRequest/properties/password");
        assertTrue(password.get("writeOnly").asBoolean());
        assertEquals("password", password.get("format").asString());
        assertTrue(schemas.at("/ErrorResponse/properties").has("fieldErrors"));
    }

    @Test
    @DisplayName("Схемы безопасности: cookie JSESSIONID и заголовок X-XSRF-TOKEN, в описании API есть вход и CSRF")
    void apiDocs_hasSecuritySchemes() throws Exception {
        JsonNode doc = docs();
        JsonNode session = doc.at("/components/securitySchemes/sessionCookie");
        assertEquals("apiKey", session.get("type").asString());
        assertEquals("cookie", session.get("in").asString());
        assertEquals("JSESSIONID", session.get("name").asString());
        JsonNode csrf = doc.at("/components/securitySchemes/csrfHeader");
        assertEquals("apiKey", csrf.get("type").asString());
        assertEquals("header", csrf.get("in").asString());
        assertEquals("X-XSRF-TOKEN", csrf.get("name").asString());

        String description = doc.at("/info/description").asString();
        assertTrue(description.contains("POST /api/auth/login"));
        assertTrue(description.contains("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("Небезопасные методы требуют сессию и CSRF вместе; вход только CSRF; GET только сессию")
    void apiDocs_securityRequirementsPerOperation() throws Exception {
        JsonNode doc = docs();
        JsonNode post = doc.at("/paths/~1api~1notes/post/security/0");
        assertTrue(post.has("sessionCookie") && post.has("csrfHeader"));
        JsonNode delete = doc.at("/paths/~1api~1notes~1{id}/delete/security/0");
        assertTrue(delete.has("sessionCookie") && delete.has("csrfHeader"));
        JsonNode login = doc.at("/paths/~1api~1auth~1login/post/security");
        assertEquals(1, login.size());
        assertTrue(login.get(0).has("csrfHeader"));
        assertFalse(login.get(0).has("sessionCookie"));
        // GET: требование задано глобально
        assertTrue(doc.at("/paths/~1api~1notes/get/security").isMissingNode());
        assertTrue(doc.at("/security/0").has("sessionCookie"));
    }

    @Test
    @DisplayName("Коды ответов: 401 везде кроме выхода, 403 у небезопасных, 404 у операций по id, 413 у импорта")
    void apiDocs_responseCodes() throws Exception {
        JsonNode paths = docs().get("paths");
        List<String> missing = new ArrayList<>();
        for (String path : paths.propertyNames()) {
            for (String method : paths.get(path).propertyNames()) {
                JsonNode responses = paths.get(path).get(method).get("responses");
                if (!path.equals("/api/auth/logout") && !responses.has("401")) {
                    missing.add(method + " " + path + " без 401");
                }
                if (!method.equals("get") && !responses.has("403")) {
                    missing.add(method + " " + path + " без 403");
                }
                if (path.contains("{id}") && !responses.has("404")) {
                    missing.add(method + " " + path + " без 404");
                }
            }
        }
        assertTrue(missing.isEmpty(), missing.toString());

        JsonNode imp = paths.at("/~1api~1import/post");
        assertTrue(imp.at("/responses/413").isObject());
        assertTrue(imp.at("/responses/400").isObject());
        assertTrue(imp.at("/requestBody/content").has("multipart/form-data"));
        assertEquals("#/components/schemas/ErrorResponse",
                imp.at("/responses/413/content/application~1json/schema/$ref").asString());
        assertEquals("#/components/schemas/ErrorResponse",
                paths.at("/~1api~1notes~1{id}/get/responses/401/content/application~1json/schema/$ref").asString());
    }

    @Test
    @DisplayName("Успешные ответы ссылаются на схемы DTO")
    void apiDocs_successResponsesUseDtoSchemas() throws Exception {
        JsonNode paths = docs().get("paths");
        assertEquals("#/components/schemas/NoteResponse",
                paths.at("/~1api~1notes~1{id}/get/responses/200/content/*~1*/schema/$ref").asString());
        assertEquals("#/components/schemas/FolderResponse",
                paths.at("/~1api~1folders/get/responses/200/content/*~1*/schema/items/$ref").asString());
    }
}
