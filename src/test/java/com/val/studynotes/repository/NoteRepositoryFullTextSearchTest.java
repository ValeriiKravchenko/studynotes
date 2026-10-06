package com.val.studynotes.repository;

import com.val.studynotes.model.Note;
import com.val.studynotes.support.PostgresDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRepositoryFullTextSearchTest extends PostgresDataJpaTest {

    @Autowired
    private NoteRepository noteRepository;

    private Note save(String title, String content) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        return noteRepository.saveAndFlush(note);
    }

    @Test
    void findsByWordFromTitle() {
        save("Транзакции", "что-то про базы");
        save("Другая тема", "совсем не то");

        List<Note> result = noteRepository.fullTextSearch("Транзакции");

        assertThat(result).extracting(Note::getTitle).containsExactly("Транзакции");
    }

    @Test
    void findsByWordFromContent() {
        save("Заголовок", "Индексы ускоряют выборку");
        save("Другая тема", "совсем не то");

        List<Note> result = noteRepository.fullTextSearch("выборку");

        assertThat(result).extracting(Note::getTitle).containsExactly("Заголовок");
    }

    @Test
    void appliesRussianMorphology() {
        save("Одна заметка", "текст");
        save("Другое", "про собак");

        List<Note> result = noteRepository.fullTextSearch("заметки");

        assertThat(result).extracting(Note::getTitle).containsExactly("Одна заметка");
    }

    @Test
    void ordersByRelevance() {
        save("Мало", "индекс и много другого текста про разное и прочее");
        save("Много", "индекс индекс индекс");

        List<Note> result = noteRepository.fullTextSearch("индекс");

        assertThat(result).extracting(Note::getTitle).containsExactly("Много", "Мало");
    }

    @Test
    void blankQueryReturnsEmptyResult() {
        save("Транзакции", "текст");

        assertThat(noteRepository.fullTextSearch("")).isEmpty();
        assertThat(noteRepository.fullTextSearch("   ")).isEmpty();
    }

    @Test
    void nonMatchingQueryReturnsEmptyResult() {
        save("Транзакции", "текст");

        assertThat(noteRepository.fullTextSearch("квантовая")).isEmpty();
    }

    @Test
    void ignoresCase() {
        save("Транзакции", "Индексы");

        assertThat(noteRepository.fullTextSearch("ТРАНЗАКЦИИ")).hasSize(1);
        assertThat(noteRepository.fullTextSearch("индексы")).hasSize(1);
    }
}
