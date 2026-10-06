package com.val.studynotes.config;

import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Реальная цепочка Spring Security (SecurityConfig) поверх MVC-слоя; сервисы замоканы.
 * Учётные данные — вымышленные, заданы только для тестов.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
class SecurityConfigTest {

    private static final String JSON = "{\"title\":\"t\",\"content\":\"c\"}";

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.security.username}")
    private String validUsername;

    @Value("${app.security.password}")
    private String validPassword;

    @MockitoBean
    private NoteService noteService;
    @MockitoBean
    private ImportService importService;
    @MockitoBean
    private FolderService folderService;
    @MockitoBean
    private MarkdownService markdownService;

    // ---------- неавторизованный доступ (фиксируем текущее поведение) ----------

    @Test
    @DisplayName("GET /api/notes без авторизации: сейчас 302 на /login (не 401), сервис не вызывается")
    void unauthenticatedApiGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("GET /api/notes/{id} без авторизации: сейчас 302 на /login")
    void unauthenticatedApiGetById_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/notes/1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Мутирующие запросы /api/** без авторизации, но с CSRF: сейчас 302 на /login, данные не меняются")
    void unauthenticatedApiMutations_redirectToLogin() throws Exception {
        mockMvc.perform(post("/api/notes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(put("/api/notes/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(delete("/api/notes/1").with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Веб-страница /notes без авторизации: 302 на /login")
    void unauthenticatedWebPage_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/notes"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Страница /login и статика /css/** доступны без авторизации")
    void loginPageAndStaticAreOpen() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Авторизованный запрос к /api/notes проходит до контроллера")
    void authenticatedApiGet_isAllowed() throws Exception {
        NoteResponse n = new NoteResponse();
        n.setId(1L);
        n.setTitle("t");
        when(noteService.getAllNotes()).thenReturn(List.of(n));

        mockMvc.perform(get("/api/notes").with(user("any-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ---------- логин ----------

    @Test
    @DisplayName("Логин с верными данными: редирект на /notes, пользователь аутентифицирован")
    void login_validCredentials_redirectsToNotes() throws Exception {
        mockMvc.perform(formLogin("/login").user(validUsername).password(validPassword))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(authenticated().withUsername(validUsername));
    }

    @Test
    @DisplayName("Логин с неверным паролем: редирект на /login?error, не аутентифицирован")
    void login_wrongPassword_redirectsToError() throws Exception {
        mockMvc.perform(formLogin("/login").user(validUsername).password("wrong-" + validPassword))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Логин с неизвестным пользователем: редирект на /login?error")
    void login_unknownUser_redirectsToError() throws Exception {
        mockMvc.perform(formLogin("/login").user("nobody-" + validUsername).password(validPassword))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Логин после успешной аутентификации даёт доступ к /api/notes в той же сессии")
    void login_thenSessionGrantsAccess() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user(validUsername).password(validPassword))
                .andReturn().getRequest().getSession(false);

        when(noteService.getAllNotes()).thenReturn(List.of());
        mockMvc.perform(get("/api/notes").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /login без CSRF-токена: 403")
    void login_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", validUsername)
                        .param("password", validPassword))
                .andExpect(status().isForbidden());
    }

    // ---------- logout ----------

    @Test
    @DisplayName("Logout с CSRF: редирект на /login?logout, сессия инвалидирована")
    void logout_redirectsAndInvalidatesSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc
                .perform(formLogin("/login").user(validUsername).password(validPassword))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"));

        mockMvc.perform(get("/api/notes").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /logout без CSRF-токена: 403")
    void logout_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/logout").with(user("any-user")))
                .andExpect(status().isForbidden());
    }

    // ---------- CSRF для POST/PUT/DELETE ----------

    @Test
    @DisplayName("POST /api/notes без CSRF: 403, сервис не вызывается")
    void post_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/api/notes").with(user("any-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("PUT /api/notes/{id} без CSRF: 403, сервис не вызывается")
    void put_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(put("/api/notes/1").with(user("any-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} без CSRF: 403, сервис не вызывается")
    void delete_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/notes/1").with(user("any-user")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("Неверный CSRF-токен: 403")
    void invalidCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/notes/1").with(user("any-user")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(noteService);
    }

    @Test
    @DisplayName("POST /api/notes с CSRF: проходит (201)")
    void post_withCsrf_isAllowed() throws Exception {
        NoteResponse created = new NoteResponse();
        created.setId(1L);
        when(noteService.createNote(any())).thenReturn(created);

        mockMvc.perform(post("/api/notes").with(user("any-user")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/notes/{id} с CSRF: проходит (200)")
    void put_withCsrf_isAllowed() throws Exception {
        NoteResponse updated = new NoteResponse();
        updated.setId(1L);
        when(noteService.updateNote(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/notes/1").with(user("any-user")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/notes/{id} с CSRF: проходит (204)")
    void delete_withCsrf_isAllowed() throws Exception {
        mockMvc.perform(delete("/api/notes/1").with(user("any-user")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(noteService).deleteNote(1L);
    }

    @Test
    @DisplayName("GET не требует CSRF-токена")
    void get_doesNotRequireCsrf() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of());

        mockMvc.perform(get("/api/notes").with(user("any-user")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /import (старый импорт по пути) больше не существует: вошедший пользователь получает 404, сервис не вызывается")
    void oldPostImport_isGone() throws Exception {
        mockMvc.perform(post("/import").with(user("any-user")).with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(importService);
    }
}
