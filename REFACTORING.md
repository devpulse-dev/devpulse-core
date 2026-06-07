# План рефакторинга v1 → v2

> **Статус:** рефакторинг (Сессии 1–7) завершён ✅. Дальше — фичи поверх готового каркаса.
> Текущая активная работа описана в разделе [«Фичи»](#фичи) в конце документа.



Документ — единый источник правды по тому, что мы делаем и почему. Сюда же кладём решения, чтобы при возвращении к проекту через неделю/месяц не пришлось гадать «а это зачем».

---

## 1. Зачем переписываем

**v1** — однамодульное Spring Boot 3.2.4 / Java 17 приложение `src/main/java/.../gitlab/{client,config,interactor,mapper,model,persistence,rest,service,utill}`. Работало, но накопилось:

- Бизнес-логика размазана между `service/`, `service/impl/`, `service/impl/helper/`. «God-сервисы» с десятком зависимостей.
- Слой сервисов знает про JPA-энтити и DTO одновременно — мутации через `setXxx`, ленивые баги при сохранении.
- Конкуррентность руками: `AsyncConfig` + `Executors.newFixedThreadPool(5)` + `CompletableFuture.allOf().join()`. Состояния гонок (см. фиксы #5 «UserSyncHelper race condition», #7 «daily_author_stats unique constraint»).
- N+1: `findOrCreateByEmail` дёргался в цикле — пришлось ввести batch (#3, #8).
- Тесты — фрагментарные. Архитектурных проверок (направление зависимостей) не было.
- `RestTemplate` руками + свой retry на каждый клиент (Git CLI, Kaiten, AI).

**v2 — цели рефакторинга:**

1. **Гексагональная архитектура.** `domain` чистый, `application` оперирует только портами, адаптеры заменяемы и тестируемы изолированно.
2. **Multi-module Maven.** Зависимости явные в pom-ах, ArchUnit сторожит направление.
3. **Java 25 + Spring Boot 4.** Virtual threads + Structured Concurrency вместо ручного executor. `@HttpExchange` вместо самописных `RestTemplate`.
4. **Bulk-первым.** Любая массовая операция — один SQL/один HTTP запрос на batch, не N.
5. **Изоляция фаз сбора.** Падение Kaiten не должно стирать прогресс git-сбора.
6. **Полный тестовый каркас.** Unit (domain + use cases) + Testcontainers integration (persistence) + ArchUnit.

---

## 2. Что оставляем, меняем, выпиливаем

### Оставляем (с переносом 1-в-1 в новую структуру)

- Схема БД целиком — все 14 миграций v1 перенесены в `adapter-persistence/.../liquibase/migration/`. Никаких ломающих изменений данных.
- Контракт Git CLI: `git log --numstat` с форматом `hash|email|parent|date|message` — парсер переехал в `adapter-git/GitLogParser` с покрытием юнит-тестами.
- Адаптивный rate-limiter Kaiten (429 + 5xx + Retry-After) — переехал в `adapter-kaiten/KaitenRateLimiter`.
- Идея `UnifiedUser` (один человек = одна запись, связь к git author по email и к Kaiten user по `kaiten_id`).
- Поведение «курсор двигается только на успех», «Kaiten не валит git stats», «commit без email пропускается».

### Меняем

| v1 | v2 |
|---|---|
| `AsyncConfig` + `ThreadPoolTaskExecutor` | Virtual threads + `StructuredTaskScope` |
| `CompletableFuture.allOf().join()` | `StructuredTaskScope.ShutdownOnFailure` |
| `RestTemplate` + ручной retry | `@HttpExchange` interface client + `KaitenRateLimiter` декоратор |
| Сервисы пишут в JPA-энтити напрямую | Use case оперирует доменными record, MapStruct мапит на JPA в адаптере |
| `LastExportTracker` (тип `DAILY_STATS`) | Таблица `collection_run` с UUID, since/until, статусом и ошибкой |
| `findOrCreateByEmail` в цикле | `UnifiedUserRepository.findOrCreateAll(emails)` — один SELECT + один batch-INSERT |
| `dailyStatsRepository.saveAll(...)` | Native `INSERT ... ON CONFLICT DO UPDATE` через JdbcTemplate |
| `/api/v1/*` (контроллеры с богом-DTO) | `/api/v2/*` — чистые контракты, ISO-даты, RFC 7807 для ошибок |

### Выпиливаем (НЕ переносим)

- ❌ **AI-сводки.** `AiSummaryService`, `AiClient`, `AiProperties`, `/api/v1/ai-summary`. Если вернёмся к AI — отдельный adapter-ai снаружи. Сейчас не нужно.
- ❌ **Старый `/api/v1/analysis`** (`AnalysisController`/`AnalysisService`/`AnalysisInteractor`/`AnalysisRun`/`AuthorStats`/`RepoStats`). Дублировал daily stats, синхронный API с UUID запуска — никто им не пользовался.
- ❌ **Redis** + `RedisConfig`. Кэш был лепным, профита не давал.
- ❌ **Спорные хелперы** профиля пользователя (`UserProfileSummaryGenerator`) — генерили AI-промпт, мёртвый код после удаления AI.

---

## 3. Структура модулей

```
devpulse/
├── pom.xml                       # parent, packaging=pom
│
├── domain/                       # pure Java, без Spring/JPA/Jackson/Hibernate/Lombok
│   ├── common/                   # Email, Period, PageRequest, TaskNumber
│   ├── model/
│   │   ├── git/                  # Commit, CommitHash, RepoName
│   │   ├── stats/                # DailyAuthorStats
│   │   ├── kaiten/               # KaitenCard, KaitenUser, KaitenCardId
│   │   ├── user/                 # UnifiedUser, KaitenUserId
│   │   └── collection/           # CollectionRun, CollectionStatus
│   └── service/                  # AuthorAggregator, CommitMessageParser, TestFileDetector
│
├── application/                  # без Spring/JPA. Lombok разрешён.
│   ├── port/
│   │   ├── in/                   # *UseCase — что система умеет
│   │   └── out/                  # GitGateway, KaitenGateway, *Repository
│   └── service/                  # POJO-реализации UseCase'ов
│
├── adapter-rest/                 # @RestController + DTO (api/v2)
├── adapter-persistence/          # JPA entities + Spring Data + Liquibase + MapStruct
├── adapter-git/                  # git CLI client → GitGateway
├── adapter-kaiten/               # @HttpExchange + RateLimiter → KaitenGateway
└── bootstrap/                    # @SpringBootApplication, @Configuration wiring, ArchUnit-тесты
```

---

## 4. REST API v2 (план)

| Method | Path | Use case |
|---|---|---|
| POST | `/api/v2/collection/runs` | `CollectDailyStatsUseCase` |
| GET | `/api/v2/collection/runs/{id}` | (статус прогона) |
| GET | `/api/v2/stats/daily?from=&to=` | `GetDailyStatsUseCase` |
| GET | `/api/v2/stats/weekly?from=&to=` | `GetWeeklyStatsUseCase` |
| GET | `/api/v2/stats/summary?from=&to=` | `GetPeriodSummaryUseCase` |
| GET | `/api/v2/users/{email}/profile?from=&to=` | `GetUserProfileUseCase` |
| GET | `/api/v2/users/{email}/commits?from=&to=` | `GetUserCommitsUseCase` |
| POST | `/api/v2/kaiten/sync-users` | `SyncKaitenUsersUseCase` |

**Принципы:**
- Периоды через `from`/`to` (ISO date), включительно.
- Пагинация — `page` + `size`.
- Ошибки — RFC 7807 (`application/problem+json`).
- Никаких «god-DTO» из v1: каждый эндпоинт отдаёт ровно то, что нужно фронту.

---

## 5. Roadmap по сессиям

Статус: ✅ done · 🟡 in progress · ⬜ todo

| # | Сессия | Состав | Статус |
|---|---|---|---|
| 1 | **Каркас** | Parent pom, 8 модулей с pom.xml, Main + application.yml, ArchUnit-тест, перенос миграций | ✅ |
| 2 | **Domain + application ports** | Value objects (Email, Period, hashes, ids), entities, domain services + unit-тесты, in-ports и out-ports | ✅ |
| 3 | **adapter-persistence** | JPA entities на существующие таблицы, миграция 015 (collection_run), MapStruct entity↔domain, Spring Data репозитории, реализации out-портов, Testcontainers тесты | ✅ |
| 4 | **adapter-git + adapter-kaiten** | GitProperties+CliClient+LogParser+GatewayAdapter (StructuredTaskScope), KaitenProperties+DTO+HttpClient (`@HttpExchange`)+RateLimiter+GatewayAdapter (streaming), unit-тесты | ✅ |
| 5 | **Use cases команды (write side)** | `CollectDailyStatsService`, `SyncKaitenUsersService`, wiring в bootstrap, Mockito-тесты | ✅ |
| 6 | **Use cases запросов + adapter-rest** | Реализация query-use cases (Profile, Weekly, Summary, DailyStats, UserCommits, CollectionRun), `StatsSummarizer` в domain, REST-контроллеры api/v2 (Collection/Stats/Users/Kaiten), DTO с `from(domain)`, RFC 7807 через `ProblemDetail`, MockMvc-тесты | ✅ |
| 7 | **Финал** | Удаление `.old-src/`, smoke-тест в bootstrap, документация эндпоинтов | ✅ |

### Где мы сейчас — рефакторинг завершён ✅

Сделано:
- ✅ `domain/service/StatsSummarizer` — pure-агрегация `DailyAuthorStats → PeriodSummary` и `→ List<WeeklyStats>` (ISO weeks).
- ✅ 5 query use cases в `application/service/`: `GetDailyStatsService`, `GetWeeklyStatsService`, `GetPeriodSummaryService`, `GetUserCommitsService`, `GetUserProfileService` + `GetCollectionRunService` (для polling статуса прогона).
- ✅ Все use cases зарегистрированы как `@Bean` в `UseCaseConfig`.
- ✅ Unit-тесты: `StatsSummarizerTest` (domain) + `QueryUseCasesTest` (application, все 5 use case-ов с Mockito).
- ✅ DTO в `adapter-rest/dto/` — все records со статическим `from(domain)`.
- ✅ 4 REST-контроллера: `CollectionController` (POST/GET runs), `StatsController` (daily/weekly/summary), `UsersController` (profile/commits + pagination), `KaitenController` (sync-users).
- ✅ `ApiExceptionHandler` — RFC 7807 через `ProblemDetail`, обрабатывает 400 (malformed/illegal input) и 500.
- ✅ MockMvc-тесты на каждый контроллер.

---

## 6. Архитектурные решения (ADR-lite)

### ADR-1. Use cases — POJO, не `@Service`
**Контекст:** `application` модуль не должен зависеть от Spring (enforced ArchUnit).
**Решение:** реализации use cases — обычные классы с конструктором (Lombok `@RequiredArgsConstructor`). Регистрация бинов — в `bootstrap` через `@Configuration` + `@Bean` методы.
**Плюс:** application можно использовать без Spring, тесты на POJO без `@SpringBootTest`.

### ADR-2. Логирование через SLF4J
**Контекст:** `application` без Spring, но логи нужны.
**Решение:** зависимость на `slf4j-api`, аннотация `@Slf4j` (Lombok). Реальную имплементацию (log4j2) даёт `bootstrap` через `spring-boot-starter`.

### ADR-3. Изоляция Kaiten от Git в use case
**Контекст:** Kaiten часто отдаёт 429, на поздних страницах падение часовых git-сборов недопустимо.
**Решение:** `CollectDailyStatsService.run()` оборачивает git и kaiten в **разные** `try/catch`. Git упал → `CollectionRun.failed()`. Kaiten упал → лог error, но `CollectionRun.succeeded()`. `@Transactional` на use case **нет** — каждая bulk-операция своя транзакция на уровне репозитория.

### ADR-4. Курсор сбора — `collection_run`, не `last_export_tracker`
**Контекст:** v1 хранил курсор в строке с типом `DAILY_STATS`. Нет аудита прогонов.
**Решение:** новая таблица `collection_run` (UUID, started/finished, since/until, status, error). Старт следующего сбора — `MAX(until) WHERE status='SUCCESS'`. Прогон с ошибкой остаётся в журнале, но курсор не двигает.

### ADR-5. Парсинг git-output живёт в адаптере
**Контекст:** Соблазн был оставить `parseGitOutput` в use case (как было в v1 `AnalysisServiceImpl`).
**Решение:** парсинг строк `hash|email|parent|date|message` + numstat — **деталь git CLI**, не бизнес-логика. Адаптер `adapter-git/GitLogParser` возвращает `List<Commit>`. Use case оперирует только доменом.

### ADR-6. Bulk upsert через JdbcTemplate, не Hibernate
**Контекст:** Hibernate `saveAll` для daily stats делал N SELECT-перед-UPDATE даже с batch settings.
**Решение:** `DailyStatsRepositoryAdapter` использует `JdbcTemplate.batchUpdate` с native `INSERT ... ON CONFLICT (email, date, repository_name) DO UPDATE`. Атомарно, без race conditions, без N+1.

### ADR-7. Stream-API карточек Kaiten
**Контекст:** API карточек пагинирован, может быть много страниц, 429 на N-й странице терял всё.
**Решение:** `KaitenGateway.streamCards(memberIds, since, pageHandler)` — отдаёт страницу через callback. Use case коммитит каждую страницу отдельной транзакцией.

### ADR-8. Spring Boot 4 test slices — нюансы стека
**Контекст:** в Spring Boot 4 web-slice-тесты пересобрали:
- `@WebMvcTest` переехал из `spring-boot-test-autoconfigure` (пакет `…test.autoconfigure.web.servlet`) в отдельный артефакт `spring-boot-starter-webmvc-test` (пакет `…boot.webmvc.test.autoconfigure`).
- Контроллеры в Spring Framework 7 надёжно работают как `public class` с публичными методами-хендлерами (package-private даёт молчаливые 404).
- Тестовый якорь должен быть `@SpringBootApplication`, **не** сокращённая связка `@SpringBootConfiguration + @EnableAutoConfiguration` — иначе слайс не делает `@ComponentScan` пакета и контроллеры не находятся.

**Решение:**
- В `adapter-rest/pom.xml` добавлена test-зависимость `spring-boot-starter-webmvc-test`.
- В `adapter-rest/src/test/java/.../TestApplication.java` — минимальный `@SpringBootApplication`-якорь (test-source, ArchUnit его не видит).
- Контроллеры — `public class` с `public` хендлерами.

---

## 7. Java 25 — где используем какую фичу

| Фича | Где (план/факт) |
|---|---|
| **Virtual threads** | `bootstrap/application.yml`: `spring.threads.virtual.enabled=true` (Spring Boot 4 GA). Tomcat и `@Async` автоматом на virtual. |
| **`StructuredTaskScope`** | Зарезервировано. В коде сбор per-repo sequential — обоснование в ADR-9. Виртуальный поток используется только для git stdout reader (`GitCliClient`). |
| **Scoped Values** | планируется в Сессии 6 — request-scoped `CorrelationId` для REST-логов. |
| **Stream Gatherers** | в Сессии 6 — недельная агрегация со скользящим окном (`Gatherers.windowFixed(7)` и пр.). |
| **Pattern matching for switch** | домен: `CollectionStatus`, в REST: обработка ошибок use case → HTTP-статус. |
| **Markdown в Javadoc** | по мере написания публичных портов. |

---

## 8. Принципы, которые держим

1. **Транзакции через port, не аннотации в use case.** Application не зависит от Spring (enforced ArchUnit). Когда use case'у нужна композитная атомарность (например cleanup + recompute), он явно оборачивает блок в `TransactionRunner.inTransaction(...)`. Adapter-методы оставляем с `@Transactional` для single-operation вызовов извне use case.
2. **Никаких DTO в `application`.** DTO — деталь REST-адаптера. Use case принимает и возвращает domain types.
3. **Адаптеры package-private.** Все классы адаптеров (кроме `@Configuration`-точек) с дефолтной видимостью — извне их никто не должен инстанцировать.
4. **Конструкторная инъекция везде.** `@Autowired` поля и сеттеры запрещены. Lombok `@RequiredArgsConstructor` — норма.
5. **Юнит-тесты на русском.** `@DisplayName` русский, `assertAll` для группировки, параметризация где уменьшает дублирование.
6. **N+1 — баг, а не оптимизация.** Любая операция «в цикле по списку» — повод для batch-метода в порту.

---

## 9. Открытые вопросы / TODO

- [ ] Расписание сбора: оставляем cron `0 0 1 * * ?` (как в v1) или переводим на `@Scheduled` в `bootstrap` с явным конфигом из yaml? Решим в Сессии 6/7.
- [ ] Миграция данных из v1 (если разворачиваем v2 поверх существующей БД) — нужно ли что-то перелить, или схема та же и всё подхватится? Проверим в Сессии 7.
- [ ] Эндпоинт `POST /api/v2/collection/runs` — синхронный (ждёт окончания) или возвращает 202 + id? Решение в Сессии 6.
- [ ] Авторизация REST — есть в проекте сейчас? Если да — переезжает в Сессии 6, если нет — отдельная задача.
- [x] **Отмена прогона сбора через API** (P1-2в) — **Этап 1 сделан** (ADR-12): `POST /collection/runs/{id}/cancel`, DB-backed флаг `cancel_requested`, checkpoints между репозиториями/фазами, статус `CANCELLED`. Advisory-лок снимается сам при развороте (try-with-resources). ⬜ **Этап 2 (опц.)** — interrupt-aware git-фаза для мгновенной реакции (сейчас отмена срабатывает на ближайшем checkpoint'е / по `command-timeout` текущей git-команды). Делать, только если ожидание окажется слишком долгим на практике.

---

## 10. История фиксов в v1, которые НЕ должны вернуться в v2

Эти проблемы были закрыты в v1 (задачи #1–#8), и архитектура v2 их предупреждает:

| Проблема в v1 | Как предотвращено в v2 |
|---|---|
| Race в `UserSyncHelper.findOrCreateByEmail` | `UnifiedUserRepository.findOrCreateAll(emails)` — один SQL, без гонки в коде |
| Hibernate batch не работал | Native `JdbcTemplate.batchUpdate` + `INSERT ... ON CONFLICT` |
| `daily_author_stats` unique constraint падал | Уникальный ключ `(email, date, repository_name)` + upsert по нему |
| Поля `hour`/`collectedAt`/`repositoryName` забывались в `CommitDetails` | Domain `Commit` — record, поля обязательны, забыть нельзя |
| `LastExportTracker` ломался при первой ошибке | `CollectionRun` — отдельные записи на каждый прогон, курсор берётся из последнего SUCCESS |
| N+1 в `saveDailyStatsForRepo` | Один upsert на весь батч |

---

## Фичи

После завершения рефакторинга работаем поверх готового каркаса. Каждая фича — отдельный раздел ниже.

### Фича 1 — Главный борд + ленивый Kaiten ✅

**Изменения требований:**
1. Запуск сбора по шедулеру убран — только ручной триггер с фронта (`POST /api/v2/collection/runs`).
2. При сборе **не** тянем карточки Kaiten. Только sync пользователей (`kaiten_id` + `avatarUrl` в `unified_user`).
3. На главном борде фронта — топ-N активных + N аутсайдеров за последние 30 дней.
4. Карточки Kaiten — **live при открытии профиля** (`GET /users/{email}/profile`), фильтр по `updatedAfter`.
5. Недельная статистика — без изменений, тоже без карточек.

**Что сделано:**

| Слой | Изменения |
|---|---|
| domain | Новый `Dashboard` record. В `StatsSummarizer` — новый `dashboard(period, stats, topN, outsiderN)` (pure-функция). |
| application | Новый `GetDashboardUseCase` + `GetDashboardService`. `CollectDailyStatsService` упрощён: вместо фазы карточек — sync `kaiten_user` + `unified_user.updateKaitenId(...)`. `GetUserProfileService` теперь дергает `KaitenGateway.fetchCardsForMember(...)` (live), а не `KaitenCardRepository`. |
| port out | В `KaitenGateway` добавлен `fetchCardsForMember(KaitenUserId, LocalDateTime)`. `KaitenCardRepository` оставлен как legacy на случай возврата к кэшу — но из use case-ов не используется. |
| adapter-kaiten | `KaitenGatewayAdapter.fetchCardsForMember` — поверх существующего `streamCards`. |
| adapter-rest | Новый `DashboardController` (`/api/v2/dashboard`) с дефолтом периода = last 30 days. `UsersController#profile` — `from`/`to` теперь опциональны, дефолт тот же. Новый `DashboardResponse` DTO. |
| тесты | `StatsSummarizerTest` — 3 новых сценария для `dashboard`. `CollectDailyStatsServiceTest` переписан под новый контракт (sync вместо cards). `QueryUseCasesTest` — заменён `KaitenCardRepository` на `KaitenGateway`, добавлена секция `DashboardService`. Новый `DashboardControllerTest` (MockMvc). Smoke-тест дополнен `GetDashboardUseCase`. |

**Что НЕ менялось:**
- Cron / `@Scheduled` — мы его и не добавляли в v2 (он был в открытых вопросах). Просто фиксируем: ручной триггер навсегда.
- Схема БД — `kaiten_card` таблица остаётся, просто не наполняется. Если решим окончательно убрать кэш карточек — отдельная задача с миграцией drop-table.
- Weekly stats — без изменений.

### Фича 2 — Paginated dashboard + enrichment + non-merge sort ✅

**Что хотел фронт:**
1. Дашборд возвращает **все** активные авторы paginated (для скролла/таблицы), а не только top-10 + outsider-10.
2. На карточке автора нужны `displayName` и `avatarUrl` из `unified_user` — чтобы не делать N запросов профиля.
3. Уточнение: в топе должны быть «реально работающие», а не тимлиды-мерджеры.

**Архитектурные решения:**

| Было | Стало |
|---|---|
| `Dashboard(period, List<AuthorSummary> topActive, List<AuthorSummary> outsiders)` | `Dashboard(period, Page<AuthorSummary> authors)` |
| Сортировка по `commits desc` (включая мерджи) | Сортировка по `nonMergeCommits desc` + tiebreak по email |
| `AuthorSummary(email, commits, …)` | `AuthorSummary(email, displayName, avatarUrl, commits, …)` + computed `nonMergeCommits()` |
| `StatsSummarizer.dashboard(period, stats, topN, outsiderN)` → `Dashboard` | `StatsSummarizer.activeAuthorsByActivity(stats)` → `List<AuthorSummary>` (pagination — детали use case) |
| `GetDashboardUseCase.get(period, topN, outsiderN)` | `GetDashboardUseCase.get(period, PageRequest)` |
| REST: `?topN=10&outsiderN=10`, ответ `{topActive, outsiders}` | REST: `?page=0&size=20`, ответ `{page, size, totalElements, totalPages, hasNext, items}` |

**Доменный примитив `Page<T>`** добавлен в `domain.common` — обёртка с `items + totalElements + page + size` и static-фабрикой `Page.of(allSorted, request)`. Spring `Page` мы наверх не таскаем (domain без Spring).

**Enrichment-стратегия:** профили (`displayName`, `avatarUrl`) подтягиваются из `unified_user` через новый `UnifiedUserRepository.findByEmails` — один SELECT `WHERE email IN (?)`. Делается **только для авторов текущей страницы**, не для всего набора — экономим запросы когда фронт листает большой dashboard.

**Тесты обновлены:** `StatsSummarizerTest` (новые сценарии под `activeAuthorsByActivity` с проверкой не-мердж сортировки), `QueryUseCasesTest.DashboardService` (paginated + enrichment + skip enrich при пустых stats), `DashboardControllerTest` (новый paginated contract с проверкой default 30 days + page/size override).

**Enrichment покрывает все три эндпоинта c `AuthorSummary`:**
- `/dashboard` — enrichment **только для текущей страницы** (экономим запросы).
- `/stats/weekly` — один batch-fetch на все недели сразу, потом per-week mapping.
- `/stats/summary` — top-N (≤10), тривиальный batch.

Общий helper [`AuthorSummaryEnricher`](application/src/main/java/ru/x5/markable/dev/analytics/application/service/AuthorSummaryEnricher.java) — два метода: `enrich(list)` для плоского случая и `batchEnricher(groups)` для weekly (общий fetch + раздача по группам).

`/stats/daily` — намеренно **без** enrichment: записей тысячи, цена не оправдана. Если фронту нужны аватары на дневном уровне — агрегирует по email на своей стороне и тянет профили через `/dashboard` или `/users/{email}/profile`.

### Фича 3 — Activity score (composite scoring) ✅

`AuthorSummary.activity` теперь содержит композитную метрику для оценки активности — закрывает кейс «много мелких коммитов = молодец» из v1. См. подробности в [API.md](./API.md#activity-score).

Domain:
- `ActivityCategory` enum: `INACTIVE / BELOW_AVERAGE / ACTIVE / STAR`.
- `ActivityScore(score, category, volumeFactor, qualityFactor, avgLinesPerCommit)`.
- `ActivityScorer.score(author, expectedCommits)` — pure-функция, piecewise-linear `qualityFactor` штрафует микро-коммиты (avg < 10 строк) и бомбы (avg > 200 строк).

Конфигурация: `scoring.expected-commits-per-30-days` в yml (default 50), масштабируется под длину запрошенного периода.

`GetDashboardService` теперь сортирует по `activity.score desc` (вместо `nonMergeCommits desc`). На остальных эндпоинтах `AuthorSummary.activity = null` (не считаем там, чтобы не путать семантику).

### Фича 4 — Consistency через recompute + rebase cleanup ✅

**Проблема в v1 (фиксы #6, #8) — рассинхрон между `commit_details` и `daily_author_stats`:**

1. **Инкрементальные UPSERT'ы перезаписывали значения.** Старая `persistCommitBatch` считала агрегат по `fresh` (новые коммиты этого батча) и делала `ON CONFLICT DO UPDATE SET commits = EXCLUDED.commits`. Если за один день за два сбора приходили коммиты A и B — после второго сбора в строке оставался **только B**, потому что UPSERT заменял, а не суммировал.

2. **Rebase + force-push порождал «zombie»-коммиты в `commit_details`.** Старый hash остаётся в БД после force-push, новый hash тоже сохраняется → один и тот же объём работы посчитан дважды.

В результате на `/users/{email}/profile` `summary.commits=15` при `commits[].length > 50`.

**Новый pipeline `CollectDailyStatsService.collectGitStats`:**

```
для каждого репо:
  seenHashes = {}
  streamCommits(repo, since, until, batch -> {
    persistCommitBatch(batch)          // только save в commit_details
    seenHashes ∪= batch.hashes
  })
  inDb = commitRepo.findHashesByRepoAndPeriod(repo, period)
  zombies = inDb − seenHashes           // rebase / force-push выкинул
  commitRepo.deleteByHashes(zombies)

// один SQL на всю партию — никаких инкрементальных UPSERT'ов
dailyStatsRepo.recomputeFromCommits(affectedAuthors, period)
```

**Новые out-port методы:**

- `CommitRepository.findHashesByRepoAndPeriod(repo, period)` — для cleanup zombies.
- `CommitRepository.deleteByHashes(hashes)` — bulk delete.
- `DailyStatsRepository.recomputeFromCommits(emails, period)` — native SQL: `DELETE WHERE email IN(...) AND date BETWEEN ?` + `INSERT FROM commit_details GROUP BY (LOWER(email), DATE(commit_date), repo)`.

**Принципы новой логики:**
1. **`daily_author_stats` — теперь чистое derived state из `commit_details`.** Источник правды один — таблица коммитов. Агрегат всегда консистентен с источником.
2. **`commit_details` — зеркало git'а** (для собранного периода). При сборе сравниваем хеши в БД с хешами в git → удаляем то, чего больше нет в git.
3. **Recompute — один SQL на сбор**, не per-batch. Дёшево, потому что delete + insert by index `(LOWER(email), date)` — sub-second даже для тысяч строк.

**Одноразовая миграция данных:** Liquibase `017-rebuild-daily-author-stats.yaml` — TRUNCATE daily_author_stats + INSERT FROM commit_details. Накатывается автоматически при старте после деплоя. Rebase-zombie в `commit_details` миграция не трогает — их вычистит сервис при следующем `POST /api/v2/collection/runs`.

**Тесты обновлены:** `CollectDailyStatsServiceTest` — happy path с проверкой recompute, отдельные сценарии cleanup zombies (есть/нет), git failure → нет recompute, kaiten failure изолирован, нет коммитов → нет recompute.

---

## ADR-9. Честная переоценка после architecture review

Этот раздел — post-mortem на собственные обещания. После полного review нашёл расхождения между «как продавали в README» и «как сделано в коде». Фиксирую здесь, чтобы не маркетинговать архитектуру в большей степени, чем она того заслуживает.

### Расхождение 1 — `StructuredTaskScope` обещан, в коде sequential

**В трекере и README** значился пункт «adapter-git: GitGatewayAdapter + StructuredTaskScope» — fan-out по репозиториям через структурную конкуррентность. **В коде:** `CollectDailyStatsService.collectGitStats()` — обычный `for (RepoName repo : repos)`.

**Почему не делаем параллельно сейчас:**

1. **Distributed lock через advisory lock — single-writer семантика.** Два параллельных сбора уже отсечены на уровне `pg_try_advisory_lock`. Параллелизм внутри одного сбора (по репам) — другая ось, но менее ценная.
2. **Per-repo атомарная tx (cleanup + recompute) держит row-level locks.** Если бы два потока параллельно делали recompute разных репо но пересекающихся авторов — они блокировались бы друг на друге через DELETE+INSERT в `daily_author_stats`. Получили бы deadlock'и, а не speedup.
3. **Узкое место сбора — `git fetch` / `git log`**, а не CPU. Параллелизм добавил бы только если git'у бы хватало bandwidth/CPU на host'е. Это проверяется бенчмарком, которого у нас не было.

**Если в будущем понадобится:**
- Параллелить нужно `git fetch` (download bound) — отдельная фаза перед стримом логов.
- Recompute оставить sequential (или агрегировать в финальную одну tx на весь сбор — но тогда теряем per-repo атомарность отказа).

В коде это пометили честно (см. `GitGatewayAdapter` javadoc, README принцип 5).

### Расхождение 2 — это не «rich hexagonal с DDD»

В первоначальных доках упоминалось «hexagonal architecture» в контексте, который намекал на rich domain model. **Реальность:**

- `domain.model.*` — это **value objects** (Email, Period, RepoName, CommitHash, TaskNumber) с инвариантами в конструкторе. Это правильно и работает.
- `domain.model.stats.*` — DTO-records без поведения (`DailyAuthorStats`, `AuthorSummary`, `WeeklyStats`, `PeriodSummary`, `Dashboard`).
- `domain.service.*` — pure-функции (`StatsSummarizer`, `ActivityScorer`, `CommitMessageParser`, `TestFileDetector`). Не "rich" — это **transaction scripts** в чистом виде.
- Реальная бизнес-логика **распределена**: часть в SQL (`RECOMPUTE_SQL` агрегирует и фильтрует), часть в domain (`ActivityScorer`), часть в use case (`GetUserProfileService.filterByRelevance`), часть в record-helpers (`AuthorSummary.nonMergeCommits()`).

**Это нормально для аналитического сервиса.** Аналитика — это transformation pipeline, не bounded context с инвариантами агрегата. Rich domain model был бы здесь over-engineering.

**Что мы реально получили:**
- **Ports & adapters** изоляцию (заменяемые адаптеры, тесты без I/O) — да, это работает.
- **Direction enforcement** через ArchUnit — да, держит границы.
- **Не rich domain** — и слава богу, не нужен здесь.

«Hexagonal» в README — это про ports & adapters, не про DDD. Поправлено.

### Расхождение 3 — `domain.common.Page` / `PageRequest` — это не domain primitives

`Page<T>` и `PageRequest` живут в `domain.common.*`, но это не доменные понятия — это **query-level** примитивы (так же как `Period` для запросов, или `PageRequest` Spring Data). Они в domain потому что use case-ы возвращают `Page<AuthorSummary>` через port, а port не должен зависеть от Spring.

Это не баг и не leakage в строгом смысле — это **прагматичное решение**: альтернатива (тащить Spring Data `Page` в application через `application/port/...`) хуже. Но называть их "domain entities" нечестно — это **shared kernel** для query-уровня.

### Что нужно было бы сделать иначе, если бы делал заново

- Не маркетинговать "rich domain" и "DDD" — оба термина создают неверные ожидания.
- Не обещать `StructuredTaskScope` пока не доказан profile-driven выигрыш.
- 8 Maven-модулей для приложения (не библиотеки) — overkill. Хватило бы 4: `domain` + `application` объединить в `core`, `adapter-rest` + `adapter-persistence` + `adapter-external` (git + kaiten) объединить, плюс `bootstrap`. ArchUnit держит границы — Maven boundaries дублируют это с лишним build-overhead. Менять сейчас уже дорого — оставлено как есть.


---

## Architecture review — выполненные фиксы

После Фичи 4 провели жёсткое principal-level architecture review (см. ADR-9 выше — context).
Ниже — все 20 идентифицированных проблем и что с ними сделали.

### CRITICAL (security / data loss / системные риски)

| # | Проблема | Что было | Что стало | Файлы |
|---|---|---|---|---|
| 1 | Git token leak | Токен в URL, в `Arrays.toString(command)`, в `.git/config` | `GIT_ASKPASS` через POSIX-скрипт + env var, маскирование `://user:pass@` в exception | `GitCliClient`, `GitCommandFailedException`, `GitProperties` |
| 2 | OOM на больших репо | `git log` копил весь stdout в `ArrayList<String>` | Push-stream через `GitLogParser.Streaming`, virtual thread reader, ring-buffer tail (50 строк) | `GitCliClient`, `GitLogParser`, `GitGatewayAdapter` |
| 3 | Нет таймаутов на `Process.waitFor()` | Зависший git подвешивал сборку навсегда | `commandTimeout` (default 30m), `destroyForcibly` + grace; virtual thread обходит блокировку `readLine` | `GitCliClient`, `GitProperties` |
| 4 | `StructuredTaskScope` обещан, в коде sequential | Доки маркетинговали fan-out которого не было | Доки честно говорят что sequential per-repo — обоснование в ADR-9 | `README.md`, `REFACTORING.md` |
| 5 | Транзакционные границы — partial failure landmine | `saveAll` + `cleanup` + `recompute` — каждое своя tx | Новый port `TransactionRunner`, финальная per-repo tx { cleanup + recompute } через `inTransaction()` | `TransactionRunner` (port), `SpringTransactionRunner`, `CollectDailyStatsService` |
| 6 | Параллельный сбор → race в git cache + двойной DELETE/INSERT | Никакого lock'а | `pg_try_advisory_lock` + `CollectionLock` port + `CollectionAlreadyRunningException` → 409 | `CollectionLock` (port), `PgAdvisoryCollectionLock`, `CollectDailyStatsService`, `ApiExceptionHandler` |

### HIGH (architectural quality / consistency)

| # | Проблема | Что было | Что стало | Файлы |
|---|---|---|---|---|
| 7 | "Rich hexagonal с DDD" — маркетинг | Доки преувеличивали | ADR-9 "Честная переоценка": ports & adapters над transaction scripts, не DDD | `REFACTORING.md`, `README.md` |
| 8 | `AuthorSummary` 2 конструктора + дубль-геттеры | 9-arg + 8-arg + `name()/avatar()/activityOptional()` | Один constructor, удалены 4 dead-метода | `AuthorSummary` + 7 callsite'ов |
| 9 | `KaitenGatewayAdapter` ручное пересоздание `KaitenCard` + hardcoded URL | 14 строк ручного `new KaitenCard(...)` + `"https://kaiten.x5.ru/"` в коде | `KaitenCardMapper.toDomain(dto, webBaseUrl)` через MapStruct expression + `KaitenProperties.webBaseUrl` | `KaitenCardMapper`, `KaitenGatewayAdapter`, `KaitenProperties`, `application.yml` |
| 10 | Двойной `prepare()` git fetch на репо | Use case + адаптер оба звали `cli.prepare(url)` | Удалён `prepare()` из порта `GitGateway`, `streamCommits` сам инкапсулирует подготовку | `GitGateway` (port), `CollectDailyStatsService`, `GitGatewayAdapter` |
| 11 | `MAX(cd.user_id)` — недетерминированный FK | Case-sensitive дубли + случайный user_id из MAX | Liquibase 020 (dedup + LOWER normalize + UNIQUE INDEX), `RECOMPUTE_SQL` subquery от source of truth | migration 020, `DailyStatsRepositoryAdapter`, `UnifiedUserRepositoryAdapter` |
| 12 | `Email.toLowerCase()` в адаптере | 4 избыточных `.toLowerCase()` (я сам добавил их в #11) | Подтверждение: `Email` нормализует **в конструкторе** уже. Убрали redundant, добавили инвариант в JavaDoc | `Email`, `UnifiedUserRepositoryAdapter`, `DailyStatsRepositoryAdapter` |
| 13 | `/profile` live Kaiten без кэша + без HTTP-таймаутов | Каждый запрос — paginated round-trip к Kaiten | Caffeine cache TTL 5m + connect/read timeouts (5s/30s) | `KaitenGatewayAdapter` `@Cacheable`, `KaitenProperties`, `KaitenAdapterConfig`, `application.yml` |

### MEDIUM (cleanup / hardening)

| # | Проблема | Что было | Что стало | Файлы |
|---|---|---|---|---|
| 14 | ArchUnit покрывает 20% полезного | 3 правила (layered + bans × 2) | 14 правил: ports interfaces, domain immutable, framework annotations в правильных пакетах, legacy API bans (Date/Calendar, System.out) | `HexagonalArchitectureTest` |
| 15 | `CollectDailyStatsService` — god service с 8 deps | Lock + lifecycle + git + kaiten + link — 5 ответственностей | Orchestrator (4 deps) + `CollectGitStatsService` worker (5 deps) + расширенный `SyncKaitenUsersService` (3 deps) | `CollectGitStatsUseCase` (port), `CollectGitStatsService`, `CollectDailyStatsService`, `SyncKaitenUsersService` |
| 16 | 8 Maven-модулей — overkill для приложения | (как было) | **Не меняем** — ArchUnit держит границы. См. ADR-9 | (none) |
| 17 | Dead schema `kaiten_card` + 6 классов адаптера | Карточки live, но persistent storage остался лежать | Удалены 8 файлов + Liquibase 021 (DROP TABLE) | удалены 8 файлов, migration 021 |
| 18 | `weekStart()` использует `LocalDate.now()` | Pure-функция с скрытой зависимостью от "сегодня" | `LocalDate.of(year, 1, 4).with(ISO.dayOfWeek, 1).plusWeeks(week - 1)` — детерминированно | `StatsSummarizer`, regression test |
| 19 | `KaitenRateLimiter.pauseUntil` race | `volatile long` + прямая запись (две паузы — теряется большая) | `AtomicLong` + `updateAndGet(Math::max)`, reserve-slot pattern без `synchronized` | `KaitenRateLimiter` |
| 20 | `handleAny` — 500 для всех ошибок | Upstream Kaiten = тот же 500 что наш баг | 502 для upstream errors, 504 для timeouts, 500 только для **наших** ошибок | `ApiExceptionHandler`, новые 7 тестов |

### Бонусные мелкие фиксы по пути

- **`urn:markable:*` → `urn:devpulse:*`** в `ApiExceptionHandler` — пропущенный rename из переименования проекта.
- **`GetUserProfileUseCase.Profile` nested record → `UserProfile` в `domain.model.stats`** — следствие нового ArchUnit-правила #14 "ports — только interfaces". Логичный дом рядом с `Dashboard`/`WeeklyStats`/`PeriodSummary`.
- **`KaitenHttpClient` package-private → public** — чтобы кэш-IT в bootstrap мог его мокать через `@MockitoBean`. Скромная утечка ради тестируемости.

### Что НЕ делали и почему

1. **8 Maven-модулей → 4** (#16): инвазивный рефакторинг сборки, не даёт ничего что не дают ArchUnit-правила. ROI отрицательный сейчас.
2. **Sealed `AuthorSummary` (`Plain`/`Scored`)** (#8): types-as-states корректнее, но overkill для одного nullable поля. Минимизация surface вместо иерархии.
3. **Распределённый Redis-кэш для Kaiten** (#13): in-memory Caffeine достаточен для текущей нагрузки (1-2 инстанса). Под multi-pod — добавим Redis отдельным шагом.
4. **`commit_details.kaiten_card_title` колонка** (рядом с #17): теоретически мёртвая, но удаление = миграция с сохранением данных. Не в скоупе review.

### Post-mortem: четыре итерации `RECOMPUTE_SQL` для маппинга user_id

История попыток получить user_id из unified_user внутри INSERT...SELECT с GROUP BY:

**Попытка 1:** correlated subquery с прямой ссылкой на outer.
```sql
(SELECT u.id FROM unified_user u WHERE LOWER(u.email) = LOWER(cd.email) LIMIT 1)
```
Postgres ругается: *"subquery uses ungrouped column cd.email from outer query"* — не
распознаёт что выражение в subquery текстуально совпадает с тем что в GROUP BY. ❌

**Попытка 2:** correlated subquery с outer alias `email`.
```sql
SELECT LOWER(cd.email) AS email, ...
  (SELECT u.id FROM unified_user u WHERE LOWER(u.email) = email LIMIT 1) AS user_id
```
SQL компилируется. Эту форму я (reviewer) ошибочно одобрил как корректную, поверив
в alias resolution к outer scope. **Не так**: внутри subquery `email` сначала ищется
в local FROM. `unified_user u` имеет колонку `email` → shadowing. Условие становится
`LOWER(u.email) = u.email`, после миграции 020 (emails lowercase) истинно для всех →
LIMIT 1 = первый user_id по plan order. Все строки получили **один и тот же** случайный
user_id. Сбор не падал → симптом visible только в дашборде/профиле. Поймал
регрессионный тест `DailyStatsRepositoryAdapterIT.recomputeAssignsCorrectUserId`. ❌

**Попытка 3:** alias `email_lower` (имя НЕ существует в `unified_user`).
```sql
SELECT LOWER(cd.email) AS email_lower, ...
  (SELECT u.id FROM unified_user u WHERE LOWER(u.email) = email_lower LIMIT 1) AS user_id
```
Я предположил что Postgres резолвит outer SELECT alias через extension к standard SQL.
**Не так**: PostgreSQL **не** разрешает aliases из outer SELECT в subquery. Падает с
*"column 'email_lower' does not exist"*. Subquery scope в Postgres строго standard. ❌

**Попытка 4 (текущая):** `LEFT JOIN unified_user u ON LOWER(u.email) = LOWER(cd.email)`,
`MAX(u.id)` в SELECT.
```sql
SELECT
    LOWER(cd.email) AS email,
    ..., MAX(u.id) AS user_id
FROM commit_details cd
LEFT JOIN unified_user u ON LOWER(u.email) = LOWER(cd.email)
WHERE ...
GROUP BY LOWER(cd.email), CAST(cd.commit_date AS DATE), cd.repository_name
```
Standard SQL. JOIN добавляет `u.id` к каждой строке cd. `UNIQUE INDEX uq_unified_user_email_lower`
(миграция 020) гарантирует ровно один матч на `LOWER(cd.email)` → `MAX(u.id)` = тот
самый id, детерминированно. `LEFT JOIN` сохраняет cd rows без матчинг'а (user_id = NULL —
nullable FK). ✓

**Уроки:**
1. PostgreSQL aliases из outer SELECT **не доступны** в subquery WHERE — это standard SQL.
2. В correlated subquery shadow'ятся outer references локальными колонками — нужно
   ВНИМАТЕЛЬНО проверять что имя alias не пересекается ни с одной колонкой в FROM любого
   subquery.
3. При сомнениях — `LEFT JOIN` + `MAX/MIN` (с UNIQUE constraint для детерминированности)
   проще correlated subquery и работает в standard SQL без edge cases.
4. **Регрессионный test обязательно нужен** для семантических SQL-фиксов. Compile-валидный
   запрос ≠ корректный запрос.

### Метрики

- Новых файлов: ~20 (порты, services, мигрions, тесты, **`UserProfile`**, `ADR-9`).
- Обновлённых: ~30.
- Liquibase миграций добавлено: 2 (019 functional indexes уже была, 020 unified_user cleanup, 021 drop kaiten_card).
- ArchUnit правил: 3 → 14.
- Зависимостей у самого большого service'а: 8 → 4 (orchestrator).
- Новых тестов: ~40 (включая 7 для ApiExceptionHandler, 5 для GitGatewayAdapter, 5 для CollectGitStatsService, 6 для CollectDailyStatsService, 4 для KaitenCardsCacheIT, 4 для GitCommandFailedException).


---

## Architecture review #2 — новый backlog (после reviews-фичи)

> **Статус:** backlog закрыт ✅. Все P0/P1/P2 сделаны; P2-4/P2-5 — осознанные won't-fix/watch,
> P1-2в (отмена прогона через API) — открытый вопрос (см. раздел «Открытые вопросы»).
> Легенда: ✅ done · 🟡 in progress · ⬜ todo

Второе principal-level review (уже **после** Фичи reviews/GitLab и всех 20 фиксов выше)
нашло расхождения между гарантиями в javadoc/README и фактическим кодом. Главное: часть
обещаний про scaling («миллионы коммитов без OOM», «batch insert») в текущем коде **не
выполняется**. Ниже — приоритизированный backlog. P0 — то, что ломается в проде первым.

### Контекст: что уже хорошо и НЕ трогаем

- Per-phase изоляция сбора (git/kaiten/reviews в разных try/catch) — оставляем.
- `pg_try_advisory_lock` single-writer + 409 — оставляем.
- Functional-индексы под `LOWER(email)`/`CAST(date)` + `FunctionalIndexesIT` — оставляем.
- Set-based `RECOMPUTE_SQL` (`GROUP BY`) вместо построчного upsert — оставляем.
- `KaitenRateLimiter` reserve-slot lock-free — оставляем (кроме мелочи в P2-1).
- ArchUnit-правила — оставляем.

### P0 — ломается первым в проде (противоречит заявленным гарантиям)

| # | Проблема | Почему критично | План фикса | Файлы | Статус |
|---|---|---|---|---|---|
| P0-1 | **`GenerationType.IDENTITY` убивает JDBC batch inserts** | `hibernate.jdbc.batch_size=100 / order_inserts=true` в `application.yml` — **мёртвая конфигурация**: при IDENTITY Hibernate не может батчить INSERT (обязан вернуть id после каждой строки). `saveAll(500)` = 500 round-trip'ов. На «миллионах коммитов» (заявка в javadoc `GitCliClient`/`GitGatewayAdapter`) это бутылочное горло записи. | ✅ Сделано для `commit_details` (hot-path): `saveAll` → native `JdbcTemplate.batchUpdate` (обходит IDENTITY), `reWriteBatchedInserts=true` через Hikari data-source-properties, удалён мёртвый `toEntity`. **ADR-11.** `mr_review` native insert вынесен в P1-3 (тот же адаптер). | `CommitRepositoryAdapter`, `CommitEntityMapper`, `application.yml` | ✅ |
| P0-2 | **Zombie-detection грузит ВСЕ хеши репо в heap** — противоречит O(1)-памяти | `findHashesByRepoAndPeriod` тянул все хеши репо за период в `HashSet<CommitHash>`; плюс `seenInGit` копил все хеши прогона. Финальная cleanup-tx материализовала миллионы строк → OOM. | ✅ Сделано: **mark-and-sweep по `collected_at`** (лучше, чем черновой temp-table/`unnest`). Existing-коммиты помечаются `markSeen(runMark)`, sweep `deleteZombies(repo, period, runMark)` считает set-разность в БД. `seenInGit` удалён → heap O(batch). Индекс `idx_commit_details_repo_date` (миграция 027). IT `markAndSweepZombies`. | `CommitRepository`, `CommitDetailsJpaRepository`, `CommitRepositoryAdapter`, `CollectGitStatsService`, migration 027 | ✅ |
| P0-3 | **Per-repo recompute → O(K²) write amplification** | `recomputeFromCommits` делает `DELETE ... WHERE email=ANY(...)` **без фильтра по репо** и зовётся в цикле per-repo. Автор, активный в K репо, переписывает свои daily-строки по всем K репо — K раз за прогон. При 1000 репо и тимлидах-в-десятках-репо это заметно. | ✅ Сделано: `recompute` вынесен из per-repo цикла в `collect()` — один вызов над `allAffected` в конце прогона. Sweep остаётся per-repo. Trade-off (потеря per-repo атомарности recompute) зафиксирован в **ADR-10**. Тест `singleRecomputeForWholeRun`. | `CollectGitStatsService` | ✅ |

### P1 — корректность / операционные риски

| # | Проблема | Почему важно | План фикса | Файлы | Статус |
|---|---|---|---|---|---|
| P1-1 | **Дубль `findOrCreateAll` + leak оркестрации в адаптер** | `CollectGitStatsService.persistCommitBatch` зовёт `findOrCreateAll`, и `CommitRepositoryAdapter.saveAll` зовёт его **ещё раз**. Двойная работа + адаптер persistence управляет identity (use-case concern). | ✅ Сделано: `saveAll(commits, Map<Email,Long>)` принимает готовый маппинг, адаптер только пишет; `findOrCreateAll` остался единственной точкой в use-case; убрана зависимость `UnifiedUserRepository` из адаптера. | `CommitRepository` (port), `CommitRepositoryAdapter`, `CollectGitStatsService` | ✅ |
| P1-2 | **Сбор нельзя отменить; reviews-fan-out без общего deadline** | `POST /collection/runs` запускает операцию на часы, держит advisory lock + connection, отмены через API нет. `ReviewGatewayAdapter` `ExecutorService.close()` ждёт все задачи без таймаута. Ошибки отдельных MR молча проглатываются. <br>**Уточнение по ходу:** одиночный MR НЕ виснет вечно — GitLab HTTP-клиент имеет read timeout 30s, а rate-limiter bounded (max 5 retry, backoff ≤60s). Реальный риск — деградация GitLab × десятки тысяч MR = часы под локом. | ✅ (а)+(б) Сделано: `project-review-timeout` (default 30m, `0s`=без границы) — по истечении недособранные MR отменяются, собранные сохраняются; потери логируются **агрегатным** WARN (`failed`+`dropped`) вместо тихого per-MR drop. <br>⬜ (в) **Открытый вопрос:** отмена прогона через API (`DELETE /collection/runs/{id}`) — отдельная фича (нужен interrupt-aware сбор + снятие advisory-лока), вынесена в «Открытые вопросы». | `ReviewGatewayAdapter`, `GitlabProperties`, `application.yml` | 🟡 |
| P1-3 | **N+1 в reviews upsert** (+ `mr_review` native insert из P0-1) | `findByGitlabProjectIdAndGitlabMrIid` в цикле по каждому MR — десятки тысяч точечных SELECT на backfill. Плюс `mr_review` на IDENTITY → `reviewJpa.saveAll` без батчинга. | ✅ Сделано: batch-lookup `findByGitlabProjectIdInAndGitlabMrIidIn` (суперсет + фильтр по композитному ключу `MrKey`) вместо N+1; `mr_review` insert → native `JdbcTemplate.batchUpdate` (см. ADR-11). IT `batchLookupDistinguishesSameIidAcrossProjects`. | `MergeRequestJpaRepository`, `ReviewWriteRepositoryAdapter` | ✅ |

### P2 — hardening / честность документации

| # | Проблема | План фикса | Файлы | Статус |
|---|---|---|---|---|
| P2-1 | `KaitenRateLimiter` парсит `Retry-After` только как integer-секунды, игнорит HTTP-date форму | ✅ Сделано: `retryAfterMillis` парсит обе формы RFC 7231 (delta-seconds + RFC 1123 HTTP-date, кламп в прошлом → 0); fallback на exp-backoff если ни то, ни другое. Метод стал package-private static, прямой юнит-тест `parsesRetryAfterForms`. | `KaitenRateLimiter` (+ тест) | ✅ |
| P2-2 | Нет видимости stale-окна daily_stats (краш между батчем и recompute) | ✅ Сделано: (а) Micrometer gauge `devpulse.collection.staleness.seconds` (секунд с `until` последнего успешного сбора, NaN если не было) — `ObservabilityConfig` в bootstrap, видно в `/actuator/metrics`. (б) счётчик закрытых карточек без `closedAt` в perf-review логируется (`PerformanceReviewAssembler.closedCardsMissingClosedAt` + INFO в сервисе). | `ObservabilityConfig`, `PerformanceReviewAssembler`, `PerformanceReviewService` | ✅ |
| P2-3 | Нагрузочных тестов под scaling-заявки нет | ✅ Сделано: correctness-at-scale IT `scaleInsertAndSweep` (3000 коммитов одним `saveAll` → native batch insert; markSeen 2000 + sweep → ровно 1000 зомби удалено). Память O(batch) гарантирована дизайном (нет материализации хешей в `Set`) — это валидируется аргументом, а не замером heap (надёжно heap в JUnit не измерить). Микро-benchmark insert не делаем — флаки в CI. | `CommitRepositoryAdapterIT` | ✅ |
| P2-4 | Query-side ceremony: 18 интерфейс+сервис+@Bean для pass-through чтений | **Won't-fix сейчас.** Зафиксировать как осознанный trade-off (как #16 с модулями). Свернём, только если станет тормозить разработку | (none) | ⬜ |
| P2-5 | `git.repositories` связывает adapter-git и adapter-reviews через общий property | **Watch.** Развязать, только когда появится не-GitLab git-хостинг | (none) | ⬜ |

### Порядок исполнения

1. ✅ **P1-1** (дубль `findOrCreateAll`) — самый дешёвый и безопасный, разогрев.
2. ✅ **P0-2** (zombie mark-and-sweep) — чинит OOM, локализован в per-repo tx.
3. ✅ **P0-3** (single recompute) — трогает тот же `CollectGitStatsService`, логично сразу после P0-2.
4. ✅ **P0-1** (native batch insert commit_details + reWriteBatchedInserts). `mr_review` → P1-3.
5. ✅ **P1-3** (N+1 reviews + mr_review native insert). 🟡 **P1-2** (deadline reviews сделан; отмена прогона — открытый вопрос).
6. ✅ **P2-1** (Retry-After HTTP-date). ✅ **P2-2** (observability: staleness gauge + closedAt-счётчик). ⬜ P2-3 (нагрузочный IT) — по мере сил.

> ⚠️ **Build-замечание:** полный `mvn verify` требует доступа к GitHub Packages (OAS-контракты)
> + Docker (Testcontainers). Если локально нет — каждый шаг проверяем точечно (`mvn -pl <module> -am test`)
> и финально гоняем CI. Коммиты атомарные, чтобы откат любого шага был дешёвым.

---

## ADR-10. Один recompute на прогон вместо per-repo (P0-3)

**Контекст.** `DailyStatsRepositoryAdapter.recomputeFromCommits` пересобирает `daily_author_stats`
для набора email через `DELETE ... WHERE LOWER(email)=ANY(?) AND date BETWEEN ?` + `INSERT ...
SELECT FROM commit_details`. Важно: **DELETE не фильтрует по репозиторию** — он сносит daily-строки
автора по ВСЕМ его репозиториям в периоде, затем `INSERT` восстанавливает их из `commit_details`
(`GROUP BY email, date, repo`).

Раньше `recompute` звался **внутри per-repo цикла** (`CollectGitStatsService.collectOneRepo`).
Следствие: автор, активный в K репозиториях, при сборе получал K полных delete+reinsert своих
daily-строк по всем K репо — **O(K²) записи** для cross-repo-активных людей (тимлиды, коммитящие
в десятки репо). При 1000 репозиториев это заметная write amplification.

**Решение.** `recompute` вынесен из цикла: per-repo остаётся только sweep зомби
(`deleteZombies`, scoped по репо), а `recomputeFromCommits(allAffected, period)` вызывается
**один раз** после обработки всех репозиториев — над объединением затронутых авторов. Каждый автор
пересобирается ровно один раз → линейно от числа строк, без O(K²).

**Trade-off (осознанный).** Теряем per-repo атомарность пересчёта. Раньше каждый репо был
«всё-или-ничего» по своему recompute; теперь recompute — последний шаг прогона:
- Если прогон падает на репо N, `commit_details` уже частично обновлён (репо 1..N), но
  `daily_author_stats` ещё НЕ пересобран — он останется **stale до retry**.
- Это компенсируется существующей семантикой: курсор двигает только `SUCCESS`, упавший прогон
  фиксируется `FAILED`, следующий прогон стартует с того же `since`, пересобирает `commit_details`
  (дубли отсекает `findExistingHashes`) и делает recompute заново. Идемпотентно.

Окно неконсистентности `daily_stats` (между падением и retry) — то же, что уже задокументировано
в P2-2 (наблюдаемость stale-окна — отдельный пункт). Для аналитического сервиса, где сбор ручной
и повторяемый, это приемлемо; цена O(K²) при 1000 репо — нет.

**Почему не оставили per-repo recompute с фильтром по репо.** Можно было бы добавить
`AND repository_name = ?` в DELETE и пересобирать только текущий репо. Но `recompute` логически
работает по ключу `(email, date)` агрегата, и фильтр по репо усложнил бы инвариант «daily_stats —
чистое derived state из commit_details» (Фича 4). Один проход в конце — проще и быстрее.

---

## ADR-11. Native batch insert для commit_details вместо JPA saveAll (P0-1)

**Контекст.** `CommitDetailsEntity` (как и большинство сущностей) использует
`@GeneratedValue(strategy = IDENTITY)`. При IDENTITY Hibernate **не может** батчить INSERT: ему
нужно вернуть сгенерированный id после каждой строки, поэтому `saveAll(500)` шёл 500 отдельными
round-trip'ами. При этом в `application.yml` стоят `hibernate.jdbc.batch_size=100 /
order_inserts=true` — для `commit_details` это была **мёртвая конфигурация**. На «миллионах
коммитов» (заявка в javadoc стрима) запись становилась узким местом.

**Решение.** `commit_details` пишется append-only, сгенерированный id обратно не нужен. Поэтому
`CommitRepositoryAdapter.saveAll` переведён на native `JdbcTemplate.batchUpdate` с явным
`INSERT ... VALUES (?,…)` (14 колонок), `batchSize=500`. Это:
- обходит IDENTITY-ограничение Hibernate (батчинг реально работает);
- убирает dirty-checking и построение persistence-context на hot-path;
- держится принципа ADR-6 («bulk через JdbcTemplate, не Hibernate»).

Дополнительно в JDBC включён **`reWriteBatchedInserts=true`** (через
`spring.datasource.hikari.data-source-properties`) — pgjdbc переписывает серию single-row INSERT
в один multi-row INSERT. Без него даже `batchUpdate` шлёт по строке за раз. Заданием через
data-source-properties (а не в URL) гарантируем, что флаг применится независимо от того, как
задан `DB_URL` в проде.

Мёртвый `CommitEntityMapper.toEntity` удалён — маппер остался только для чтения (`toDomain`).
Сама `CommitDetailsEntity` и её IDENTITY-id оставлены: для **чтения** (JPA-проекции, `findByAuthor`)
это корректно и менять схему незачем.

**Не входит в P0-1 (вынесено в P1-3).** `mr_review` тоже на IDENTITY и пишется
`reviewJpa.saveAll`, а `merge_request` требует возврата id для связки ревью. Их перевод на native
insert логично делать вместе с устранением N+1 в `ReviewWriteRepositoryAdapter` (P1-3) — это один
и тот же адаптер и одна транзакционная логика. Здесь не трогаем, чтобы коммит был сфокусирован на
самом горячем пути (commit_details = миллионы строк против десятков тысяч у reviews).

**Что НЕ делали.** `unified_user` тоже IDENTITY, но это few-rows-per-run (по новому автору) —
не hot-path, оставлено на JPA. `hibernate.jdbc.batch_size` оставлен: он ещё помогает остальным
JPA-операциям (`order_updates` и т.п.).

---

## ADR-12. Отмена прогона сбора — кооперативная, через DB-флаг (P1-2в, Этап 1)

**Контекст.** `POST /collection/runs` запускается на минуты-часы (синхронно), держит advisory-лок
+ connection, прервать извне было нельзя. Нужна отмена без насильного убийства потока и без
рассинхрона данных.

**Решение (Этап 1).**
- **Контракт (OAS 2.1.0):** `POST /collection/runs/{id}/cancel` → 202 (run в RUNNING с поднятым
  флагом; отмена асинхронна, финал наблюдать через GET), 404 (нет прогона), 409 (терминальный).
  `CollectionRun.status += CANCELLED`.
- **DB-backed флаг** `collection_run.cancel_requested` (миграция 028). Cancel-эндпоинт ставит флаг
  на любом инстансе (быстрый UPDATE, лок не нужен) → работает кросс-под без sticky-routing.
- **Checkpoints:** git-фаза проверяет `CancellationSignal` перед каждым репозиторием; оркестратор —
  после git и перед фиксацией SUCCESS. При отмене обход прекращается, **recompute пропускается**,
  бросается внутренний `CollectionCancelledException` → оркестратор помечает `CANCELLED`.
- **Сигнал:** `CancellationSignal` (port/in, `() -> collectionRunRepository.isCancelRequested(runId)`),
  прокидывается в git-фазу. Прямой SELECT на checkpoint'е — дёшево (PK-lookup, ≤ числа репо за прогон).
- **Advisory-лок** снимается сам: отмена доводит поток до `return`, try-with-resources закрывает
  handle → `pg_advisory_unlock`. Отдельной логики не нужно.

**Консистентность = как у FAILED.** `CANCELLED ≠ SUCCESS` → курсор (`findLastSuccessfulUntil`) его
игнорирует → следующий сбор стартует с того же `since`, mark-and-sweep + recompute доводят
`commit_details`/`daily_stats`. Частичная запись и stale daily_stats — то же окно, что у FAILED
(ADR-10), не новый риск.

**Companion-эндпоинт `GET /collection/runs/latest` (OAS 2.2.0).** Без него cancel из UI
бесполезен: `POST /collection/runs` синхронный и отдаёт id только в конце, а refresh/другая
вкладка/другой юзер этот id теряют. `/latest` возвращает самый свежий прогон по `startedAt`
(идущий = самый свежий, т.к. single-flight) — фронт на загрузке экрана узнаёт «идёт ли сбор +
его id» (для poll и cancel) либо последний результат. `CollectionRunRepository.findLatest`
(`findFirstByOrderByStartedAtDesc`) + `GetCollectionRunUseCase.findLatest` + controller. Литерал
`/runs/latest` Spring роутит раньше шаблона `/runs/{id}` (тест это проверяет).

**Что осознанно НЕ делали (Этап 2, опц.).** In-memory registry + `Thread.interrupt()` для
мгновенного прерывания текущей git-команды. Сейчас отмена срабатывает на ближайшем checkpoint'е
или по `command-timeout` (≤30m) текущей команды. Interrupt-путь добавим, только если ожидание
окажется болезненным на практике — он требует аккуратности с пулом connection'ов при interrupt
во время JDBC, поэтому не в Этапе 1.

**Гексагональность.** Cancel-исключение для 409 — в `port.out` (прецедент:
`CollectionAlreadyRunningException`). `CancellationSignal`/`CancelCollectionUseCase` — `port.in`
(интерфейсы, ArchUnit-чисто). `CollectionCancelledException` — внутренний control-flow в
`application.service` (final, не покидает слой). Флаг `cancel_requested` — не часть доменного
`CollectionRun` (run-control сигнал), маппером не трогается (`@Mapping(ignore)`).

---

## ADR-13. Startup-реконсиляция осиротевших RUNNING-прогонов (review #2)

**Контекст.** Процесс упал/убит mid-run (OOM, `kill -9`, деплой) → запись `collection_run`
остаётся в `RUNNING` навсегда (некому перевести в терминал). Advisory-lock Postgres отпускается
(смерть сессии), новый сбор стартует, но `GET /collection/runs/latest` отдаёт фантом как «сбор
идёт» бесконечно, а cancel по нему мёртв (некому прочитать флаг). Review #2 показало: пока поллинг
на фронте был выпилен — пробел был невидим; `/latest` сделал его вредным для UI.

**Решение (multi-instance-safe).** Политика — в `StartupReconciliationService` (application.service,
Spring-free, Lombok); Spring-триггер `StartupReconciliationTrigger` (bootstrap,
`@EventListener(ApplicationReadyEvent)`) только дёргает её. Разделение принципиальное: bootstrap —
composition root без бизнес-логики и без Lombok; политика жизненного цикла collection-run живёт в
application (рядом с оркестратором, который так же использует `CollectionLock`). Сервис
берёт advisory-lock через тот же `CollectionLock`.
- Лок **свободен** → ни один сбор не идёт нигде (single-flight) → любой `RUNNING` это фантом →
  `failOrphanedRunning()` (bulk UPDATE `RUNNING → FAILED`).
- Лок **занят** → реальный сбор идёт на другом инстансе → `RUNNING` НЕ трогаем (его допишет
  ведущий). Ловим `CollectionAlreadyRunningException`, пропускаем.

Лок берётся на миллисекунды (один bulk-UPDATE) и сразу отпускается (try-with-resources) — не мешает
реальному сбору стартовать следом. Это корректно и для single-, и для multi-instance: не нужен ни
порог по времени (который оставлял бы фантом висеть часами), ни риск снести живой прогон.

**Почему не «fail all RUNNING на старте без лока».** При rolling-деплое pod B стартовал бы во время
живого сбора на pod A и снёс бы его `RUNNING` → `FAILED` (self-heal'ится при финальном save, но окно
вранья в UI). Лок-гейт это исключает.

Тесты: `StartupReconciliationServiceTest` (лок свободен → reconcile + release; занят → skip),
`CollectionRunRepositoryAdapterIT.failOrphanedRunningMarksRunningAsFailed`.


