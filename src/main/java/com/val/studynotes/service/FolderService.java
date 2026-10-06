package com.val.studynotes.service;

import com.val.studynotes.dto.FolderResponse;
import com.val.studynotes.exception.FolderNotFoundException;
import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FolderService {
    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    public List<Folder> getRootFolders() {
        return folderRepository.findByParentIsNull();
    }

    /** Плоский список всех папок; заметки считаются одним запросом с группировкой. */
    @Transactional(readOnly = true)
    public List<FolderResponse> getAllFolders() {
        return folderRepository.findAllWithNoteCount().stream()
                .map(f -> new FolderResponse(f.getId(), f.getName(), f.getParentId(), f.getNoteCount()))
                .toList();
    }

    public Folder getById(Long id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException(id));
    }
}
