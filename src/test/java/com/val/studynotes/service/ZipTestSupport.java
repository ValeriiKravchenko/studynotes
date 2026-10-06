package com.val.studynotes.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Сборка zip-архивов в памяти для тестов; бинарных фикстур в репозитории нет. */
public final class ZipTestSupport {

    private ZipTestSupport() {
    }

    /** Порядок записей сохраняется; значение null означает запись-каталог (имя должно оканчиваться на '/'). */
    public static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) {
                    zos.write(e.getValue());
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, byte[]> entries(Object... nameAndContent) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            Object content = nameAndContent[i + 1];
            map.put((String) nameAndContent[i],
                    content instanceof String s ? text(s) : (byte[]) content);
        }
        return map;
    }
}
