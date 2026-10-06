package com.val.studynotes.controller;

import com.val.studynotes.config.SecurityConfig;
import com.val.studynotes.config.WebConfig;
import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.exception.ImportRejectedException;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static com.val.studynotes.service.ZipTestSupport.entries;
import static com.val.studynotes.service.ZipTestSupport.zip;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** POST /api/import поверх реальной цепочки Spring Security; ImportService замокан. */
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "app.security.username=test-user",
        "app.security.password=test-secret-123"
})
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportService importService;
    @MockitoBean
    private NoteService noteService;
    @MockitoBean
    private FolderService folderService;
    @MockitoBean
    private MarkdownService markdownService;

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "notes.zip", "application/zip", content);
    }

    @Test
    @DisplayName("без авторизации: 401 с JSON, сервис не вызывается")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/import").file(file(zip(entries("a.md", "# A")))).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("с авторизацией, но без CSRF: 403, сервис не вызывается")
    void withoutCsrf_forbidden() throws Exception {
        mockMvc.perform(multipart("/api/import").file(file(zip(entries("a.md", "# A")))).with(user("any-user")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("корректный zip: 200 и ImportResult в JSON")
    void validZip_returnsResult() throws Exception {
        ImportResult result = new ImportResult();
        result.incrementTotal();
        result.incrementImported();
        result.incrementIgnored();
        when(importService.importFromZip(any(InputStream.class))).thenReturn(result);

        mockMvc.perform(multipart("/api/import").file(file(zip(entries("a.md", "# A"))))
                        .with(user("any-user")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.ignored").value(1))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    @DisplayName("отсутствует поле file: 400 в формате ErrorResponse")
    void missingFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/import").file(new MockMultipartFile("other", "x".getBytes()))
                        .with(user("any-user")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Отсутствует обязательная часть запроса 'file'"));

        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("multipart без частей вообще: 400")
    void noParts_returns400() throws Exception {
        mockMvc.perform(multipart("/api/import").with(user("any-user")).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("пустой файл: 400, сервис не вызывается")
    void emptyFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/import").file(file(new byte[0])).with(user("any-user")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Файл пустой"));

        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("сервис отклонил архив (не zip, лимиты): 400 с его сообщением")
    void rejectedArchive_returns400WithMessage() throws Exception {
        when(importService.importFromZip(any(InputStream.class)))
                .thenThrow(new ImportRejectedException("В архиве больше 5000 записей"));

        mockMvc.perform(multipart("/api/import").file(file("not a zip".getBytes())).with(user("any-user")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("В архиве больше 5000 записей"));
    }

    @Test
    @DisplayName("не multipart-запрос (JSON): 415, сервис не вызывается")
    void nonMultipart_returns415() throws Exception {
        mockMvc.perform(post("/api/import").contentType("application/json").content("{}")
                        .with(user("any-user")).with(csrf()))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(importService);
    }
}
