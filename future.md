# Future: раздел «Подготовка к Performance Review»

Статус: дизайн зафиксирован, идёт реализация (Фаза 1). Документ — единый источник
правды по фиче, пока она не въедет в README/REFACTORING.

## Идея

Новый раздел на фронте: выбираешь человека + период → система собирает «досье» к
perf-review. Сколько было дефектов и задач на разработку, сколько коммитов, строк
тест-кода, проведённых ревью — с дельтами к предыдущему периоду и ссылками-пруфами.

Это **read-model поверх уже собранных данных**, а не новый сбор: ~80% метрик уже
лежат в БД. Главная новая работа — композиция, счётчики карточек по типам и дельты.

## Принятые решения (штурм)

| Вопрос | Решение |
|---|---|
| Подход к API | **B** — новый агрегирующий эндпоинт `GET /performance/review`, бэк композитит существующие сервисы |
| Где контракт | Расширяем **stats-contract**, не плодим 7-й артефакт |
| Историчность дефектов | **Снапшот «как сейчас»**: карточки считаются по текущему состоянию, фильтр по created/closed/updated в периоде. На фронте честно подписать |
| Композитный скор | **Нет.** Только сырые метрики + дельты. В HR-контексте единый балл провоцирует «оценку по цифре» и гейминг |
| Приватность | Пока **открытая** — без авторизации в v1 |
| Команды | Поле `team` в `unified_user`, людей добавляют в команды **через фронт** |

## Доступность данных

| Метрика | Источник | Статус |
|---|---|---|
| Коммиты, строки +/− | `daily_author_stats` | ✅ persisted, агрегируется (`StatsSummarizer`) |
| Тесты добавил | `daily_author_stats.testAddedLines` | ⚠️ это **строки** тест-кода, не число тестов. Подписать честно |
| Ревью (given/received) | `mr_review` → `ReviewAuthorStats` | ✅ есть (`GetReviewStatsUseCase`), нужен per-user срез |
| Дефекты / задачи разработки | `KaitenCard.cardType()` (DEFECT/DEVELOPMENT), live-fetch по member | ⚠️ данные есть, не агрегируются как счётчики, не хранятся исторически |

`GetUserProfileUseCase` уже собирает для одного человека за период `user + summary +
commits + cards` — perf-review это его расширение + ревью-метрики + счётчики типов.

## Грабли (приняты осознанно)

1. **Карточки не персистятся** (live + Caffeine 5 мин) → исторические дефекты считаем
   по снапшоту «как сейчас». Снимается в Фазе 3.
2. **«Тесты» = строки, не количество.** Прокси. Считать тест-методы — отдельная
   работа в `adapter-git`, вне Фазы 1.
3. **HR-чувствительность** → подаём как «факты к разговору», без единого балла.

## API (Фаза 1) — расширение stats-contract

Эндпоинт:
```
GET /performance/review
  ?email={email}              # subject (консистентно с остальным API — ключ по email)
  &from=YYYY-MM-DD&to=YYYY-MM-DD
  &compareToPrevious=true     # дельты к равному периоду встык перед from
```

Ответ `PerformanceReview`:
```
subject:    UserProfile (теперь с полем team)
period:     { from, to }
comparedTo: { from, to } | null
metrics:    набор MetricDelta { current, previous, delta, deltaPct }:
            commits, nonMergeCommits, addedLines, deletedLines, testAddedLines,
            reviewsGiven, commentsGiven, reviewsReceived, avgTimeToMergeHours, mergedMrCount,
            defectsInWork, defectsClosed, devTasksInWork, devTasksClosed
taskBreakdown: { defect: {inProgress, done, total}, development: {...} }
highlights:    [ { kind: CARD|MR, title, url, ... } ]   # пруфы со ссылками
```

Команды (users-contract):
```
GET  /users                  # список (для picker'а и управления командами), фильтр ?team=
PUT  /users/{email}/team     # body { team: string|null } — назначить/снять команду
```
Поле `team` (nullable) добавляется в shared `UserProfile`.

## Бэк (Фаза 1) — композиция, без нового сбора

- `GetPerformanceReviewUseCase` (port/in) → `PerformanceReviewService` (application).
- Переиспользует: `DailyStatsRepository` (commits/lines), `ReviewSummarizer`/review-репо
  (per-user срез), `KaitenGateway.fetchCardsForMember` (карточки → фильтр по `cardType()`
  + `columnStatus()` + пересечение с периодом), `UnifiedUserRepository` (subject + kaitenId).
- **Дельты:** агрегируем дважды (текущий период + встык-предыдущий равной длины),
  вычитаем чистой доменной функцией (тестируется без БД).
- Счёт карточек: `inWork` = `columnStatus()==IN_PROGRESS`; `closed` = `closedAt` в периоде.
- Новых таблиц/миграций в Фазе 1 нет (кроме `team`-колонки) — всё read-only.

## Фронт (Фаза 1)

Роут `/performance-review`. Picker человека (по `GET /users`, фильтр по команде) +
picker периода (пресеты квартал/полугодие/год + custom) + тогл «сравнить с предыдущим».
Layout: шапка subject → сетка KPI с дельтами → разбивка задач (стэкбар defect/dev по
статусам) → код → ревью (given vs received) → heatmap (переиспользуем weekly/hourly) →
highlights со ссылками → печать (Фаза 1 — print-CSS). Важны zero/empty-состояния.

## Команды и лиды — модель

- **Команда** — `unified_user.team` (nullable `VARCHAR`, свободный текст). Команды деривятся
  группировкой по этому полю (доменный `TeamAssembler`), отдельной сущности нет.
- **Лид** — `unified_user.is_lead` (boolean, миграция 026). Один лид на команду; инвариант
  держит app-слой (`SetTeamLeadService`: назначение чистит прежнего лида и добавляет нового
  в команду). Лид = участник команды с `is_lead = true`.
- **Засев данных:** миграция 025 проставила команды по орг-структуре (47 чел.), 026 — DEV-лидов
  (6 чел.). Delivery-лиды в `unified_user` отсутствуют (нет git/kaiten-активности) — не заведены.
- Канонический справочник команд (таблица `team` + FK, роли Delivery/DEV-лид) — кандидат на
  Фазу 2, если понадобится валидация/переименование/иерархия/несколько лидов.

## Фазы

1. **Сейчас:** `team`-колонка + OAS (shared `team`, `/users` list + `/users/{email}/team`,
   `/performance/review`) → бэк-композиция → фронт read-only с дельтами.
2. Экспорт (PDF) + персист отчёта (`performance_review`) для воспроизводимости/аудита.
3. Персист снимков карточек Kaiten на момент сбора → точные исторические дефекты.

## Чеклист contract-first (OAS в devpulse-oas / локально markable-dev-analytics-oas)

- [x] PR в `devpulse-oas`: shared `team`, users `/users` + `/users/{email}/team`, stats `/performance/review`
- [x] bump `<revision>` в корневом `pom.xml` OAS (1.4.0 → 1.5.0, аддитивный minor)
- [x] `mvn clean install` + `cd api-types && npm run build` (синк package.json), publish train (workflow_dispatch)
- [x] bump `devpulse-oas.version` в `adapter-rest/pom.xml` (1.4.0 → 1.5.0) + `mvn -U`
- [x] бэк: `team`-миграция (024), use case'ы (`GetPerformanceReview`/`ListUsers`/`SetUserTeam`),
      доменный `PerformanceReviewAssembler` + модели, контроллеры (`StatsController.getPerformanceReview`,
      `UsersController.listUsers/setUserTeam`), MapStruct `PerformanceReviewMapper`, wiring, юнит-тесты
- [x] бэк-тесты зелёные (домен `PerformanceReviewAssemblerTest`/`PeriodTest`, app `PerformanceReviewServiceTest`,
      web-slice `StatsControllerTest`/`UsersControllerTest`)
- [x] фронт perf-review (раздел, picker'ы, KPI-дельты) — сделан
- [x] данные команд: миграция 025 (команды по орг-структуре), фронт-управление командами

### Итерация 2 — команды first-class + лиды (API 1.6.0)

- [x] OAS 1.5.0 → 1.6.0: shared `UserProfile.isLead`; тег `Teams` → `GET /teams`, `PUT /teams/lead`; схемы `Team`, `SetTeamLeadRequest`
- [x] миграция 026 (`is_lead` + засев DEV-лидов)
- [x] бэк: `UnifiedUser.lead`, домен `Team` + `TeamAssembler`, порт `updateLead`/`clearLeadForTeam`,
      use case'ы `ListTeams`/`SetTeamLead`, `TeamsController`, `TeamMapper`, `UserProfileMapper(isLead)`, wiring
- [x] тесты: `TeamAssemblerTest`, `TeamServicesTest`, `TeamsControllerTest`
- [ ] publish OAS 1.6.0 + bump `devpulse-oas.version` 1.5.0 → 1.6.0 + `mvn -U` ← **за тобой**
- [ ] фронт: глобальный фильтр по всем командам (вместо хардкода Маркировки), отображение лидов
      («кто откуда»), управление командой (add/exclude member, assign/remove lead)

## Статус

**Бэкенд готов и протестирован, API 1.6.0.** Perf-review (фронт сделан) + команды/лиды.
Осталось: опубликовать OAS 1.6.0 и доработать фронт под команды-фильтр/лидов.

## Фронт — готовый контракт (types из `@devpulse-dev/api-types` ^1.6.0)

Раздел `/performance-review` + управление командами. Все ручки под префиксом `/api/v2`.

**Команды и лиды (Итерация 2):**
- `GET /teams` → `Team[]` `{ name, lead: UserProfile|null, members: UserProfile[] }` — для
  глобального фильтра по командам и отображения «кто откуда» (лид виден). У `UserProfile` теперь есть `isLead`.
- `PUT /teams/lead` body `{ team, email: string|null }` → `Team` — назначить (email) / снять (null) лида.
- членство: `PUT /users/{email}/team` body `{ team }` (имя — добавить/перевести, `null` — исключить).
- глобальный фильтр: дропдаун из `GET /teams` (имена) + members для резолва по командам.

**Perf-review:**
- `GET /users` (опц. `?team=`) → `UserProfile[]` (с `team`, `isLead`) — picker человека и фильтр по команде.
- `PUT /users/{email}/team` body `{ team: string | null }` → `UserProfile` — назначить/снять команду (404 если нет юзера).
- `GET /performance/review?email=&from=&to=&compareToPrevious=` → `PerformanceReview`:
  - `subject: UserProfile` (имя/аватар/команда),
  - `period`, `comparedTo: Period | null`,
  - `metrics`: 14 полей типа `MetricDelta { current, previous, delta, deltaPct }`
    (git+ревью — с дельтами; `defectsInWork/Closed`, `devTasksInWork/Closed` — **снапшот, previous/delta = null**),
  - `taskBreakdown: { defect, development }` каждый `{ inProgress, done, total }`,
  - `highlights: { kind: CARD|MR, title, subtitle?, url }[]`.

**UI-план:** шапка subject → сетка KPI-карточек (виджет `MetricDelta`: значение + ↑/↓ дельта, у снапшот-метрик дельту скрыть) → стэкбар задач (defect/dev × inProgress/done) → код/ревью секции → highlights со ссылками → print-CSS для печати. Контролы: picker человека (по `/users`, фильтр по команде), picker периода (пресеты квартал/полугодие/год + custom), тогл «сравнить с предыдущим». Не забыть zero/empty-состояния.

**Подписать на UI честно:** дефекты/задачи — «по текущему состоянию карточек»; «тесты» — строки тест-кода, не число тестов.

**Управление командами:** отдельный экран/модалка — список `/users`, инлайн-назначение команды через `PUT /users/{email}/team`.
