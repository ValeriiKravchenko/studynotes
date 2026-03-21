package com.val.studynotes.service;

import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import org.springframework.stereotype.Service;

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
}
