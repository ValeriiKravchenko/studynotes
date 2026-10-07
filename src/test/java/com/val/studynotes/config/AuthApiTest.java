package com.val.studynotes.config;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Вход, выход и /me через API на реальной цепочке безопасности; токены берутся из реальных ответов. */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
class AuthApiTest {

    private static final String PASSWORD = "test-secret-123";

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

    private static Cookie xsrf(MockHttpServletResponse response) {
        // При ротации сначала приходит удаляющая cookie, затем новая; браузер применяет последнюю
        Cookie cookie = Arrays.stream(response.getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .reduce((first, last) -> last).orElse(null);
        assertNotNull(cookie, "cookie XSRF-TOKEN не выставлена");
        assertFalse(cookie.getValue().isEmpty(), "cookie XSRF-TOKEN пустая (удалена)");
        return cookie;
    }

    private Cookie freshXsrf() throws Exception {
        return xsrf(mockMvc.perform(get("/api/auth/me")).andReturn().getResponse());
    }

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private MockHttpServletRequestBuilder loginRequest(Cookie xsrf, String body) {
        return post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MvcResult login(Cookie xsrf) throws Exception {
        return mockMvc.perform(loginRequest(xsrf, loginJson("test-user", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
    }

    private static MockHttpSession session(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private static String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"\"");
    }

    // ---------- вход ----------

    @Test
    @DisplayName("Верные данные: 200, в теле только username, без пароля и хеша")
    void login_success_bodyHasOnlyUsername() throws Exception {
        Cookie x = freshXsrf();
        String body = mockMvc.perform(loginRequest(x, loginJson("test-user", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").value("test-user"))
                .andReturn().getResponse().getContentAsString();

        assertEquals("{\"username\":\"test-user\"}", body);
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains("$2"), "хеш bcrypt в теле");
        assertFalse(body.toLowerCase().contains("role"));
    }

    @Test
    @DisplayName("После входа в той же сессии GET /api/notes: 200")
    void login_thenProtectedApiWorks() throws Exception {
        MvcResult result = login(freshXsrf());

        mockMvc.perform(get("/api/notes").session(session(result)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Неверный пароль и неизвестный пользователь: 401, тела совпадают (кроме timestamp)")
    void login_badCredentials_identicalBodies() throws Exception {
        Cookie x = freshXsrf();
        MockHttpServletResponse wrongPassword = mockMvc.perform(loginRequest(x, loginJson("test-user", "wrong-pass")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        MockHttpServletResponse unknownUser = mockMvc.perform(loginRequest(x, loginJson("no-such-user", "wrong-pass")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        String a = wrongPassword.getContentAsString(StandardCharsets.UTF_8);
        String b = unknownUser.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(stripTimestamp(a), stripTimestamp(b));
        assertEquals(wrongPassword.getContentType(), unknownUser.getContentType());
        assertTrue(a.contains("\"status\":401"));
        assertFalse(a.contains("wrong-pass"));
        assertFalse(a.contains("no-such-user"));
        assertFalse(a.contains("test-user"));
        assertNull(wrongPassword.getHeader("WWW-Authenticate"));
    }

    @Test
    @DisplayName("Неудачный вход не создаёт вход: /api/auth/me после него 401")
    void login_failure_doesNotAuthenticate() throws Exception {
        Cookie x = freshXsrf();
        MvcResult result = mockMvc.perform(loginRequest(x, loginJson("test-user", "wrong-pass")))
                .andExpect(status().isUnauthorized()).andReturn();

        MockHttpSession s = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(s == null ? new MockHttpSession() : s))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Вход без CSRF: 403")
    void login_withoutCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("test-user", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Доступ запрещён"));
    }

    @Test
    @DisplayName("Вход с неверным CSRF-токеном: 403")
    void login_wrongCsrf_forbidden() throws Exception {
        Cookie x = freshXsrf();
        mockMvc.perform(post("/api/auth/login").cookie(x).header("X-XSRF-TOKEN", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson("test-user", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Пустой username: 400 с fieldErrors, пароль в ответ не попадает")
    void login_blankUsername_badRequest() throws Exception {
        Cookie x = freshXsrf();
        String body = mockMvc.perform(loginRequest(x, loginJson("", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(PASSWORD));
    }

    @Test
    @DisplayName("Пустой password: 400 с fieldErrors")
    void login_blankPassword_badRequest() throws Exception {
        Cookie x = freshXsrf();
        mockMvc.perform(loginRequest(x, loginJson("test-user", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    @DisplayName("Пробельные username и password и пустое тело: 400")
    void login_whitespaceAndMissingBody_badRequest() throws Exception {
        Cookie x = freshXsrf();
        mockMvc.perform(loginRequest(x, loginJson("   ", "   ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(loginRequest(x, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(loginRequest(x, "not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное тело запроса"));
    }

    @Test
    @DisplayName("LoginRequest.toString не содержит пароль")
    void loginRequest_toString_hidesPassword() {
        String text = new com.val.studynotes.dto.LoginRequest("u", "very-secret").toString();
        assertFalse(text.contains("very-secret"));
    }

    // ---------- сессия и CSRF при входе ----------

    @Test
    @DisplayName("Вход меняет идентификатор сессии, если она была")
    void login_changesSessionId() throws Exception {
        Cookie x = freshXsrf();
        MockHttpSession before = new MockHttpSession();
        String oldId = before.getId();

        MvcResult result = mockMvc.perform(loginRequest(x, loginJson("test-user", PASSWORD)).session(before))
                .andExpect(status().isOk()).andReturn();

        String newId = result.getRequest().getSession(false).getId();
        assertNotEquals(oldId, newId);
    }

    @Test
    @DisplayName("Вход меняет CSRF-токен: cookie приходит в ответе на вход, новый токен работает, старый нет")
    void login_rotatesCsrf_newWorksOldFails() throws Exception {
        Cookie before = freshXsrf();
        MvcResult result = login(before);

        // Главная проверка ротации: сам ответ на вход несёт Set-Cookie XSRF-TOKEN с непустым значением,
        // отличным от прежнего. Без ротации в ответе на вход этой cookie нет (браузер остался бы со старым токеном)
        List<String> xsrfHeaders = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("XSRF-TOKEN=")).toList();
        assertFalse(xsrfHeaders.isEmpty(), "ответ на вход не выставляет XSRF-TOKEN: токен не ротирован");
        String lastValue = xsrfHeaders.get(xsrfHeaders.size() - 1).split(";", 2)[0].substring("XSRF-TOKEN=".length());
        assertFalse(lastValue.isEmpty(), "последняя XSRF-TOKEN в ответе на вход пустая (удалена)");
        assertNotEquals(before.getValue(), lastValue, "значение токена не изменилось");

        Cookie after = xsrf(result.getResponse());
        assertEquals(lastValue, after.getValue());
        MockHttpSession s = session(result);

        // Токен хранится в cookie (double submit), сервер старое значение не помнит. Эта часть верна и без ротации:
        // она лишь проверяет, что значения не взаимозаменяемы (новая cookie со старым заголовком не проходит)
        mockMvc.perform(post("/api/auth/logout").session(s).cookie(after)
                        .header("X-XSRF-TOKEN", before.getValue()))
                .andExpect(status().isForbidden());

        // первый POST с новым токеном проходит
        mockMvc.perform(post("/api/auth/logout").session(s).cookie(after)
                        .header("X-XSRF-TOKEN", after.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Повторный вход уже вошедшего: 200, состояние не ломается, токен снова новый")
    void login_twice_keepsWorking() throws Exception {
        MvcResult first = login(freshXsrf());
        Cookie x1 = xsrf(first.getResponse());
        MockHttpSession s1 = session(first);

        MvcResult second = mockMvc.perform(loginRequest(x1, loginJson("test-user", PASSWORD)).session(s1))
                .andExpect(status().isOk()).andReturn();
        Cookie x2 = xsrf(second.getResponse());
        MockHttpSession s2 = session(second);
        assertNotEquals(x1.getValue(), x2.getValue());

        mockMvc.perform(get("/api/auth/me").session(s2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test-user"));
    }

    // ---------- /me ----------

    @Test
    @DisplayName("GET /api/auth/me: вошёл 200 с username, не вошёл 401")
    void me() throws Exception {
        MvcResult result = login(freshXsrf());
        String body = mockMvc.perform(get("/api/auth/me").session(session(result)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("{\"username\":\"test-user\"}", body);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"));
    }

    // ---------- logout ----------

    @Test
    @DisplayName("POST /api/auth/logout с CSRF: 204 без редиректа, JSESSIONID удаляется, сессия мертва")
    void apiLogout_success() throws Exception {
        MvcResult result = login(freshXsrf());
        Cookie x = xsrf(result.getResponse());
        MockHttpSession s = session(result);

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/logout").session(s).cookie(x)
                        .header("X-XSRF-TOKEN", x.getValue()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertNull(response.getHeader("Location"));
        assertEquals("", response.getContentAsString());
        assertTrue(s.isInvalid(), "сессия должна быть инвалидирована");
        Cookie jsession = Arrays.stream(response.getCookies())
                .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElse(null);
        assertNotNull(jsession, "cookie JSESSIONID должна быть удалена");
        assertEquals(0, jsession.getMaxAge());

        // старая сессия мертва
        mockMvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout без CSRF: 403, сессия остаётся рабочей")
    void apiLogout_withoutCsrf_forbidden() throws Exception {
        MvcResult result = login(freshXsrf());
        MockHttpSession s = session(result);

        mockMvc.perform(post("/api/auth/logout").session(s)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/auth/logout не выходит из системы")
    void apiLogout_getDoesNotLogout() throws Exception {
        MvcResult result = login(freshXsrf());
        MockHttpSession s = session(result);

        mockMvc.perform(get("/api/auth/logout").session(s));

        mockMvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Веб-logout /logout по-прежнему редиректит на /login?logout")
    void webLogout_stillRedirects() throws Exception {
        MvcResult result = login(freshXsrf());
        Cookie x = xsrf(result.getResponse());
        MockHttpSession s = session(result);

        mockMvc.perform(post("/logout").session(s).cookie(x).header("X-XSRF-TOKEN", x.getValue()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));
        assertTrue(s.isInvalid());
    }

    @Test
    @DisplayName("Веб-вход через форму /login по-прежнему редиректит на /notes")
    void webLogin_stillRedirects() throws Exception {
        Cookie x = freshXsrf();
        mockMvc.perform(post("/login").cookie(x).param("_csrf", x.getValue())
                        .param("username", "test-user").param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/notes"));
    }

    @Test
    @DisplayName("Выход без входа, но с CSRF: фиксируем фактическое поведение")
    void apiLogout_anonymous_behaviour() throws Exception {
        Cookie x = freshXsrf();
        int status = mockMvc.perform(post("/api/auth/logout").cookie(x).header("X-XSRF-TOKEN", x.getValue()))
                .andReturn().getResponse().getStatus();
        assertEquals(204, status);
    }

    @Test
    @DisplayName("Ответы входа не содержат пароль в заголовках и cookie")
    void login_noPasswordInHeaders() throws Exception {
        MockHttpServletResponse r = login(freshXsrf()).getResponse();
        List<String> all = r.getHeaderNames().stream()
                .flatMap(n -> r.getHeaders(n).stream()).toList();
        assertTrue(all.stream().noneMatch(h -> h.contains(PASSWORD)));
    }
}
