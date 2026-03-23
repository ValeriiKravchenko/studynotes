package com.val.studynotes.dto;

import java.util.List;

public class NoteWithHeadings {
    private final NoteResponse note;
    private final List<HeadingInfo> headings;

    public NoteWithHeadings(NoteResponse note, List<HeadingInfo> headings) {
        this.note = note;
        this.headings = headings;
    }

    public NoteResponse getNote() {
        return note;
    }

    public List<HeadingInfo> getHeadings() {
        return headings;
    }
}
