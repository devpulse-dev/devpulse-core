# API v2

Все эндпоинты живут под префиксом `/api/v2`. Формат тел запросов/ответов — JSON. Ошибки — [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) (`application/problem+json`).

> **Период:** для query-эндпоинтов параметры `from` и `to` — оба обязательны, ISO-формат `YYYY-MM-DD`, **обе границы включительно**. Если `to < from` — 400.

---

## Содержание

- [Управление сбором](#управление-сбором)
  - `POST /api/v2/collection/runs` — запустить сбор
  - `GET  /api/v2/collection/runs/{id}` — статус прогона
- [Статистика](#статистика)
  - `GET /api/v2/stats/daily` — дневные агрегаты
  - `GET /api/v2/stats/weekly` — недельная статистика (ISO-недели)
  - `GET /api/v2/stats/summary` — сводка за период
- [Пользователи](#пользователи)
  - `GET /api/v2/users/{email}/profile` — профиль с агрегацией
  - `GET /api/v2/users/{email}/commits` — коммиты с пагинацией
- [Kaiten](#kaiten)
  - `POST /api/v2/kaiten/sync-users` — принудительная синхронизация пользователей
- [Формат ошибок](#формат-ошибок)

---

## Управление сбором

### `POST /api/v2/collection/runs`

Запускает полный цикл сбора: git → daily stats → (изолированно) Kaiten cards. **Синхронный** — отдаёт уже финальный результат (`SUCCESS` или `FAILED`).

**Тело (опционально):**
```json
{ "since": "2026-05-10T00:00:00" }
```
- `since` — момент начала периода (`LocalDateTime`). Если опущено или `null` — берётся точка из последнего успешного прогона (или `2026-01-01` если прогонов ещё не было).

**Ответ 200:**
```json
{
  "id": "f1a4c0d2-…",
  "startedAt":  "2026-05-23T14:00:00",
  "finishedAt": "2026-05-23T14:00:42",
  "sinceDate":  "2026-05-10T00:00:00",
  "untilDate":  "2026-05-23T14:00:00",
  "status": "SUCCESS",
  "errorMessage": null
}
```

**Статусы:** `RUNNING` (промежуточное состояние, в БД), `SUCCESS`, `FAILED`. При `FAILED` поле `errorMessage` заполнено.

### `GET /api/v2/collection/runs/{id}`

Опрос статуса прогона по UUID.

**Ответ 200:** тот же формат, что у `POST /collection/runs`.

**Ответ 404:** если прогона с таким `id` нет.

---

## Статистика

### `GET /api/v2/stats/daily?from=YYYY-MM-DD&to=YYYY-MM-DD`

Дневные агрегаты за период (по ключу `email × date × repo`).

**Ответ 200:**
```json
[
  {
    "id": 12345,
    "email": "boris@x5.ru",
    "date": "2026-05-10",
    "repo": "xrg-core",
    "commits": 3,
    "mergeCommits": 0,
    "addedLines": 124,
    "deletedLines": 58,
    "testAddedLines": 22,
    "lastUpdated": "2026-05-10T01:02:15",
    "userId": 42
  }
]
```

### `GET /api/v2/stats/weekly?from=YYYY-MM-DD&to=YYYY-MM-DD`

Группировка по ISO-неделям, для каждой недели — totals и per-author breakdown. Список отсортирован по возрастанию `weekStart`.

**Ответ 200:**
```json
[
  {
    "year": 2026,
    "week": 19,
    "weekStart": "2026-05-04",
    "totalCommits": 47,
    "totalMergeCommits": 3,
    "totalAddedLines": 1820,
    "totalDeletedLines": 850,
    "totalTestAddedLines": 320,
    "authors": [
      { "email": "boris@x5.ru", "commits": 12, "mergeCommits": 1, "addedLines": 480, "deletedLines": 200, "testAddedLines": 90 }
    ]
  }
]
```

### `GET /api/v2/stats/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`

Сводка за период: totals + топ-10 авторов по убыванию коммитов.

**Ответ 200:**
```json
{
  "from": "2026-05-01",
  "to":   "2026-05-31",
  "totalCommits": 312,
  "totalMergeCommits": 18,
  "totalAddedLines": 12_400,
  "totalDeletedLines": 5_800,
  "totalTestAddedLines": 2_120,
  "uniqueAuthors": 14,
  "topAuthors": [
    { "email": "boris@x5.ru", "commits": 47, "mergeCommits": 3, "addedLines": 1820, "deletedLines": 850, "testAddedLines": 320 }
  ]
}
```

---

## Пользователи

### `GET /api/v2/users/{email}/profile?from=YYYY-MM-DD&to=YYYY-MM-DD`

Профиль пользователя за период: запись в `unified_user`, агрегированный `summary` из daily stats, первые 500 коммитов и карточки Kaiten (если у пользователя есть `kaiten_id`).

**Ответ 200:**
```json
{
  "user": {
    "id": 42,
    "email": "boris@x5.ru",
    "username": "boris",
    "name": "Boris",
    "avatarUrl": null,
    "kaitenId": 7,
    "gitlabId": null
  },
  "summary": { "email": "boris@x5.ru", "commits": 47, "mergeCommits": 3, "addedLines": 1820, "deletedLines": 850, "testAddedLines": 320 },
  "commits": [
    {
      "hash": "a1b2c3d4…",
      "authorEmail": "boris@x5.ru",
      "commitDate": "2026-05-10T12:00:00",
      "merge": false,
      "addedLines": 42,
      "deletedLines": 7,
      "testAddedLines": 12,
      "message": "12345 fix the bug",
      "taskNumber": "12345",
      "repo": "xrg-core"
    }
  ],
  "cards": [
    {
      "id": 12345,
      "title": "Fix the bug",
      "description": "…",
      "status": "in_progress",
      "columnName": "In Progress",
      "boardName": "Core",
      "spaceName": "Engineering",
      "ownerId": 7,
      "ownerName": "Boris",
      "createdAt": "2026-05-01T10:00:00",
      "updatedAt": "2026-05-10T12:00:00",
      "closedAt": null,
      "archived": false,
      "url": "https://kaiten.x5.ru/12345",
      "memberIds": [7]
    }
  ]
}
```

**Ответ 404:** если в `unified_user` нет записи с таким email.

### `GET /api/v2/users/{email}/commits?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=50`

Коммиты пользователя за период с пагинацией.

**Параметры пагинации:**
- `page` (default `0`) — номер страницы, 0-based.
- `size` (default `50`, max `500`) — размер страницы.

**Ответ 200:** массив объектов того же формата, что `commits[]` в профиле выше.

---

## Kaiten

### `POST /api/v2/kaiten/sync-users`

Тянет всех пользователей из Kaiten API и bulk-upsert-ит в `kaiten_user`. Используется как ручной триггер, если расписание сбора пропустило обновления.

**Ответ 200:**
```json
{ "synced": 142 }
```

---

## Формат ошибок

Все ошибки — RFC 7807 `application/problem+json`:

```json
{
  "type":   "urn:markable:problem:bad-request",
  "title":  "Bad request",
  "status": 400,
  "detail": "period.to (2026-05-01) is before period.from (2026-05-31)",
  "instance": "/api/v2/users/boris@x5.ru/profile"
}
```

| Status | `type` | Когда |
|---|---|---|
| 400 | `urn:markable:problem:bad-request` | Бизнес-валидация (например `Period.to < from`). |
| 400 | `urn:markable:problem:bad-request` *(title: Malformed request)* | Не пришёл обязательный query-параметр, не распарсилась дата. |
| 404 | (без тела) | Ресурс не найден (профиль / прогон сбора). |
| 500 | `urn:markable:problem:internal` | Внутренняя ошибка сервера. Детали в логах сервера. |
