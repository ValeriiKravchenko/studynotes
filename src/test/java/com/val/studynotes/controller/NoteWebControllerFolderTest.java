package com.val.studynotes.controller;

import com.val.studynotes.dto.NoteRequest;
import com.val.studynotes.dto.NoteResponse;
import com.val.studynotes.mapper.NoteMapper;
import com.val.studynotes.model.Folder;
import com.val.studynotes.model.Note;
import com.val.studynotes.repository.FolderRepository;
import com.val.studynotes.repository.NoteRepository;
import com.val.studynotes.service.FolderService;
import com.val.studynotes.service.MarkdownService;
import com.val.studynotes.service.NoteService;
import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Регрессия: веб-форма не знает про папку, но редактирование заметки через Thymeleaf
 * не должно её стирать. Настоящие сервисы и PostgreSQL, контроллер вызывается напрямую.
 */
@Import({NoteService.class, FolderService.class, NoteMapper.class})
class NoteWebControllerFolderTest extends PostgresDataJpaTest {

    @Autowired
    private NoteService noteService;
    @Autowired
    private FolderService folderService;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private NoteRepository noteRepository;

    private NoteWebController controller() {
        return new NoteWebController(noteService, folderService, mock(MarkdownService.class));
    }

    @Test
    void editFormKeepsFolderOfNote() {
        Folder folder = folderRepository.save(new Folder("Java"));
        Note note = new Note();
        note.setTitle("Заметка");
        note.setContent("Текст");
        note.setFolder(folder);
        Long id = noteRepository.saveAndFlush(note).getId();

        Model model = new ExtendedModelMap();
        controller().showEditForm(id, model);

        NoteRequest form = (NoteRequest) model.getAttribute("noteRequest");
        assertThat(form.getFolderId()).isEqualTo(folder.getId());
    }

    @Test
    void editThroughWebFormDoesNotEraseFolder() {
        Folder root = folderRepository.save(new Folder("Java"));
        Folder folder = folderRepository.save(new Folder("Collections", root));
        Note note = new Note();
        note.setTitle("Старый заголовок");
        note.setContent("Старый текст");
        note.setFolder(folder);
        Long id = noteRepository.saveAndFlush(note).getId();

        // Форма: открыть, поменять только текст, отправить как приходит из браузера
        Model model = new ExtendedModelMap();
        NoteWebController controller = controller();
        controller.showEditForm(id, model);
        NoteRequest submitted = (NoteRequest) model.getAttribute("noteRequest");
        submitted.setTitle("Новый заголовок");
        submitted.setContent("Новый текст");
        controller.updateNote(id, submitted);
        noteRepository.flush();

        NoteResponse after = noteService.getNoteById(id);
        assertThat(after.getTitle()).isEqualTo("Новый заголовок");
        assertThat(after.getFolderId()).isEqualTo(folder.getId());
        assertThat(after.getFolderPath()).isEqualTo("Java / Collections");
    }

    @Test
    void editOfNoteWithoutFolderStaysWithoutFolder() {
        Note note = new Note();
        note.setTitle("Заметка");
        Long id = noteRepository.saveAndFlush(note).getId();

        Model model = new ExtendedModelMap();
        NoteWebController controller = controller();
        controller.showEditForm(id, model);
        NoteRequest submitted = (NoteRequest) model.getAttribute("noteRequest");
        controller.updateNote(id, submitted);

        assertThat(noteService.getNoteById(id).getFolderId()).isNull();
    }
}
