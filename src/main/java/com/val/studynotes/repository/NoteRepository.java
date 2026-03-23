package com.val.studynotes.repository;

import com.val.studynotes.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    boolean existsByTitle(String title);

    List<Note> findByFolderId(Long folderId);

    List<Note> findByFolderIsNull();

    @Query(value = """
            SELECT * FROM notes
            WHERE to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(content, ''))
               @@ plainto_tsquery('russian', :query)
            ORDER BY ts_rank(
                to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(content, '')),
                plainto_tsquery('russian', :query)
            ) DESC
            """,
            nativeQuery = true)
    List<Note> fullTextSearch(@Param("query") String query);
}

