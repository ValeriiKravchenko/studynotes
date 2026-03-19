package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
}
