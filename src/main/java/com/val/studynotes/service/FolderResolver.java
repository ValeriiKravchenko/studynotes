package com.val.studynotes.service;

import com.val.studynotes.model.Folder;
import com.val.studynotes.repository.FolderRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FolderResolver {
    private final FolderRepository folderRepository;

    public FolderResolver(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    public Folder resolveFolder(List<String> folderNames) {
        if (folderNames.isEmpty()) {
            return null;
        }

        Folder parent = null;

        for (String name : folderNames) {
            parent = findOrCreate(name, parent);
        }
        return parent;
    }

    private Folder findOrCreate(String name, Folder parent) {
        Folder existing;

        if (parent == null) {
            existing = folderRepository.findByNameAndParentIsNull(name).orElse(null);
        } else {
            existing = folderRepository.findByNameAndParent(name, parent).orElse(null);
        }

        if (existing != null) {
            return existing;
        }

        Folder newFolder = new Folder(name, parent);
        return folderRepository.save(newFolder);
    }
}
