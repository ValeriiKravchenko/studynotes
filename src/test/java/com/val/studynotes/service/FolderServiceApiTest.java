package com.val.studynotes.service;

import com.val.studynotes.dto.FolderResponse;
import com.val.studynotes.exception.FolderNotFoundException;
import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderNoteCount;
import com.val.studynotes.repository.FolderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** getAllFolders и getById (getRootFolders покрыт в FolderServiceTest). */
@ExtendWith(MockitoExtension.class)
class FolderServiceApiTest {

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private FolderService folderService;

    private static FolderNoteCount row(Long id, String name, Long parentId, long noteCount) {
        return new FolderNoteCount() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Long getParentId() {
                return parentId;
            }

            @Override
            public long getNoteCount() {
                return noteCount;
            }
        };
    }

    @Test
    @DisplayName("getAllFolders: маппит строки запроса в FolderResponse с parentId и noteCount")
    void getAllFolders_mapsRows() {
        when(folderRepository.findAllWithNoteCount()).thenReturn(List.of(
                row(1L, "Java", null, 4),
                row(2L, "Collections", 1L, 0)));

        List<FolderResponse> result = folderService.getAllFolders();

        assertEquals(List.of(
                new FolderResponse(1L, "Java", null, 4),
                new FolderResponse(2L, "Collections", 1L, 0)), result);
        verify(folderRepository).findAllWithNoteCount();
    }

    @Test
    @DisplayName("getAllFolders: папок нет — пустой список")
    void getAllFolders_empty() {
        when(folderRepository.findAllWithNoteCount()).thenReturn(List.of());

        assertTrue(folderService.getAllFolders().isEmpty());
    }

    @Test
    @DisplayName("getById: папка найдена — возвращает её")
    void getById_found() {
        Folder folder = new Folder("Java");
        when(folderRepository.findById(5L)).thenReturn(Optional.of(folder));

        assertSame(folder, folderService.getById(5L));
    }

    @Test
    @DisplayName("getById: папки нет — FolderNotFoundException с id")
    void getById_notFound() {
        when(folderRepository.findById(999L)).thenReturn(Optional.empty());

        FolderNotFoundException ex = assertThrows(FolderNotFoundException.class, () -> folderService.getById(999L));

        assertEquals(999L, ex.getFolderId());
    }
}
