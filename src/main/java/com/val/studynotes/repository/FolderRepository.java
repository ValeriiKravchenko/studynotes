package com.val.studynotes.repository;

import com.val.studynotes.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    Optional<Folder> findByNameAndParent(String name, Folder parent);

    Optional<Folder> findByNameAndParentIsNull(String name);

    List<Folder> findByParentIsNull();

    /** Все папки с числом прямых заметок за один проход (группировка), без N+1. */
    @Query("""
            select f.id as id, f.name as name, f.parent.id as parentId, count(n) as noteCount
            from Folder f left join f.notes n
            group by f.id, f.name, f.parent.id
            order by f.id
            """)
    List<FolderNoteCount> findAllWithNoteCount();
}
