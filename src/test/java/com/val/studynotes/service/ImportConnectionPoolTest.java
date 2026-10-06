package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.support.PostgresSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.val.studynotes.service.ZipTestSupport.entries;
import static com.val.studynotes.service.ZipTestSupport.zip;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Параллельные импорты с новыми папками при маленьком пуле соединений. Импорт не должен держать
 * одно соединение и одновременно просить второе: иначе все потоки занимают весь пул и ждут друг друга.
 * Отдельный контекст Spring со своими свойствами пула; остальные тесты его не затрагивает.
 */
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=5000"
})
class ImportConnectionPoolTest extends PostgresSpringBootTest {

    private static final int REPEATS = 10;
    private static final int THREADS = 4;

    @Autowired
    private ImportService importService;

    @RepeatedTest(REPEATS)
    @DisplayName("пул из 2 соединений, 4 параллельных импорта с новыми папками: все успешны, без ошибок пула")
    void parallelImportsWithNewFolders_doNotExhaustPool() throws Exception {
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<ImportResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                futures.add(executor.submit(() -> {
                    // у каждого потока свои новые папки и одна общая цепочка на всех
                    byte[] archive = zip(entries(
                            "Own" + n + "/Deep/a" + n + ".md", "# Own " + n,
                            "Shared/Deep/b" + n + ".md", "# Shared " + n));
                    start.await(10, TimeUnit.SECONDS);
                    return importService.importFromZip(new ByteArrayInputStream(archive));
                }));
            }
            for (Future<ImportResult> future : futures) {
                ImportResult result = future.get(60, TimeUnit.SECONDS);
                assertThat(result.getImported()).isEqualTo(2);
                assertThat(result.getErrors()).isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notes", Integer.class)).isEqualTo(THREADS * 2);
        // Own0..Own3 с Deep, плюс Shared с Deep
        assertThat(jdbc.queryForObject("SELECT count(*) FROM folders", Integer.class)).isEqualTo((THREADS + 1) * 2);
    }
}
