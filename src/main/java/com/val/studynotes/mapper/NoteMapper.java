package com.val.studynotes.mapper;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.model.Note;
import org.springframework.stereotype.Component;

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
        return response;
    }

    public void updateEntity(Note existingNote, NoteRequest request) {
        existingNote.setTitle(request.getTitle());
        existingNote.setContent(request.getContent());
    }
}
