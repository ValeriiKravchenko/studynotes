package com.val.studynotes.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "Итог импорта zip-архива")
public class ImportResult {
    private int total;
    private int imported;
    private int skipped;
    private int ignored;
    private List<String> errors;

    public ImportResult() {
        this.total = 0;
        this.imported = 0;
        this.skipped = 0;
        this.errors = new ArrayList<>();
    }

    public void incrementTotal() {
        this.total++;
    }

    public void incrementImported() {
        this.imported++;
    }

    public void incrementSkipped() {
        this.skipped++;
    }

    public void incrementIgnored() {
        this.ignored++;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public int getTotal() {
        return total;
    }

    public int getImported() {
        return imported;
    }

    public int getSkipped() {
        return skipped;
    }

    /** Записи архива, которые не являются .md и были проигнорированы (в total не входят). */
    public int getIgnored() {
        return ignored;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
