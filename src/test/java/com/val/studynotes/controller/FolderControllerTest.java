package com.val.studynotes.controller;

import com.val.studynotes.dto.FolderResponse;
import com.val.studynotes.service.FolderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Слой MVC без Spring Security (addFilters = false), сервис замокан. */
@WebMvcTest(FolderController.class)
@AutoConfigureMockMvc(addFilters = false)
class FolderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderService folderService;

    @Test
    @DisplayName("GET /api/folders: папок нет — 200 и пустой массив")
    void getAll_empty_returnsEmptyArray() throws Exception {
        when(folderService.getAllFolders()).thenReturn(List.of());

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/folders: плоский список, у корня parentId = null")
    void getAll_returnsFlatList() throws Exception {
        when(folderService.getAllFolders()).thenReturn(List.of(
                new FolderResponse(1L, "Java", null, 2),
                new FolderResponse(2L, "Collections", 1L, 0)));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[0].parentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].noteCount").value(2))
                .andExpect(jsonPath("$[1].parentId").value(1))
                .andExpect(jsonPath("$[1].noteCount").value(0));
    }

    @Test
    @DisplayName("GET /api/folders: в JSON только id, name, parentId, noteCount (нет children, notes, parent)")
    void getAll_jsonShapeHasOnlyPrimitives() throws Exception {
        when(folderService.getAllFolders()).thenReturn(List.of(new FolderResponse(2L, "Collections", 1L, 3)));

        mockMvc.perform(get("/api/folders"))
                .andExpect(jsonPath("$[0].length()").value(4))
                .andExpect(jsonPath("$[0].children").doesNotExist())
                .andExpect(jsonPath("$[0].notes").doesNotExist())
                .andExpect(jsonPath("$[0].parent").doesNotExist());
    }

    @Test
    @DisplayName("POST, PUT, DELETE /api/folders: папки через API не создаются и не меняются (405)")
    void mutatingMethods_notSupported() throws Exception {
        mockMvc.perform(post("/api/folders")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/folders")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/folders")).andExpect(status().isMethodNotAllowed());
    }
}
