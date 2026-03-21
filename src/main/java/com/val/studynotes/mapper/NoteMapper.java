package com.val.studynotes.mapper;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class NoteMapper {
    public Note toEntity(NoteRequest request) {
        Note note = new Note();
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        return note;
    }

    public NoteResponse toResponse(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setTitle(note.getTitle());
        response.setContent(note.getContent());
        response.setCreatedAt(note.getCreatedAt());
        response.setUpdatedAt(note.getUpdatedAt());
        Folder folder = note.getFolder();
        if (folder != null) {
            response.setFolderId(folder.getId());
            response.setFolderName(folder.getName());
            response.setFolderPath(buildFolderPath(folder));
        }
        return response;
    }

    public void updateEntity(Note existingNote, NoteRequest request) {
        existingNote.setTitle(request.getTitle());
        existingNote.setContent(request.getContent());
    }

    private String buildFolderPath(Folder folder) {
        List<String> parts = new ArrayList<>();
        Folder current = folder;
        while (current != null) {
            parts.add(current.getName());
            current = current.getParent();
        }
        Collections.reverse(parts);
        return String.join(" / ", parts);
    }
}
