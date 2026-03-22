package com.val.studynotes.service;

import org.springframework.stereotype.Service;

@Service
public class TitleExtractor {

    public String extract(String content, String fallbackName) {
        if (content == null || content.isBlank()) {
            return fallbackName;
        }
        return content.lines()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .orElse(fallbackName);
    }
}
