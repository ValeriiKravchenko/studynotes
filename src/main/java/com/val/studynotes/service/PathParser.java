package com.val.studynotes.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PathParser {
    public List<String> extractFolderNames(Path root, Path file) {
        Path relativePath = root.relativize(file.getParent());
        if (relativePath.toString().isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < relativePath.getNameCount(); i++) {
            result.add(relativePath.getName(i).toString());
        }
        return result;
    }
}
