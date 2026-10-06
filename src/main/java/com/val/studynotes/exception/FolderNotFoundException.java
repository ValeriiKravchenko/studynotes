package com.val.studynotes.exception;

public class FolderNotFoundException extends RuntimeException {
    private final Long folderId;

    public FolderNotFoundException(Long folderId) {
        super("Folder not found with id: " + folderId);
        this.folderId = folderId;
    }

    public Long getFolderId() {
        return folderId;
    }
}
