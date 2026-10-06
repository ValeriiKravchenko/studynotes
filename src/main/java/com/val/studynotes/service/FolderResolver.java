package com.val.studynotes.service;

import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Component
public class FolderResolver {
    /** Сколько раз повторяем «найти или создать», если папку параллельно создал другой импорт. */
    private static final int MAX_ATTEMPTS = 3;

    private final FolderRepository folderRepository;
    private final TransactionOperations createTransaction;

    @Autowired
    public FolderResolver(FolderRepository folderRepository, PlatformTransactionManager transactionManager) {
        this(folderRepository, new TransactionTemplate(transactionManager));
    }

    FolderResolver(FolderRepository folderRepository, TransactionOperations createTransaction) {
        this.folderRepository = folderRepository;
        this.createTransaction = createTransaction;
    }

    public Folder resolveFolder(List<String> folderNames) {
        if (folderNames.isEmpty()) {
            return null;
        }

        Folder parent = null;

        for (String name : folderNames) {
            parent = findOrCreate(name, parent);
        }
        return parent;
    }

    /**
     * Найти или создать папку. Вызывать нужно БЕЗ внешней транзакции: каждое создание идёт в собственной короткой
     * транзакции, поэтому нарушение уникального индекса откатывает только её. Два параллельных импорта могут
     * одновременно не найти папку и оба её создать: второй получит нарушение индекса, после чего папка
     * перечитывается (победитель уже закоммитил её; уровень изоляции по умолчанию READ COMMITTED).
     * Отдельную транзакцию REQUIRES_NEW не используем: она нужна была только под внешнюю транзакцию, которая
     * держала соединение, и при маленьком пуле параллельные импорты блокировали друг друга. Если вызвать метод
     * внутри внешней транзакции, создание присоединится к ней, и конфликт пометит её rollback-only.
     * Побочный эффект: созданная папка фиксируется сразу и остаётся, даже если импорт потом откатится.
     */
    private Folder findOrCreate(String name, Folder parent) {
        DataIntegrityViolationException conflict = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<Folder> existing = find(name, parent);
            if (existing.isPresent()) {
                return existing.get();
            }
            try {
                return createTransaction.execute(status -> folderRepository.save(new Folder(name, parent)));
            } catch (DataIntegrityViolationException e) {
                conflict = e;
            }
        }
        // Последнее перечитывание после конфликта; если папки всё равно нет, это не гонка, а другая ошибка данных
        Optional<Folder> existing = find(name, parent);
        if (existing.isPresent()) {
            return existing.get();
        }
        throw conflict;
    }

    private Optional<Folder> find(String name, Folder parent) {
        if (parent == null) {
            return folderRepository.findByNameAndParentIsNull(name);
        }
        return folderRepository.findByNameAndParent(name, parent);
    }
}
