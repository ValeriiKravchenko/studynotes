package com.val.studynotes.config;

import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import com.val.studynotes.testsupport.RestoreCsrfTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Доступ к адресам вне /api: только GET и HEAD, остальные методы закрыты.
 * Учётные данные — вымышленные, заданы только для тестов.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
@ExtendWith(RestoreCsrfTokenRepository.class)
class SpaAccessTest {

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

    @Test
    @DisplayName("POST вне /api с CSRF, аноним: 403")
    void post_outsideApi_anonymous_isForbidden() throws Exception {
        mockMvc.perform(post("/notes/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT вне /api с CSRF, аноним: 403")
    void put_outsideApi_anonymous_isForbidden() throws Exception {
        mockMvc.perform(put("/notes/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE вне /api с CSRF, аноним: 403")
    void delete_outsideApi_anonymous_isForbidden() throws Exception {
        mockMvc.perform(delete("/notes/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST вне /api с CSRF, вошедший: 403")
    void post_outsideApi_authenticated_isForbidden() throws Exception {
        mockMvc.perform(post("/notes/1").with(user("any-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT вне /api с CSRF, вошедший: 403")
    void put_outsideApi_authenticated_isForbidden() throws Exception {
        mockMvc.perform(put("/notes/1").with(user("any-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE вне /api с CSRF, вошедший: 403")
    void delete_outsideApi_authenticated_isForbidden() throws Exception {
        mockMvc.perform(delete("/notes/1").with(user("any-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ERROR-диспетчеризация на /error для POST/PUT/DELETE не закрывается denyAll (не 403)")
    void errorDispatch_nonGetMethods_isNotForbidden() throws Exception {
        for (var method : new org.springframework.http.HttpMethod[] {
                org.springframework.http.HttpMethod.POST,
                org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.DELETE}) {
            mockMvc.perform(request(method, "/error").with(req -> {
                        req.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                        return req;
                    }).with(csrf()))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(
                            403, result.getResponse().getStatus(), method + " /error"));
        }
    }

    @Test
    @DisplayName("Прямой POST на /error (не ERROR-диспетчеризация), аноним: 403")
    void directPostToError_anonymous_isForbidden() throws Exception {
        mockMvc.perform(post("/error").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET неизвестного пути без точки и без api/ отдаёт index.html без входа")
    void get_unknownSpaRoute_returnsIndexHtml() throws Exception {
        mockMvc.perform(get("/some/unknown-route"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("HEAD неизвестного SPA-пути без входа: 200")
    void head_unknownSpaRoute_isOk() throws Exception {
        mockMvc.perform(head("/some/unknown-route")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/unknown без входа: 401 JSON")
    void get_apiUnknown_anonymous_is401Json() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/unknown со входом: 404 JSON")
    void get_apiUnknown_authenticated_is404Json() throws Exception {
        mockMvc.perform(get("/api/unknown").with(user("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api со входом: 404 JSON, а не index.html")
    void get_apiRoot_authenticated_is404Json() throws Exception {
        mockMvc.perform(get("/api").with(user("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}
