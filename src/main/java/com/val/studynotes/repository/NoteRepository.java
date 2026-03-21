package com.val.studynotes.repository;

import com.val.studynotes.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    boolean existsByTitle(String title);

    List<Note> findByFolderId(Long folderId);

    List<Note> findByFolderIsNull();
}
