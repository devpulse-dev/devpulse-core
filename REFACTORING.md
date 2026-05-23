# План рефакторинга v1 → v2

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
markable-dev-analytics/
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
| 7 | **Финал** | Удаление `.old-src/`, smoke-тест в bootstrap, документация эндпоинтов | 🟡 |

### Где мы сейчас (Сессия 6 — query + REST)

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

---

## 7. Java 25 — где используем какую фичу

| Фича | Где (план/факт) |
|---|---|
| **Virtual threads** | `bootstrap/application.yml`: `spring.threads.virtual.enabled=true` (Spring Boot 4 GA). Tomcat и `@Async` автоматом на virtual. |
| **`StructuredTaskScope`** | `adapter-git/GitGatewayAdapter` — fan-out по репозиториям, отмена при ошибке одного. Возможно `CollectDailyStatsService` если вернём параллелизм по репам на уровне use case. |
| **Scoped Values** | планируется в Сессии 6 — request-scoped `CorrelationId` для REST-логов. |
| **Stream Gatherers** | в Сессии 6 — недельная агрегация со скользящим окном (`Gatherers.windowFixed(7)` и пр.). |
| **Pattern matching for switch** | домен: `CollectionStatus`, в REST: обработка ошибок use case → HTTP-статус. |
| **Markdown в Javadoc** | по мере написания публичных портов. |

---

## 8. Принципы, которые держим

1. **Никаких `@Transactional` в use case.** Транзакции — на уровне репозитория, рисуют границы консистентности данных. Use case оркеструет.
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
