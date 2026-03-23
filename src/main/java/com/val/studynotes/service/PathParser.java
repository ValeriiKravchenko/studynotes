package com.val.studynotes.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PathParser {
    public List<String> extractFolderNames(Path root, Path file) {
        Path relativePath = root.relativize(file.getParent());
        if (relativePath.toString().isEmpty()) {
            return List.of();
        }
        return IntStream.range(0, relativePath.getNameCount())
                .mapToObj(i -> relativePath.getName(i).toString())
                .collect(Collectors.toList());
    }
}
