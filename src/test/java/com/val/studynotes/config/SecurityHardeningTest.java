package com.val.studynotes.config;

import com.val.studynotes.dto.HeadingsResult;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Добивка безопасности: SameSite, матчер /api/**, мусорные CSRF-значения, эндпоинты шага 6b. */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
class SecurityHardeningTest {

    private static final String JSON = "{\"title\":\"t\",\"content\":\"c\"}";
    private static final String PREVIEW = "{\"content\":\"# h\"}";

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

    private Cookie fetchXsrfCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/notes")).andReturn().getResponse();
        Cookie cookie = Arrays.stream(response.getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElse(null);
        assertNotNull(cookie);
        return cookie;
    }

    // ---------- SameSite ----------

    /**
     * MockHttpServletResponse не добавляет SameSite в текст заголовка Set-Cookie, поэтому проверяется
     * атрибут объекта Cookie; как Tomcat запишет его в заголовок, не проверено.
     */
    @Test
    @DisplayName("XSRF-TOKEN выставляется с атрибутом SameSite=Lax")
    void xsrfCookie_hasSameSiteLax() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/notes")).andReturn().getResponse();

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "нет cookie XSRF-TOKEN");
        assertEquals("Lax", cookie.getAttribute("SameSite"));
    }

    // ---------- матчер /api/** ----------

    @Test
    @DisplayName("GET /api без входа: 401 JSON")
    void apiRoot_unauthenticated_is401Json() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/ без входа: 401 JSON")
    void apiRootSlash_unauthenticated_is401Json() throws Exception {
        mockMvc.perform(get("/api/"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------- мусорные значения X-XSRF-TOKEN ----------

    @ParameterizedTest(name = "[{index}] значение ''{0}''")
    @ValueSource(strings = {
            "",
            "!!!not-base64@@@###",
            "abcd=",
            "abc==",
            "ab cd ef gh",
            " ",
            "%%%%",
    })
    @DisplayName("Мусорный X-XSRF-TOKEN для /api/**: 403 JSON, не 500")
    void garbageToken_is403Json(String garbage) throws Exception {
        Cookie cookie = fetchXsrfCookie();
        String body = mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", garbage)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Доступ запрещён"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(!garbage.isBlank() && body.contains(garbage));
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Очень длинный X-XSRF-TOKEN (1500 символов): 403 JSON, не 500")
    void veryLongToken_is403Json() throws Exception {
        Cookie cookie = fetchXsrfCookie();
        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", "A".repeat(1500))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Очень длинный не-base64 X-XSRF-TOKEN (1500 символов): 403 JSON, не 500")
    void veryLongNonBase64Token_is403Json() throws Exception {
        Cookie cookie = fetchXsrfCookie();
        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", "!".repeat(1500))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(noteService);
    }

    // ---------- замаскированный токен страницы в заголовке для /api/** ----------

    @Test
    @DisplayName("Замаскированный токен из мета-тега страницы в X-XSRF-TOKEN принимается на /api/**")
    void maskedMetaToken_acceptedForApi() throws Exception {
        NoteResponse note = new NoteResponse();
        note.setId(5L);
        note.setTitle("T");
        note.setContent("C");
        when(noteService.getNoteById(5L)).thenReturn(note);
        when(markdownService.renderSafe(any())).thenReturn(new HeadingsResult("<p>C</p>", List.of()));

        MockHttpServletResponse page = mockMvc.perform(get("/notes/5").with(user("any-user")))
                .andExpect(status().isOk()).andReturn().getResponse();
        Matcher m = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]*)\"").matcher(page.getContentAsString());
        assertTrue(m.find(), "нет мета-тега _csrf");
        String masked = m.group(1);
        Cookie cookie = Arrays.stream(page.getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN")).reduce((a, b) -> b).orElseThrow();
        assertNotEquals(cookie.getValue(), masked, "токен страницы должен быть замаскирован");

        NoteResponse created = new NoteResponse();
        created.setId(1L);
        when(noteService.createNote(any())).thenReturn(created);

        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", masked)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated());
    }

    // ---------- исключение безопасности на не-/api пути ----------

    @Test
    @DisplayName("AccessDeniedException из веб-контроллера (не /api): 403 стандартного обработчика, не 500")
    void webControllerAccessDenied_isNot500() throws Exception {
        when(noteService.getNoteById(5L)).thenThrow(new AccessDeniedException("secret-detail"));

        String body = mockMvc.perform(get("/notes/5").with(user("any-user")))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("secret-detail"));
    }

    // ---------- шаг 6b ----------

    @Test
    @DisplayName("GET /api/notes/{id}/render без входа: 401 JSON, сервисы не вызываются")
    void render_unauthenticated_is401() throws Exception {
        mockMvc.perform(get("/api/notes/1/render"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(noteService, markdownService);
    }

    @Test
    @DisplayName("POST /api/markdown/preview без входа: 401 JSON")
    void preview_unauthenticated_is401() throws Exception {
        // Токен берётся из реальной cookie, а не через csrf(): тот подменяет репозиторий токенов в общем кешированном контексте
        Cookie cookie = fetchXsrfCookie();
        mockMvc.perform(post("/api/markdown/preview").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(markdownService);
    }

    @Test
    @DisplayName("POST /api/markdown/preview со входом, но без CSRF: 403 JSON")
    void preview_withoutCsrf_is403() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").with(user("any-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(markdownService);
    }

    @Test
    @DisplayName("POST /api/markdown/preview со входом и верным токеном: 200")
    void preview_withValidToken_is200() throws Exception {
        when(markdownService.renderSafe("# h")).thenReturn(new HeadingsResult("<h1>h</h1>", List.of()));
        Cookie cookie = fetchXsrfCookie();

        mockMvc.perform(post("/api/markdown/preview").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(PREVIEW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<h1>h</h1>"));
    }

    @Test
    @DisplayName("GET /api/notes/{id}/render со входом: 200")
    void render_authenticated_is200() throws Exception {
        NoteResponse note = new NoteResponse();
        note.setId(1L);
        note.setContent("# h");
        when(noteService.getNoteById(1L)).thenReturn(note);
        when(markdownService.renderSafe("# h")).thenReturn(new HeadingsResult("<h1>h</h1>", List.of()));

        mockMvc.perform(get("/api/notes/1/render").with(user("any-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<h1>h</h1>"));
    }
}
