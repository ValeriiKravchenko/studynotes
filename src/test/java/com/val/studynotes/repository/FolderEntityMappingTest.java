package com.val.studynotes.repository;

import com.val.studynotes.model.Folder;
import com.val.studynotes.support.PostgresDataJpaTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отображение сущности должно совпадать со схемой: после V4 folders.name NOT NULL.
 * ddl-auto=validate такое расхождение не ловит, поэтому смотрим метаданные Hibernate.
 */
class FolderEntityMappingTest extends PostgresDataJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Folder.name в метаданных Hibernate обязательное (nullable = false)")
    void folderName_isMappedAsNotNull() {
        SingularAttribute<? super Folder, ?> name = entityManager.getMetamodel()
                .entity(Folder.class).getSingularAttribute("name");

        assertThat(name.isOptional()).isFalse();
    }
}
