package com.val.studynotes.service;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.exception.FolderNotFoundException;
import com.val.studynotes.exception.InvalidReferenceException;
import com.val.studynotes.exception.NoteNotFoundException;
import com.val.studynotes.mapper.NoteMapper;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.NoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NoteService {
    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;
    private final FolderService folderService;

    public NoteService(NoteRepository noteRepository, NoteMapper noteMapper, FolderService folderService) {
        this.noteRepository = noteRepository;
        this.noteMapper = noteMapper;
        this.folderService = folderService;
    }

    public List<NoteResponse> getAllNotes() {
        return noteRepository.findAll()
                .stream()
                .map(noteMapper::toResponse)
                .collect(Collectors.toList());
    }

    public NoteResponse getNoteById(Long id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException(id));
        return noteMapper.toResponse(note);
    }

    public NoteResponse createNote(NoteRequest request) {
        Note note = noteMapper.toEntity(request);
        note.setFolder(resolveFolder(request.getFolderId()));
        Note saved = noteRepository.save(note);
        return noteMapper.toResponse(saved);
    }

    public NoteResponse updateNote(Long id, NoteRequest request) {
        Note existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException(id));
        noteMapper.updateEntity(existingNote, request);
        // PUT заменяет заметку целиком: folderId = null снимает папку
        existingNote.setFolder(resolveFolder(request.getFolderId()));
        Note saved = noteRepository.save(existingNote);
        return noteMapper.toResponse(saved);
    }

    private Folder resolveFolder(Long folderId) {
        if (folderId == null) {
            return null;
        }
        try {
            return folderService.getById(folderId);
        } catch (FolderNotFoundException e) {
            throw new InvalidReferenceException("folderId", "Папка не найдена");
        }
    }

    public void deleteNote(Long id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException(id));
        noteRepository.delete(note);
    }

    public List<NoteResponse> getNotesByFolder(Long folderId) {
        List<Note> notes;
        if (folderId == null) {
            notes = noteRepository.findByFolderIsNull();
        } else {
            notes = noteRepository.findByFolderId(folderId);
        }
        return notes.stream()
                .map(noteMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<NoteResponse> searchNotes(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return noteRepository.fullTextSearch(query.trim())
                .stream()
                .map(noteMapper::toResponse)
                .collect(Collectors.toList());
    }

    public long countByFolder(Long folderId) {
        return noteRepository.countByFolderId(folderId);
    }

    public long countAll() {
        return noteRepository.count();
    }
}
