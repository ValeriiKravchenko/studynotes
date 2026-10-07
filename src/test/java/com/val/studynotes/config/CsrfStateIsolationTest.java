package com.val.studynotes.config;

import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import com.val.studynotes.testsupport.RestoreCsrfTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Охрана от порчи общего контекста: после csrf() из spring-security-test в том же кешированном контексте
 * должна по-прежнему выдаваться cookie XSRF-TOKEN. Контекст намеренно тот же, что у SecurityConfigTest, CsrfSpaTest и др.
 * (без своего свойства изоляции). Без RestoreCsrfTokenRepository второй тест падает.
 */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
@ExtendWith(RestoreCsrfTokenRepository.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CsrfStateIsolationTest {

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
    @Order(1)
    @DisplayName("csrf() принимается: DELETE с авторизацией и токеном проходит")
    void first_withCsrfHelper() throws Exception {
        mockMvc.perform(delete("/api/notes/1").with(user("any-user")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(2)
    @DisplayName("после теста с csrf() cookie XSRF-TOKEN в общем контексте по-прежнему выдаётся")
    void second_afterCsrfHelper_cookieStillIssued() throws Exception {
        assertNotNull(mockMvc.perform(get("/api/notes")).andReturn().getResponse().getCookie("XSRF-TOKEN"),
                "cookie XSRF-TOKEN не выставлена: csrf() испортил общий контекст");
    }
}
