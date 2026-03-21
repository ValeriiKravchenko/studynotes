package com.val.studynotes.repository;

import com.val.studynotes.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    Optional<Folder> findByNameAndParent(String name, Folder parent);

    Optional<Folder> findByNameAndParentIsNull(String name);

    List<Folder> findByParentIsNull();
}
