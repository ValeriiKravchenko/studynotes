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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderResolverTest {

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private FolderResolver folderResolver;

    @Test
    @DisplayName("resolveFolder: пустой список имён — null, репозиторий не трогаем")
    void resolveFolder_emptyList_returnsNull() {
        assertNull(folderResolver.resolveFolder(List.of()));
        verifyNoInteractions(folderRepository);
    }

    @Test
    @DisplayName("resolveFolder: корневая папка существует — возвращается существующая, новая не создаётся")
    void resolveFolder_existingRoot_returnsExisting() {
        Folder existing = new Folder("Java");
        when(folderRepository.findByNameAndParentIsNull("Java")).thenReturn(Optional.of(existing));

        Folder result = folderResolver.resolveFolder(List.of("Java"));

        assertSame(existing, result);
        verify(folderRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolveFolder: корневой папки нет — создаётся и сохраняется без родителя")
    void resolveFolder_missingRoot_createsRootFolder() {
        when(folderRepository.findByNameAndParentIsNull("Java")).thenReturn(Optional.empty());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));

        Folder result = folderResolver.resolveFolder(List.of("Java"));

        assertEquals("Java", result.getName());
        assertNull(result.getParent());
        verify(folderRepository).save(result);
    }

    @Test
    @DisplayName("resolveFolder: вложенный путь, всё существует — возвращается самая глубокая папка")
    void resolveFolder_existingNestedPath_returnsDeepest() {
        Folder java = new Folder("Java");
        Folder collections = new Folder("Collections", java);
        when(folderRepository.findByNameAndParentIsNull("Java")).thenReturn(Optional.of(java));
        when(folderRepository.findByNameAndParent("Collections", java)).thenReturn(Optional.of(collections));

        Folder result = folderResolver.resolveFolder(List.of("Java", "Collections"));

        assertSame(collections, result);
        verify(folderRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolveFolder: вложенный путь, корень есть, дочерней нет — дочерняя создаётся с нужным родителем")
    void resolveFolder_missingChild_createsChildWithParent() {
        Folder java = new Folder("Java");
        when(folderRepository.findByNameAndParentIsNull("Java")).thenReturn(Optional.of(java));
        when(folderRepository.findByNameAndParent("Streams", java)).thenReturn(Optional.empty());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));

        Folder result = folderResolver.resolveFolder(List.of("Java", "Streams"));

        assertEquals("Streams", result.getName());
        assertSame(java, result.getParent());
    }

    @Test
    @DisplayName("resolveFolder: весь путь новый — создаются все уровни, цепочка родителей сохранена")
    void resolveFolder_allMissing_createsWholeChain() {
        when(folderRepository.findByNameAndParentIsNull("A")).thenReturn(Optional.empty());
        when(folderRepository.findByNameAndParent(eq("B"), any(Folder.class))).thenReturn(Optional.empty());
        when(folderRepository.findByNameAndParent(eq("C"), any(Folder.class))).thenReturn(Optional.empty());
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));

        Folder c = folderResolver.resolveFolder(List.of("A", "B", "C"));

        assertEquals("C", c.getName());
        assertEquals("B", c.getParent().getName());
        assertEquals("A", c.getParent().getParent().getName());
        assertNull(c.getParent().getParent().getParent());
        verify(folderRepository, times(3)).save(any(Folder.class));
    }
}
