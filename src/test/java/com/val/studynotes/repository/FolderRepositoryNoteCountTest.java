package com.val.studynotes.repository;

import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Запрос папок с подсчётом прямых заметок на настоящем PostgreSQL. */
class FolderRepositoryNoteCountTest extends PostgresDataJpaTest {

    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private NoteRepository noteRepository;

    private void saveNote(String title, Folder folder) {
        Note note = new Note();
        note.setTitle(title);
        note.setFolder(folder);
        noteRepository.save(note);
    }

    @Test
    void emptyDatabaseGivesEmptyList() {
        assertThat(folderRepository.findAllWithNoteCount()).isEmpty();
    }

    @Test
    void countsOnlyDirectNotesAndKeepsEmptyFolders() {
        Folder root = folderRepository.save(new Folder("Java"));
        Folder child = folderRepository.save(new Folder("Collections", root));
        Folder empty = folderRepository.save(new Folder("Пустая"));
        saveNote("a", root);
        saveNote("b", root);
        saveNote("c", child);
        saveNote("без папки", null);
        folderRepository.flush();
        noteRepository.flush();

        List<FolderNoteCount> result = folderRepository.findAllWithNoteCount();

        assertThat(result).extracting(FolderNoteCount::getId)
                .containsExactly(root.getId(), child.getId(), empty.getId());
        assertThat(result).extracting(FolderNoteCount::getName)
                .containsExactly("Java", "Collections", "Пустая");
        assertThat(result).extracting(FolderNoteCount::getParentId)
                .containsExactly(null, root.getId(), null);
        assertThat(result).extracting(FolderNoteCount::getNoteCount)
                .containsExactly(2L, 1L, 0L);
    }
}
