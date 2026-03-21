package com.val.studynotes.service;

import org.springframework.stereotype.Service;

@Service
public class TitleExtractor {

    public String extract(String content, String fallbackName) {
        if (content == null || content.isBlank()) {
            return fallbackName;
        }
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return fallbackName;
    }
}
