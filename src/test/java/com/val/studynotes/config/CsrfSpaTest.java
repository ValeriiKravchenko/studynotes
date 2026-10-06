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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
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

/**
 * CSRF для SPA (cookie XSRF-TOKEN + заголовок X-XSRF-TOKEN), JSON-ответы 401/403 для /api/**,
 * совместимость со страницами и htmx. Токены берутся из реальных ответов, а не конструируются.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
class CsrfSpaTest {

    private static final String JSON = "{\"title\":\"t\",\"content\":\"c\"}";

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

    private Cookie xsrfCookieFrom(MockHttpServletResponse response) {
        // При входе сначала приходит удаляющая cookie, затем новая; браузер применяет последнюю
        Cookie cookie = Arrays.stream(response.getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .reduce((first, last) -> last)
                .orElse(null);
        assertNotNull(cookie, "cookie XSRF-TOKEN не выставлена");
        assertFalse(cookie.getValue().isEmpty(), "cookie XSRF-TOKEN пустая (удалена)");
        return cookie;
    }

    private Cookie fetchXsrfCookie() throws Exception {
        return xsrfCookieFrom(mockMvc.perform(get("/api/notes")).andReturn().getResponse());
    }

    private static String metaContent(String html, String name) {
        Matcher m = Pattern.compile("<meta name=\"" + name + "\" content=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find(), "нет мета-тега " + name);
        return m.group(1);
    }

    private static String hiddenCsrf(String html) {
        Matcher m = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(m.find(), "нет скрытого поля _csrf");
        return m.group(1);
    }

    // ---------- 401 / 403 в JSON и cookie ----------

    @Test
    @DisplayName("GET /api/notes без входа: 401, и cookie XSRF-TOKEN выставлена и читается из JS")
    void unauthenticated_setsXsrfCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        Cookie cookie = xsrfCookieFrom(response);
        assertFalse(cookie.getValue().isBlank());
        assertFalse(cookie.isHttpOnly(), "XSRF-TOKEN должна читаться из JS");
        assertEquals("/", cookie.getPath());
        assertTrue(response.getHeaders("Set-Cookie").stream().noneMatch(h -> h.contains("JSESSIONID")));
    }

    @Test
    @DisplayName("401 для /api/**: JSON ErrorResponse с фиксированным сообщением, без fieldErrors")
    void unauthenticated_jsonBody() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("Вход без CSRF на /api/**: 403 с JSON, сервис не вызывается")
    void authenticatedWithoutCsrf_json403() throws Exception {
        mockMvc.perform(post("/api/notes").with(user("any-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Доступ запрещён"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Неверный X-XSRF-TOKEN: 403 с JSON, значение токена в ответ не попадает")
    void wrongToken_forbidden() throws Exception {
        Cookie cookie = fetchXsrfCookie();
        String bad = "wrong-token-value";
        String body = mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", bad)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Доступ запрещён"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(bad));
        assertFalse(body.contains(cookie.getValue()));
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Заголовок X-XSRF-TOKEN без cookie: 403")
    void headerWithoutCookie_forbidden() throws Exception {
        Cookie cookie = fetchXsrfCookie();
        mockMvc.perform(post("/api/notes").with(user("any-user"))
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Верный X-XSRF-TOKEN из cookie (сырое значение, как шлёт SPA): запрос проходит")
    void spaToken_allowed() throws Exception {
        Cookie cookie = fetchXsrfCookie();
        NoteResponse created = new NoteResponse();
        created.setId(1L);
        when(noteService.createNote(any())).thenReturn(created);

        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated());
    }

    // ---------- вход: ротация токена ----------

    @Test
    @DisplayName("Вход: токен и cookie обновляются, первый POST /api/** после входа проходит")
    void login_rotatesToken_thenPostWorks() throws Exception {
        Cookie before = fetchXsrfCookie();

        var loginResult = mockMvc.perform(post("/login").cookie(before)
                        .param("username", "test-user").param("password", "test-secret-123")
                        .param("_csrf", before.getValue()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/notes"))
                .andReturn();
        Cookie after = xsrfCookieFrom(loginResult.getResponse());
        assertNotEquals(before.getValue(), after.getValue(), "токен должен смениться после входа");
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        NoteResponse created = new NoteResponse();
        created.setId(1L);
        when(noteService.createNote(any())).thenReturn(created);

        mockMvc.perform(post("/api/notes").session(session).cookie(after)
                        .header("X-XSRF-TOKEN", after.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated());
    }

    // ---------- страницы и htmx ----------

    @Test
    @DisplayName("htmx: POST /notes/{id}/delete с токеном из мета-тега страницы (как в head.html) проходит")
    void htmxDelete_withMetaToken_allowed() throws Exception {
        NoteResponse note = new NoteResponse();
        note.setId(5L);
        note.setTitle("T");
        note.setContent("C");
        when(noteService.getNoteById(5L)).thenReturn(note);
        when(markdownService.renderSafe(any())).thenReturn(new HeadingsResult("<p>C</p>", List.of()));

        MockHttpServletResponse page = mockMvc.perform(get("/notes/5").with(user("any-user")))
                .andExpect(status().isOk()).andReturn().getResponse();
        String html = page.getContentAsString();
        String token = metaContent(html, "_csrf");
        String header = metaContent(html, "_csrf_header");
        Cookie cookie = xsrfCookieFrom(page);

        mockMvc.perform(post("/notes/5/delete").with(user("any-user")).cookie(cookie)
                        .header(header, token).header("HX-Request", "true"))
                .andExpect(status().isOk());
        verify(noteService).deleteNote(5L);
    }

    @Test
    @DisplayName("htmx: тот же запрос без токена: 403, удаления нет")
    void htmxDelete_withoutToken_forbidden() throws Exception {
        mockMvc.perform(post("/notes/5/delete").with(user("any-user")).header("HX-Request", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Форма: POST /notes с _csrf из скрытого поля страницы проходит")
    void form_withHiddenCsrf_allowed() throws Exception {
        MockHttpServletResponse page = mockMvc.perform(get("/notes/new").with(user("any-user")))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = hiddenCsrf(page.getContentAsString());
        Cookie cookie = xsrfCookieFrom(page);

        mockMvc.perform(post("/notes").with(user("any-user")).cookie(cookie)
                        .param("_csrf", token).param("title", "t").param("content", "c"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/notes"));
        verify(noteService).createNote(any());
    }

    @Test
    @DisplayName("Форма входа: POST /login с _csrf из страницы /login проходит")
    void loginForm_withHiddenCsrf_allowed() throws Exception {
        MockHttpServletResponse page = mockMvc.perform(get("/login"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = hiddenCsrf(page.getContentAsString());

        mockMvc.perform(post("/login").cookie(xsrfCookieFrom(page))
                        .param("_csrf", token)
                        .param("username", "test-user").param("password", "test-secret-123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/notes"));
    }

    // ---------- исключения безопасности из контроллера ----------

    @Test
    @DisplayName("AccessDeniedException из контроллера: 403 JSON, а не 500")
    void controllerAccessDenied_is403() throws Exception {
        when(noteService.getAllNotes()).thenThrow(new AccessDeniedException("secret-detail"));

        String body = mockMvc.perform(get("/api/notes").with(user("any-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Доступ запрещён"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("secret-detail"));
    }

    @Test
    @DisplayName("AuthenticationException из контроллера: 401 JSON, а не 500")
    void controllerAuthenticationException_is401() throws Exception {
        when(noteService.getAllNotes()).thenThrow(new BadCredentialsException("secret-detail"));

        String body = mockMvc.perform(get("/api/notes").with(user("any-user")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("secret-detail"));
    }

    @Test
    @DisplayName("Прочие исключения по-прежнему дают 500 без деталей")
    void otherException_stillGives500() throws Exception {
        when(noteService.getAllNotes()).thenThrow(new IllegalStateException("secret-detail"));

        String body = mockMvc.perform(get("/api/notes").with(user("any-user")))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("secret-detail"));
    }
}
