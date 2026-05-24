# API v2

Все эндпоинты живут под префиксом `/api/v2`. Формат тел запросов/ответов — JSON. Ошибки — [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807) (`application/problem+json`).

> **Период:** для query-эндпоинтов параметры `from` и `to` — оба обязательны, ISO-формат `YYYY-MM-DD`, **обе границы включительно**. Если `to < from` — 400.

---

## Содержание

- [Управление сбором](#управление-сбором)
  - `POST /api/v2/collection/runs` — запустить сбор
  - `GET  /api/v2/collection/runs/{id}` — статус прогона
- [Дашборд](#дашборд)
  - `GET /api/v2/dashboard` — топ-N активных + N аутсайдеров
- [Статистика](#статистика)
  - `GET /api/v2/stats/daily` — дневные агрегаты
  - `GET /api/v2/stats/weekly` — недельная статистика (ISO-недели)
  - `GET /api/v2/stats/summary` — сводка за период
- [Пользователи](#пользователи)
  - `GET /api/v2/users/{email}/profile` — профиль с агрегацией и live-карточками Kaiten
  - `GET /api/v2/users/{email}/commits` — коммиты с пагинацией
- [Kaiten](#kaiten)
  - `POST /api/v2/kaiten/sync-users` — принудительная синхронизация пользователей
- [Формат ошибок](#формат-ошибок)

---

## Управление сбором

### `POST /api/v2/collection/runs`

Запускает полный цикл сбора: git → daily stats → (изолированно) sync Kaiten users (обновление `kaitenId`/`avatarUrl` в `unified_user`). **Карточки Kaiten НЕ выкачиваются** — они тянутся live при открытии профиля. **Синхронный** — отдаёт уже финальный результат (`SUCCESS` или `FAILED`).

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

## Дашборд

### `GET /api/v2/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20`

Главный борд: **paginated** список всех активных авторов за период (имеющих ≥ 1 коммит).
Отсортирован по убыванию **не-мердж коммитов** (`commits − mergeCommits`) — отражает «реальную работу», мерджи не накручивают позицию в топе.

> Фронт может показать первую страницу как «топ» и последнюю как «аутсайдеры». Раздельных секций в API больше нет — один list + pagination.

**Параметры:**
- `from`, `to` — опциональны. Если опущены, бэк подставляет **последние 30 дней** (`today-30..today`).
- `page` — 0-based номер страницы, default `0`.
- `size` — размер страницы, default `20`, max `500`.

**Ответ 200:**
```json
{
  "from": "2026-04-23",
  "to":   "2026-05-23",
  "page": 0,
  "size": 20,
  "totalElements": 47,
  "totalPages": 3,
  "hasNext": true,
  "items": [
    {
      "email": "boris@x5.ru",
      "displayName": "Boris",
      "avatarUrl": "https://kaiten.x5.ru/.../avatar.png",
      "commits": 50,
      "nonMergeCommits": 45,
      "mergeCommits": 5,
      "addedLines": 1820,
      "deletedLines": 850,
      "testAddedLines": 320
    }
  ]
}
```

**Особенности:**
- `displayName` и `avatarUrl` подтягиваются из `unified_user` по email. Если автор ещё не связан с Kaiten — оба будут `null`.
- `nonMergeCommits` — производная (`commits − mergeCommits`), показывается для прозрачности ранжирования.
- Enrichment делается только для авторов на текущей странице — не тратим ресурсы на batch-фетч профилей для всех 47 авторов, если фронт смотрит только первые 20.

---

## Статистика

### `GET /api/v2/stats/daily?from=YYYY-MM-DD&to=YYYY-MM-DD`

Дневные агрегаты за период (по ключу `email × date × repo`).

> Здесь `displayName`/`avatarUrl` **не** возвращаются — дневных записей много (тысячи за период), enrichment дорогой. Если нужны профили — фронт может агрегировать по `email` на своей стороне и подтянуть из `/dashboard` или `/users/{email}/profile`.

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

Группировка по ISO-неделям, для каждой недели — totals и per-author breakdown с enriched `displayName`/`avatarUrl`. Список отсортирован по возрастанию `weekStart`.

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
      {
        "email": "boris@x5.ru",
        "displayName": "Boris",
        "avatarUrl": "https://kaiten.x5.ru/.../avatar.png",
        "commits": 12,
        "nonMergeCommits": 11,
        "mergeCommits": 1,
        "addedLines": 480,
        "deletedLines": 200,
        "testAddedLines": 90
      }
    ]
  }
]
```

> `displayName`/`avatarUrl` могут быть `null` — если автор ещё не связан с Kaiten (нет записи в `unified_user`).

### `GET /api/v2/stats/summary?from=YYYY-MM-DD&to=YYYY-MM-DD`

Сводка за период: totals + топ-10 авторов с enriched `displayName`/`avatarUrl`. Топ отсортирован по убыванию **всех** коммитов (включая мерджи) — это исторически так, для top-N это менее критично чем для дашборда.

**Ответ 200:**
```json
{
  "from": "2026-05-01",
  "to":   "2026-05-31",
  "totalCommits": 312,
  "totalMergeCommits": 18,
  "totalAddedLines": 12400,
  "totalDeletedLines": 5800,
  "totalTestAddedLines": 2120,
  "uniqueAuthors": 14,
  "topAuthors": [
    {
      "email": "boris@x5.ru",
      "displayName": "Boris",
      "avatarUrl": "https://kaiten.x5.ru/.../avatar.png",
      "commits": 47,
      "nonMergeCommits": 44,
      "mergeCommits": 3,
      "addedLines": 1820,
      "deletedLines": 850,
      "testAddedLines": 320
    }
  ]
}
```

---

## Пользователи

### `GET /api/v2/users/{email}/profile?from=YYYY-MM-DD&to=YYYY-MM-DD`

Профиль пользователя за период: запись в `unified_user`, агрегированный `summary` из daily stats, первые 500 коммитов и **карточки Kaiten live** (если у пользователя есть `kaiten_id`).

> **Карточки Kaiten тянутся напрямую из Kaiten API при каждом запросе** — не из локальной БД. Фильтр: карточки, обновлённые после `from`. Используется в основном кейсе «открыть профиль с дашборда», поэтому `from`/`to` тоже **опциональны** — без них берётся последние 30 дней.

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

---

## Migration notes для фронта (что меняется в Фиче 2)

Бэк выкатил две связанные правки. Фронту нужно адаптироваться по ним обоим.

### 1. `/api/v2/dashboard` — новый paginated-контракт

**Было:**
```json
{
  "from": "...", "to": "...",
  "topActive":  [ AuthorSummary, ... ],
  "outsiders":  [ AuthorSummary, ... ]
}
```

**Стало:**
```json
{
  "from": "...", "to": "...",
  "page": 0, "size": 20, "totalElements": 47, "totalPages": 3, "hasNext": true,
  "items":      [ AuthorSummary, ... ]
}
```

**Параметры запроса:**
- ❌ Убрали: `topN`, `outsiderN`.
- ✅ Добавили: `page` (0-based, default `0`), `size` (default `20`, max `500`).

**Что делать фронту:**
- Заменить запрос `?topN=10&outsiderN=10` на `?page=0&size=20`.
- Раздельных секций «top» и «outsiders» больше нет. Если нужна секция аутсайдеров — берём последнюю страницу (`page = totalPages - 1`).
- Сортировка теперь по **не-мердж коммитам** (`commits − mergeCommits`), убывающе. Tie-breaker — email алфавитно. Это значит порядок страниц стабильный, можно безопасно prefetch'ить.

### 2. `AuthorSummary` поменялся — новые поля во **всех** эндпоинтах, где он возвращается

Затронуты: `/dashboard`, `/stats/weekly`, `/stats/summary`. (`/stats/daily` использует другой тип, без изменений.)

**Было:**
```ts
type AuthorSummary = {
  email: string;
  commits: number;
  mergeCommits: number;
  addedLines: number;
  deletedLines: number;
  testAddedLines: number;
};
```

**Стало:**
```ts
type AuthorSummary = {
  email: string;
  displayName: string | null;     // ⬅️ new
  avatarUrl:   string | null;     // ⬅️ new
  commits: number;
  nonMergeCommits: number;        // ⬅️ new (computed: commits - mergeCommits)
  mergeCommits: number;
  addedLines: number;
  deletedLines: number;
  testAddedLines: number;
};
```

**Что делать фронту:**
- Обновить TS-тип (или регенерировать из OpenAPI, когда подключим — Фича отложена).
- На карточке автора показывать `avatarUrl` (если есть — иначе плейсхолдер с инициалами `email[0]`) и `displayName` (если есть — иначе fallback на email или часть email до `@`).
- Если фронт где-то отображает «количество коммитов автора», уточнить: использовать `commits` (общее число, включая мерджи) или `nonMergeCommits` (реальная работа). На дашборде логично показывать `nonMergeCommits` — это и есть метрика ранжирования. Можно показать оба числа в виде `45 + 5 мерджей`.

**Откуда берётся профиль:**
- `displayName` и `avatarUrl` тянутся бэком из таблицы `unified_user` по `email`.
- Запись в `unified_user` появляется автоматически при первом коммите автора. Поля `displayName`/`avatarUrl` заполняются на этапе **sync Kaiten users** в `POST /api/v2/collection/runs` — если у автора email совпадает с email-ом пользователя в Kaiten, бэк подставляет имя и URL аватара из Kaiten-профиля.
- Поэтому возможны три состояния:
  1. Автор **не из Kaiten** (внешний контрибьютор / сервисный аккаунт) → `displayName=null, avatarUrl=null`.
  2. Автор есть в Kaiten, но **сбор ещё не запускался** → `null, null`.
  3. Автор в Kaiten + был sync → оба поля заполнены.

### 3. Чек-лист миграции

- [ ] Заменить `topN`/`outsiderN` на `page`/`size` в запросе дашборда.
- [ ] Обновить TS-типы `DashboardResponse` и `AuthorSummary`.
- [ ] Перерендер дашборда как paginated списка (table/grid с пагинацией снизу или infinite scroll).
- [ ] Аватары на карточках авторов с fallback на инициалы из email.
- [ ] Решить семантику числа коммитов: `commits` vs `nonMergeCommits` (рекомендую отображать оба или только non-merge).
- [ ] Передёргать `POST /api/v2/collection/runs` хотя бы раз после деплоя — чтобы заполнились `kaiten_id`/`avatarUrl`/`name` в `unified_user`.

Если вопросы по полям или нужно дополнить контракт — пингуйте, обновим.
