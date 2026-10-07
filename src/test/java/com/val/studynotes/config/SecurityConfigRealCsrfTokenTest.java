package com.val.studynotes.config;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Парные тесты к SecurityConfigTest: там мутирующие запросы идут через csrf() из spring-security-test, который
 * подставляет верный токен в обход пути cookie XSRF-TOKEN -> заголовок X-XSRF-TOKEN. Здесь токен берётся из реальной
 * cookie, как у SPA. csrf() и formLogin() (он применяет csrf() внутри) здесь не используются намеренно: csrf() подменяет
 * репозиторий токенов в общем кешированном контексте, после чего cookie перестаёт выдаваться (проверено в SecurityConfigTest
 * внутри одного класса). Лишнее свойство ниже даёт этому классу собственный контекст, чтобы порядок классов не влиял.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123",
        "test.context.isolation=real-csrf-token"
})
class SecurityConfigRealCsrfTokenTest {

    private static final String JSON = "{\"title\":\"t\",\"content\":\"c\"}";
    private static final String WRONG = "not-the-cookie-value";

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
        Cookie cookie = mockMvc.perform(get("/api/notes")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie, "cookie XSRF-TOKEN не выставлена");
        assertFalse(cookie.getValue().isEmpty());
        return cookie;
    }

    @Test
    @DisplayName("POST /api/notes с токеном из cookie в заголовке X-XSRF-TOKEN (как шлёт SPA): 201, с чужим значением: 403")
    void post_withRealCookieToken() throws Exception {
        NoteResponse created = new NoteResponse();
        created.setId(1L);
        when(noteService.createNote(any())).thenReturn(created);
        Cookie cookie = fetchXsrfCookie();

        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie).header("X-XSRF-TOKEN", WRONG)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
        verify(noteService, never()).createNote(any());

        mockMvc.perform(post("/api/notes").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с токеном из cookie: 200, с чужим значением: 403")
    void put_withRealCookieToken() throws Exception {
        NoteResponse updated = new NoteResponse();
        updated.setId(1L);
        when(noteService.updateNote(eq(1L), any())).thenReturn(updated);
        Cookie cookie = fetchXsrfCookie();

        mockMvc.perform(put("/api/notes/1").with(user("any-user")).cookie(cookie).header("X-XSRF-TOKEN", WRONG)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());
        verify(noteService, never()).updateNote(anyLong(), any());

        mockMvc.perform(put("/api/notes/1").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} с токеном из cookie: 204, с чужим значением: 403")
    void delete_withRealCookieToken() throws Exception {
        Cookie cookie = fetchXsrfCookie();

        mockMvc.perform(delete("/api/notes/1").with(user("any-user")).cookie(cookie).header("X-XSRF-TOKEN", WRONG))
                .andExpect(status().isForbidden());
        verify(noteService, never()).deleteNote(anyLong());

        mockMvc.perform(delete("/api/notes/1").with(user("any-user")).cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isNoContent());
        verify(noteService).deleteNote(1L);
    }

    @Test
    @DisplayName("Logout с токеном из cookie: редирект на /login?logout, сессия инвалидирована; с чужим значением: 403")
    void logout_withRealCookieToken() throws Exception {
        Cookie before = fetchXsrfCookie();
        String loginJson = "{\"username\":\"test-user\",\"password\":\"test-secret-123\"}";
        MvcResult login = mockMvc.perform(post("/api/auth/login").cookie(before)
                        .header("X-XSRF-TOKEN", before.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        // при входе сначала приходит удаляющая cookie, затем новая; берём последнюю непустую
        Cookie cookie = java.util.Arrays.stream(login.getResponse().getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN") && !c.getValue().isEmpty())
                .reduce((first, last) -> last).orElse(null);
        assertNotNull(cookie, "после входа нет действующей XSRF-TOKEN");

        mockMvc.perform(post("/logout").session(session).cookie(cookie).header("X-XSRF-TOKEN", WRONG))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notes").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/logout").session(session).cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(get("/api/notes").session(session)).andExpect(status().isUnauthorized());
    }
}
