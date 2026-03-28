# StudyNotes

Персональная система для работы с IT-заметками. Импорт из Obsidian, полнотекстовый поиск, рендеринг Markdown с оглавлением и коллаутами, живой интерфейс без перезагрузки страницы.

**Демо:** [https://valnotes.com](https://valnotes.com)

---

## О проекте

StudyNotes решает конкретную проблему: вместо поиска по файлам и повторных запросов — набрал тему, получил все свои конспекты в одном месте. Приложение импортирует `.md` файлы из Obsidian, сохраняя структуру папок, рендерит Markdown в HTML и предоставляет полнотекстовый поиск по всей базе заметок.

---

## Возможности

- **Импорт из Obsidian** — указываешь путь к папке с `.md` файлами, приложение импортирует заметки с сохранением иерархии папок. Дубликаты пропускаются, заголовок извлекается из `# H1` или из имени файла.
- **Markdown-рендеринг** — заголовки, списки, таблицы, блоки кода с подсветкой синтаксиса (Prism.js), чек-листы, зачёркнутый текст.
- **Коллауты Obsidian** — поддержка `[!note]`, `[!tip]`, `[!warning]`, `[!danger]`, `[!info]` с возможностью сворачивания.
- **Оглавление** — автоматическая генерация из H2/H3 заголовков с якорными ссылками.
- **Полнотекстовый поиск** — PostgreSQL `tsvector` + `ts_rank` по русскоязычному контенту.
- **Живой поиск** — результаты обновляются при вводе без перезагрузки страницы (HTMX).
- **Папки** — древовидная структура с фильтрацией, счётчиками заметок, вложенными подпапками.
- **HTMX-интерактивность** — переключение папок, удаление заметок без перезагрузки.
- **Темы оформления** — 10 цветовых палитр, выбор сохраняется в localStorage.
- **Аутентификация** — Spring Security, кастомная страница логина, CSRF-защита включая HTMX-запросы.
- **CRUD** — создание, просмотр, редактирование, удаление заметок через веб-интерфейс и REST API.
- **Markdown-редактор** — toolbar с кнопками форматирования, вставка коллаутов через выпадающее меню.

---

## Стек технологий

**Backend:**
Java 21, Spring Boot, Spring MVC, Spring Data JPA, Spring Security, PostgreSQL, Hibernate

**Frontend:**
Thymeleaf, HTMX, Bootstrap 5, Prism.js

**Markdown:**
flexmark-java (GFM-таблицы, чек-листы, зачёркивание) + кастомный парсер коллаутов

**Тестирование:**
JUnit 5, Mockito

**Инфраструктура:**
Docker, Docker Compose, Nginx (reverse proxy), Let's Encrypt (HTTPS)

---

## Архитектура

Трёхслойная архитектура с чётким разделением ответственности:

```
Controller → Service → Repository
     ↕            ↕           ↕
    DTO        Mapper       Entity
```

- **Controller** — REST API (`NoteController`) и веб-интерфейс (`NoteWebController`)
- **Service** — бизнес-логика: `NoteService`, `ImportService`, `MarkdownService`, `FolderService`
- **Repository** — доступ к данным через Spring Data JPA с кастомными запросами
- **DTO** — `NoteRequest` / `NoteResponse` для разделения API и модели данных
- **Mapper** — `NoteMapper` для конвертации Entity ↔ DTO
- **Exception** — `NoteNotFoundException` + `GlobalExceptionHandler` для обработки ошибок

```
src/main/java/com/val/studynotes/
├── config/          SecurityConfig, WebConfig
├── controller/      NoteController, NoteWebController, HomeController
├── dto/             NoteRequest, NoteResponse, ImportResult, HeadingInfo, ...
├── exception/       NoteNotFoundException, GlobalExceptionHandler
├── mapper/          NoteMapper
├── model/           Note, Folder
├── repository/      NoteRepository, FolderRepository
└── service/         NoteService, ImportService, MarkdownService,
                     FolderService, TitleExtractor, PathParser, FolderResolver
```

---

## Как запустить

### Требования

- Docker и Docker Compose

### Запуск

```bash
# 1. Клонировать репозиторий
git clone https://github.com/kravval/studynotes.git
cd studynotes

# 2. Создать файл .env (по примеру .env.example)
cp .env.example .env
nano .env    # Заполнить реальными значениями

# 3. Запустить
docker compose up -d

# 4. Открыть
# http://localhost:8080
```

### Переменные окружения (.env)

```
DB_PASSWORD=пароль_базы_данных
SECURITY_USERNAME=имя_пользователя
SECURITY_PASSWORD=пароль_для_входа
```

### Остановка

```bash
docker compose down        # Остановить (данные сохраняются)
docker compose down -v     # Остановить + удалить данные
```

---

## Тестирование

```bash
# Запуск тестов (требуется Java 21 и Maven)
./mvnw test
```

Покрытие: `NoteService`, `NoteMapper`, `MarkdownService` (рендеринг + заголовки), `TitleExtractor`, `PathParser`, `ImportResult`.

---

## API

REST API доступен параллельно с веб-интерфейсом:

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/notes` | Список всех заметок |
| GET | `/api/notes/{id}` | Заметка по ID |
| POST | `/api/notes` | Создать заметку |
| PUT | `/api/notes/{id}` | Обновить заметку |
| DELETE | `/api/notes/{id}` | Удалить заметку |

---

## Автор

**Валерий Кравченко** — [GitHub](https://github.com/kravval)
