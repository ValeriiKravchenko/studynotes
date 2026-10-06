package com.val.studynotes.service;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import com.val.studynotes.exception.ImportRejectedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private record MdEntry(String name, byte[] data) {
    }

    private final NoteRepository noteRepository;
    private final TitleExtractor titleExtractor;
    private final FolderResolver folderResolver;

    public ImportService(NoteRepository noteRepository, TitleExtractor titleExtractor, FolderResolver folderResolver) {
        this.noteRepository = noteRepository;
        this.titleExtractor = titleExtractor;
        this.folderResolver = folderResolver;
    }

    /**
     * Импорт .md из zip. Архив целиком читается в память (на диск ничего не пишется), лимиты проверяются
     * до первого сохранения: при нарушении выбрасывается {@link ImportRejectedException} и ничего не импортируется.
     */
    public ImportResult importFromZip(InputStream zipStream) {
        ImportResult result = new ImportResult();
        List<MdEntry> entries = readArchive(zipStream, result);
        if (entries.isEmpty()) {
            result.addError("В архиве нет .md файлов");
            return result;
        }
        for (MdEntry entry : entries) {
            result.incrementTotal();
            processZipEntry(entry, result);
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

    private void processZipEntry(MdEntry entry, ImportResult result) {
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
            return;
        }
        if (content.isBlank()) {
            result.incrementSkipped();
            return;
        }
        String filename = lastSegment(entry.name());
        int dotIndex = filename.lastIndexOf(".");
        String fallbackName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String title = titleExtractor.extract(content, fallbackName);
        if (noteRepository.existsByTitle(title)) {
            result.incrementSkipped();
            return;
        }
        List<String> segments = new ArrayList<>(List.of(entry.name().split("/")));
        segments.removeIf(s -> s.isEmpty() || s.equals("."));
        List<String> folderNames = segments.subList(0, segments.size() - 1);
        Folder folder = folderResolver.resolveFolder(new ArrayList<>(folderNames));
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setFolder(folder);
        noteRepository.save(note);
        result.incrementImported();
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
