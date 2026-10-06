package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import com.val.studynotes.support.PostgresSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.val.studynotes.service.ZipTestSupport.entries;
import static com.val.studynotes.service.ZipTestSupport.zip;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Настоящая гонка двух потоков за создание одной и той же папки на PostgreSQL
 * (уникальные индексы uq_folders_root_name и uq_folders_parent_name из V3).
 */
class FolderResolverRaceTest extends PostgresSpringBootTest {

    private static final int REPEATS = 10;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ImportService importService;

    /**
     * Репозиторий-обёртка: после каждого поиска, не нашедшего папку, поток ждёт на барьере второй поток.
     * Так оба потока гарантированно увидели «папки нет» до первой вставки, и гонка происходит на каждом прогоне,
     * а не по везению планировщика. Сами вставки и индексы настоящие.
     */
    private FolderRepository repositoryWithRaceWindow(CyclicBarrier barrier) {
        return (FolderRepository) Proxy.newProxyInstance(
                FolderRepository.class.getClassLoader(), new Class<?>[]{FolderRepository.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(folderRepository, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    boolean isLookup = method.getName().startsWith("findByNameAnd");
                    if (isLookup && result instanceof java.util.Optional<?> o && o.isEmpty()) {
                        barrier.await(10, TimeUnit.SECONDS);
                    }
                    return result;
                });
    }

    private <T> List<T> runTwoInParallel(Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<T> first = pool.submit(task);
            Future<T> second = pool.submit(task);
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(REPEATS)
    @DisplayName("гонка за корневую папку: оба потока получают одну и ту же папку, в БД одна строка")
    void raceOnRootFolder() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        FolderResolver resolver = new FolderResolver(repositoryWithRaceWindow(barrier), transactionManager);

        List<Folder> results = runTwoInParallel(() -> resolver.resolveFolder(List.of("Race root")));

        assertThat(results.get(0).getId()).isEqualTo(results.get(1).getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folders WHERE name = 'Race root'", Integer.class))
                .isEqualTo(1);
    }

    @RepeatedTest(REPEATS)
    @DisplayName("гонка за вложенную папку: родитель создан заранее, конфликт на uq_folders_parent_name")
    void raceOnNestedFolder() throws Exception {
        Folder parent = folderRepository.save(new Folder("Race parent"));
        CyclicBarrier barrier = new CyclicBarrier(2);
        FolderResolver resolver = new FolderResolver(repositoryWithRaceWindow(barrier), transactionManager);

        List<Folder> results = runTwoInParallel(() -> resolver.resolveFolder(List.of("Race parent", "Child")));

        assertThat(results.get(0).getId()).isEqualTo(results.get(1).getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM folders WHERE name = 'Child' AND parent_id = ?", Integer.class, parent.getId()))
                .isEqualTo(1);
    }

    @RepeatedTest(REPEATS)
    @DisplayName("два параллельных импорта с одной новой цепочкой папок: оба успешны, папки не дублируются")
    void parallelImportsWithSameNewFolders() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

        List<ImportResult> results = runTwoInParallel(() -> {
            int n = counter.incrementAndGet();
            byte[] archive = zip(entries("Shared/Deep/note" + n + ".md", "# Note " + n));
            start.await(10, TimeUnit.SECONDS);
            return importService.importFromZip(new ByteArrayInputStream(archive));
        });

        assertThat(results).allSatisfy(r -> {
            assertThat(r.getImported()).isEqualTo(1);
            assertThat(r.getErrors()).isEmpty();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folders", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notes", Integer.class)).isEqualTo(2);
    }
}
