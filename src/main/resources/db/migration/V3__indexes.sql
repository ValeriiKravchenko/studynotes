-- Индексы под внешние ключи и поиск по названию.
-- PostgreSQL не индексирует столбцы внешних ключей сам.
CREATE INDEX idx_notes_folder_id ON notes (folder_id);
CREATE INDEX idx_folders_parent_id ON folders (parent_id);
CREATE INDEX idx_notes_title ON notes (title);

-- Имя папки уникально в пределах родителя. Для корня (parent_id IS NULL) нужен отдельный индекс,
-- потому что NULL в обычном уникальном индексе считается различными значениями.
CREATE UNIQUE INDEX uq_folders_parent_name ON folders (parent_id, name) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_folders_root_name ON folders (name) WHERE parent_id IS NULL;
