-- GIN-индекс для NoteRepository.fullTextSearch.
-- Выражение должно совпадать с запросом символ в символ, иначе индекс не используется.
CREATE INDEX idx_notes_fulltext ON notes
    USING GIN (to_tsvector('russian', coalesce(title, '') || ' ' || coalesce(content, '')));
