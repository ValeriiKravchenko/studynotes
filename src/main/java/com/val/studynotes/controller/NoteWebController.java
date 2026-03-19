package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class NoteWebController {
    private final NoteService noteService;

    public NoteWebController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/notes")
    public String listNotes(Model model) {
        List<NoteResponse> notes = noteService.getAllNotes();
        model.addAttribute("notes", notes);
        return "note-list";
    }

    @GetMapping("/notes/{id}")
    public String viewNote(@PathVariable Long id, Model model) {
        NoteResponse note = noteService.getNoteById(id);
        model.addAttribute("note", note);
        return "note-view";
    }
}
