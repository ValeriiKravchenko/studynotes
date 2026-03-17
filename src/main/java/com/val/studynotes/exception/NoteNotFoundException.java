package com.val.studynotes.exception;

public class NoteNotFoundException extends RuntimeException {
    private final Long noteId;

    public NoteNotFoundException(Long noteId) {
        super("Note not found with id: " + noteId);
        this.noteId = noteId;
    }

    public Long getNoteId() {
        return noteId;
    }
}
