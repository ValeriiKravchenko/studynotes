package com.val.studynotes.repository;

/** Проекция запроса папок с количеством прямых заметок. */
public interface FolderNoteCount {
    Long getId();

    String getName();

    Long getParentId();

    long getNoteCount();
}
