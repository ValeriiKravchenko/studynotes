package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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

    @GetMapping("/notes/new")
    public String showCreateForm(Model model) {
        model.addAttribute("noteRequest", new NoteRequest());
        return "note-form";
    }

    @PostMapping("/notes")
    public String createNote(@ModelAttribute NoteRequest noteRequest) {
        noteService.createNote(noteRequest);
        return "redirect:/notes";
    }

    @GetMapping("/notes/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        NoteResponse note = noteService.getNoteById(id);
        NoteRequest noteRequest = new NoteRequest();
        noteRequest.setTitle(note.getTitle());
        noteRequest.setContent(note.getContent());
        model.addAttribute("noteRequest", noteRequest);
        model.addAttribute("noteId", id);
        return "note-form";
    }

    @PostMapping("/notes/{id}")
    public String updateNote(@PathVariable Long id, @ModelAttribute NoteRequest noteRequest) {
        noteService.updateNote(id, noteRequest);
        return "redirect:/notes/" + id;
    }
}
