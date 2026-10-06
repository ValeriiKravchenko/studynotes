package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import com.val.studynotes.exception.ImportRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.List;

@Service
public class ImportService {
    /** Лимиты защиты от zip-бомбы; считаются по реально прочитанным байтам, а не по заявленному размеру. */
    static final int MAX_ENTRIES = 5000;
    static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    /** Длина колонок notes.title и folders.name (VARCHAR(255)); для заголовка совпадает с @Size в NoteRequest. */
    static final int MAX_NAME_LENGTH = 255;

    private record MdEntry(String name, byte[] data) {
    }

    /** Заметка, прошедшая все проверки, не требующие записи в БД. */
    private record PreparedNote(String title, String content, List<String> folderNames) {
    }

    private final NoteRepository noteRepository;
    private final TitleExtractor titleExtractor;
    private final FolderResolver folderResolver;

    private final TransactionOperations notesTransaction;

    @Autowired
    public ImportService(NoteRepository noteRepository, TitleExtractor titleExtractor, FolderResolver folderResolver,
                         PlatformTransactionManager transactionManager) {
        this(noteRepository, titleExtractor, folderResolver, new TransactionTemplate(transactionManager));
    }

    ImportService(NoteRepository noteRepository, TitleExtractor titleExtractor, FolderResolver folderResolver,
                  TransactionOperations notesTransaction) {
        this.noteRepository = noteRepository;
        this.titleExtractor = titleExtractor;
        this.folderResolver = folderResolver;
        this.notesTransaction = notesTransaction;
    }

    /**
     * Импорт .md из zip. Архив целиком читается в память (на диск ничего не пишется), лимиты проверяются
     * до первого сохранения: при нарушении выбрасывается {@link ImportRejectedException} и ничего не импортируется.
     * <p>
     * Три этапа, внешней транзакции на весь метод нет (иначе она держала бы соединение, пока папки создаются
     * в отдельных транзакциях, и параллельные импорты упирались бы в пул соединений):
     * <ol>
     *   <li>разбор архива и все проверки, не требующие записи (длины 255, кодировка, дубликаты);</li>
     *   <li>создание нужных папок короткими отдельными транзакциями ({@link FolderResolver});</li>
     *   <li>одна транзакция только на сохранение заметок: всё или ничего для заметок.</li>
     * </ol>
     * Компромисс: если сохранение заметок откатилось, созданные на втором этапе папки остаются пустыми.
     * Файл с заголовком или именем папки длиннее 255 символов пропускается с записью в errors, остальные импортируются.
     */
    public ImportResult importFromZip(InputStream zipStream) {
        ImportResult result = new ImportResult();
        List<MdEntry> entries = readArchive(zipStream, result);
        if (entries.isEmpty()) {
            result.addError("В архиве нет .md файлов");
            return result;
        }
        List<PreparedNote> prepared = new ArrayList<>();
        Set<String> titlesInArchive = new HashSet<>();
        for (MdEntry entry : entries) {
            result.incrementTotal();
            PreparedNote note = prepare(entry, result, titlesInArchive);
            if (note != null) {
                prepared.add(note);
            }
        }

        List<Note> notes = new ArrayList<>();
        for (PreparedNote p : prepared) {
            Folder folder = folderResolver.resolveFolder(new ArrayList<>(p.folderNames()));
            Note note = new Note();
            note.setTitle(p.title());
            note.setContent(p.content());
            note.setFolder(folder);
            notes.add(note);
        }

        notesTransaction.executeWithoutResult(status -> notes.forEach(noteRepository::save));
        for (int i = 0; i < notes.size(); i++) {
            result.incrementImported();
        }
        return result;
    }

    private List<MdEntry> readArchive(InputStream zipStream, ImportResult result) {
        List<MdEntry> mdEntries = new ArrayList<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(zipStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    throw new ImportRejectedException("В архиве больше " + MAX_ENTRIES + " записей");
                }
                // Читаем каждую запись (в том числе не .md), чтобы считать реальный распакованный объём
                byte[] data = readBounded(zip, MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - totalBytes);
                totalBytes += data.length;
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".md")) {
                    result.incrementIgnored();
                    continue;
                }
                if (!isSafeName(name)) {
                    result.addError("Запись пропущена: небезопасное имя файла");
                    continue;
                }
                mdEntries.add(new MdEntry(name, data));
            }
        } catch (ZipException | IllegalArgumentException e) {
            // IllegalArgumentException: имя записи не в UTF-8
            throw new ImportRejectedException("Файл не является корректным zip-архивом");
        } catch (IOException e) {
            throw new ImportRejectedException("Не удалось прочитать zip-архив");
        }
        if (entryCount == 0) {
            throw new ImportRejectedException("Файл не является zip-архивом или архив пуст");
        }
        return mdEntries;
    }

    private static byte[] readBounded(InputStream in, long entryLimit, long totalRemaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long read = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            read += n;
            if (read > entryLimit) {
                throw new ImportRejectedException("Запись в архиве больше " + (entryLimit / (1024 * 1024)) + " МБ");
            }
            if (read > totalRemaining) {
                throw new ImportRejectedException(
                        "Суммарный размер распакованных данных больше " + (MAX_TOTAL_BYTES / (1024 * 1024)) + " МБ");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Имя безопасно, если это относительный путь через '/', без '..', обратных слэшей, диска и NUL. */
    static boolean isSafeName(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.indexOf('\0') >= 0
                || name.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** Проверки одной записи без записи в БД; null, если запись пропущена (причина уже в result). */
    private PreparedNote prepare(MdEntry entry, ImportResult result, Set<String> titlesInArchive) {
        String content;
        try {
            // Строгий UTF-8: некорректные байты дают ошибку, а не подмену символов на U+FFFD
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(entry.data()))
                    .toString();
        } catch (CharacterCodingException e) {
            result.addError("Не удалось прочитать файл " + lastSegment(entry.name()) + ": некорректная кодировка UTF-8");
            return null;
        }
        if (content.isBlank()) {
            result.incrementSkipped();
            return null;
        }
        String filename = lastSegment(entry.name());
        int dotIndex = filename.lastIndexOf(".");
        String fallbackName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String title = titleExtractor.extract(content, fallbackName);
        if (title.length() > MAX_NAME_LENGTH) {
            result.incrementSkipped();
            result.addError("Файл " + filename + " пропущен: заголовок длиннее " + MAX_NAME_LENGTH + " символов");
            return null;
        }
        if (noteRepository.existsByTitle(title) || titlesInArchive.contains(title)) {
            result.incrementSkipped();
            return null;
        }
        List<String> segments = new ArrayList<>(List.of(entry.name().split("/")));
        segments.removeIf(s -> s.isEmpty() || s.equals("."));
        List<String> folderNames = segments.subList(0, segments.size() - 1);
        for (String folderName : folderNames) {
            if (folderName.length() > MAX_NAME_LENGTH) {
                result.incrementSkipped();
                result.addError("Файл " + filename + " пропущен: имя папки длиннее " + MAX_NAME_LENGTH + " символов");
                return null;
            }
        }
        // Заголовок занят только после всех проверок: пропущенный файл не должен блокировать одноимённый следующий
        titlesInArchive.add(title);
        return new PreparedNote(title, content, new ArrayList<>(folderNames));
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
