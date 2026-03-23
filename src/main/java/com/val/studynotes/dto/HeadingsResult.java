package com.val.studynotes.dto;

import java.util.List;

public record HeadingsResult(String html, List<HeadingInfo> headings) {
}
