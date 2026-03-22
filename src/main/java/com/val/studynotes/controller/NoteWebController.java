package com.val.studynotes.controller;

import com.val.studynotes.dto.ImportResult;
import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.model.Folder;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.ImportService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class NoteWebController {
    private final NoteService noteService;
    private final ImportService importService;
    private final FolderService folderService;
    private final MarkdownService markdownService;

    public NoteWebController(NoteService noteService, ImportService importService, FolderService folderService, MarkdownService markdownService) {
        this.noteService = noteService;
        this.importService = importService;
        this.folderService = folderService;
        this.markdownService = markdownService;
    }

    @GetMapping("/notes")
    public String listNotes(@RequestParam(required = false) Long folderId, Model model) {
        List<NoteResponse> notes;
        if (folderId != null) {
            notes = noteService.getNotesByFolder(folderId);
        } else {
            notes = noteService.getAllNotes();
        }
        List<Folder> rootFolders = folderService.getRootFolders();
        model.addAttribute("notes", notes);
        model.addAttribute("folders", rootFolders);
        model.addAttribute("selectedFolderId", folderId);
        return "note-list";
    }

    @GetMapping("/notes/{id}")
    public String viewNote(@PathVariable Long id, Model model) {
        NoteResponse note = noteService.getNoteById(id);
        String renderedContent = markdownService.renderToHtml(note.getContent());
        model.addAttribute("note", note);
        model.addAttribute("renderedContent", renderedContent);
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

    @PostMapping("/notes/{id}/delete")
    public String deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return "redirect:/notes";
    }

    @GetMapping("/import")
    public String showImportForm() {
        return "import";
    }

    @PostMapping("/import")
    public String importNotes(@RequestParam String directoryPath, Model model) {
        ImportResult result = importService.importFromDirectory(directoryPath);
        model.addAttribute("result", result);
        model.addAttribute("directoryPath", directoryPath);
        return "import";
    }
}
