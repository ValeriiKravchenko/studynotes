package com.val.studynotes.service;

import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private FolderService folderService;

    @Test
    @DisplayName("getRootFolders: возвращает корневые папки из репозитория")
    void getRootFolders_returnsRootFolders() {
        Folder java = new Folder("Java");
        Folder sql = new Folder("SQL");
        when(folderRepository.findByParentIsNull()).thenReturn(List.of(java, sql));

        List<Folder> result = folderService.getRootFolders();

        assertEquals(List.of(java, sql), result);
        verify(folderRepository).findByParentIsNull();
    }

    @Test
    @DisplayName("getRootFolders: папок нет — пустой список")
    void getRootFolders_noFolders_returnsEmptyList() {
        when(folderRepository.findByParentIsNull()).thenReturn(List.of());

        assertTrue(folderService.getRootFolders().isEmpty());
    }
}
