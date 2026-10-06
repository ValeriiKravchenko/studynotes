package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.NoteNotFoundException;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Шаг 6b: рендер через API с настоящим MarkdownService. Проверки доступа добавятся после шага 7. */
@WebMvcTest(MarkdownController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MarkdownService.class)
class MarkdownControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    private void stubNote(long id, String content) {
        NoteResponse r = new NoteResponse();
        r.setId(id);
        r.setContent(content);
        when(noteService.getNoteById(id)).thenReturn(r);
    }

    private static String json(String content) {
        return "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    // ---- GET /api/notes/{id}/render ----

    @Test
    @DisplayName("GET render: html и headings в JSON")
    void render_returnsHtmlAndHeadings() throws Exception {
        stubNote(1L, "## Intro\n\ntext **bold**\n\n### Sub");

        mockMvc.perform(get("/api/notes/1/render"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.html", containsString("<strong>bold</strong>")))
                .andExpect(jsonPath("$.headings.length()").value(2))
                .andExpect(jsonPath("$.headings[0].level").value(2))
                .andExpect(jsonPath("$.headings[0].text").value("Intro"))
                .andExpect(jsonPath("$.headings[0].slug").isNotEmpty())
                .andExpect(jsonPath("$.headings[1].level").value(3));
    }

    @Test
    @DisplayName("GET render: несуществующая заметка → 404 в формате ErrorResponse")
    void render_notFound() throws Exception {
        when(noteService.getNoteById(99L)).thenThrow(new NoteNotFoundException(99L));

        mockMvc.perform(get("/api/notes/99/render"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET render: <script> в заметке не попадает в html")
    void render_stripsScript() throws Exception {
        stubNote(2L, "text\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>");

        mockMvc.perform(get("/api/notes/2/render"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("<script"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("onerror"))));
    }

    @Test
    @DisplayName("GET render: заметка без содержимого → пустой html и headings")
    void render_nullContent() throws Exception {
        stubNote(3L, null);

        mockMvc.perform(get("/api/notes/3/render"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(""))
                .andExpect(jsonPath("$.headings.length()").value(0));
    }

    // ---- POST /api/markdown/preview ----

    @Test
    @DisplayName("POST preview: пустой content → 200 и пустой html")
    void preview_emptyContent() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(""))
                .andExpect(jsonPath("$.headings.length()").value(0));
    }

    @Test
    @DisplayName("POST preview: нет поля → 400 с fieldErrors по content")
    void preview_missingField() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    @DisplayName("POST preview: content null → 400")
    void preview_nullField() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    @DisplayName("POST preview: 200000 символов допустимо, 200001 → 400")
    void preview_lengthLimit() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json("a".repeat(200000))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json("a".repeat(200001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
    }

    @Test
    @DisplayName("POST preview: XSS-нагрузка очищается")
    void preview_stripsXss() throws Exception {
        String payload = "<script>alert(1)</script><img src=x onerror=alert(1)>"
                + "<a href=\"javascript:alert(1)\" onclick=\"x()\">l</a><iframe src=\"//e\"></iframe>"
                + "<svg onload=alert(1)></svg>";

        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("<script"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("<iframe"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("<svg"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("onerror"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("onclick"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("onload"))))
                .andExpect(jsonPath("$.html", not(containsStringIgnoringCase("javascript:"))));
    }

    @Test
    @DisplayName("POST preview: заголовки попадают в headings")
    void preview_headings() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json("## One\n\n### Two\n\ntext")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headings.length()").value(2))
                .andExpect(jsonPath("$.headings[0].text").value("One"))
                .andExpect(jsonPath("$.headings[1].level").value(3))
                .andExpect(jsonPath("$.html", containsString("One")));
    }

    // ---- форма JSON ----

    @Test
    @DisplayName("Форма JSON: ровно html и headings; у заголовка ровно level, text, slug")
    void jsonShape() throws Exception {
        mockMvc.perform(post("/api/markdown/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json("## One")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.html").exists())
                .andExpect(jsonPath("$.headings").isArray())
                .andExpect(jsonPath("$.headings[0].*", hasSize(3)))
                .andExpect(jsonPath("$.headings[0].level").exists())
                .andExpect(jsonPath("$.headings[0].text").exists())
                .andExpect(jsonPath("$.headings[0].slug").exists());
    }
}
