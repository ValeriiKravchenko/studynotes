package com.val.studynotes.repository;

import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Частичные уникальные индексы из V3. Нарушение ограничения обрывает транзакцию теста,
 * поэтому каждый отклоняемый дубль проверяется в отдельном тесте и последним действием.
 */
class FolderUniquenessTest extends PostgresDataJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long insertFolder(String name, Long parentId) {
        return jdbc.queryForObject(
                "INSERT INTO folders (name, parent_id) VALUES (?, ?) RETURNING id", Long.class, name, parentId);
    }

    @Test
    void duplicateNameInSameParentIsRejected() {
        long parent = insertFolder("Базы данных", null);
        insertFolder("Лекции", parent);

        assertThatThrownBy(() -> insertFolder("Лекции", parent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateRootNamesAreRejected() {
        insertFolder("Базы данных", null);

        assertThatThrownBy(() -> insertFolder("Базы данных", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameNameInDifferentParentsIsAllowed() {
        long first = insertFolder("Базы данных", null);
        long second = insertFolder("Алгоритмы", null);

        insertFolder("Лекции", first);
        insertFolder("Лекции", second);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM folders WHERE name = 'Лекции'", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void rootAndNestedFolderMayShareName() {
        long root = insertFolder("Лекции", null);

        insertFolder("Лекции", root);

        Integer count = jdbc.queryForObject("SELECT count(*) FROM folders WHERE name = 'Лекции'", Integer.class);
        assertThat(count).isEqualTo(2);
    }
}
