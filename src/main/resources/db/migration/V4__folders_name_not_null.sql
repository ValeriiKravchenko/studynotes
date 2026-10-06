-- Имя папки обязательно. Строки с NULL (если они есть) получают уникальное имя по id,
-- чтобы не нарушить уникальные индексы из V3, затем колонка становится NOT NULL.
UPDATE folders SET name = 'Без названия ' || id WHERE name IS NULL;
ALTER TABLE folders ALTER COLUMN name SET NOT NULL;
