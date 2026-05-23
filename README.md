# Markable Dev Analytics

[![CI](https://github.com/dev-Markable/markable-dev-analytics-core/actions/workflows/ci.yml/badge.svg)](https://github.com/dev-Markable/markable-dev-analytics-core/actions/workflows/ci.yml)

Сервис аналитики активности разработчиков: собирает коммиты из Git-репозиториев и карточки задач из Kaiten, агрегирует ежедневную статистику по авторам и отдаёт её через REST API для фронта.

> **Документы:**
> - [`API.md`](./API.md) — REST-эндпоинты v2 с примерами запросов/ответов.
> - [`REFACTORING.md`](./REFACTORING.md) — план рефакторинга v1 → v2, ADR-решения, roadmap.

---

## Технологический стек

| Слой | Технология |
|---|---|
| Язык | **Java 25** (preview features включены там, где это даёт выигрыш) |
| Framework | **Spring Boot 4.0** |
| Persistence | Spring Data JPA + Hibernate + PostgreSQL |
| Миграции | Liquibase (YAML) |
| HTTP-клиенты | Spring `@HttpExchange` interface clients |
| Конкуррентность | Virtual threads + `StructuredTaskScope` (GA в 25) |
| Тесты | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit 1.4.2 |
| Сборка | Maven multi-module |
| Логи | SLF4J → Log4j2 (Spring Boot starter) |

### Что нам даёт Java 25 (и почему не Java 21)

| Фича | В 21 | В 25 | Где используем |
|---|---|---|---|
| Virtual threads | ✅ stable | ✅ stable | I/O-bound: git CLI, JDBC, Kaiten HTTP — без ручных `Executor` |
| Structured Concurrency | ⚠️ preview | ✅ **stable** | fan-out по репозиториям в `adapter-git`, отмена при ошибке одного |
| Scoped Values | preview | ✅ stable | request-scoped state (correlation id, period) без `ThreadLocal` |
| Stream Gatherers | ❌ | ✅ stable | агрегация daily stats, оконные операции |
| Pattern matching for switch | ✅ | ✅ улучшено | домен (`CollectionStatus`, и т.п.) |
| Markdown в Javadoc | ❌ | ✅ | косметика |

---

## Архитектура: гексагон + multi-module

Каждый модуль — отдельный maven-артефакт. Зависимости направлены **только внутрь** (адаптеры → application → domain). За это отвечает ArchUnit-тест в `bootstrap`, который падает при попытке нарушить направление.

```
markable-dev-analytics/
├── pom.xml                       # parent (packaging=pom)
│
├── domain/                       # ⬅️ pure Java, никаких фреймворков (даже Lombok)
│                                 #    records, value objects (Email, RepoName, Period…),
│                                 #    domain services (AuthorAggregator, CommitMessageParser…)
│
├── application/                  # use cases + ports (in/out)
│                                 # port/in/  — что система умеет (CollectDailyStatsUseCase…)
│                                 # port/out/ — интерфейсы для адаптеров (GitGateway, *Repository…)
│                                 # service/  — реализации use cases (POJO + Lombok, без Spring)
│
├── adapter-rest/                 # IN-адаптер: REST controllers + DTO (api/v2)
├── adapter-persistence/          # OUT: JPA entities + Spring Data + Liquibase
├── adapter-git/                  # OUT: git CLI client (реализация GitGateway)
├── adapter-kaiten/               # OUT: Kaiten HTTP client (реализация KaitenGateway)
│
└── bootstrap/                    # @SpringBootApplication, application.yml, wiring (@Configuration)
                                  # + ArchUnit-тесты гексагона
```

### Правила слоёв (enforced ArchUnit)

- `domain` — **никаких** зависимостей от Spring, JPA, Jackson, Hibernate, **даже Lombok**.
- `application` — без Spring/JPA/Jackson/Hibernate. Lombok можно.
- Адаптеры между собой **не зависят** — общаются только через порты в `application`.
- `bootstrap` — единственный, кто видит всех и собирает граф через `@Configuration`.

---

## Структура БД

PostgreSQL, миграции в `adapter-persistence/src/main/resources/liquibase/migration/`. Перенесены 1-в-1 из v1, плюс одна новая (`015-create-collection-run.yaml`) под журнал прогонов сбора.

Ключевые таблицы:

| Таблица | Назначение |
|---|---|
| `commit_details` | Один git-коммит со статистикой строк, привязкой к UnifiedUser и `kaiten_card_id` (если в сообщении был номер задачи) |
| `daily_author_stats` | Дневной агрегат (commits, lines added/deleted, test added) по ключу `(email, date, repository_name)` |
| `unified_user` | Один человек = один `id`. Объединяет git author по email и Kaiten user через `kaiten_id` |
| `kaiten_user` / `kaiten_card` / `kaiten_card_member` | Зеркало Kaiten API |
| `collection_run` | Журнал прогонов сбора: started/finished, since/until, status, error |

---

## Локальный запуск

> **Требования:** Java 25, Maven 3.9+, Docker (для интеграционных тестов).

```bash
# Сборка + все тесты (unit + Testcontainers integration + ArchUnit)
mvn clean verify

# Только компиляция
mvn -q compile

# Только тесты
mvn -q test

# Запуск приложения (после первой `mvn install` или из IDE)
mvn -pl bootstrap spring-boot:run
```

### Конфиг

`bootstrap/src/main/resources/application.yml` — Postgres connection, git-репы для сбора, Kaiten endpoint/token. Чувствительные значения — через env переменные.

---

## Архитектурные принципы

1. **Изоляция фаз сбора.** Падение Kaiten НЕ откатывает уже сохранённую git-статистику. У каждой фазы своя транзакция, ошибка одной фазы не утаскивает за собой другую.
2. **Курсор по успешным прогонам.** Следующий сбор стартует с конца последнего `SUCCESS` в `collection_run`. Неудачный прогон не двигает курсор.
3. **Bulk-операции по умолчанию.** `INSERT ... ON CONFLICT DO UPDATE` для daily stats, batch `findOrCreateAll` для пользователей, batch save для коммитов. N+1 запрещён.
4. **Streaming пагинация Kaiten.** Каждая страница карточек коммитится отдельной транзакцией — при 429 на поздних страницах уже сохранённое остаётся в БД.
5. **Структурная конкуррентность.** Параллелизм по репозиториям — через `StructuredTaskScope`, а не `CompletableFuture` + ручной `Executor`. Виртуальные потоки — для I/O.
6. **Никакой бизнес-логики в адаптерах.** Парсинг git output, агрегация по дню — в `domain`. Адаптер git только запускает CLI и распарсивает вывод.

---

## Тесты

| Тип | Где | Фаза Maven | Конвенция имени |
|---|---|---|---|
| Unit (domain) | `domain/src/test/` | `test` (Surefire) | `*Test` |
| Unit (use cases с Mockito) | `application/src/test/` | `test` (Surefire) | `*Test` |
| Unit (адаптеры — парсеры, rate-limiter) | `adapter-*/src/test/` | `test` (Surefire) | `*Test` |
| MockMvc (REST slice) | `adapter-rest/src/test/` | `test` (Surefire) | `*Test` |
| ArchUnit (правила гексагона) | `bootstrap/src/test/` | `test` (Surefire) | `*Test` |
| Integration (Testcontainers + Postgres) | `adapter-persistence/src/test/` | `verify` (Failsafe) | `*IT` |
| Smoke (полный Spring-контекст) | `bootstrap/src/test/` | `verify` (Failsafe) | `*IT` |

Стиль: русский `@DisplayName`, `assertAll` для множественных проверок, параметризация через `@ParameterizedTest` где имеет смысл.

## CI / CD

GitHub Actions workflow в [`.github/workflows/ci.yml`](./.github/workflows/ci.yml) на каждый push в `main`/`master` и pull request:

| Джоба | Что делает | Время |
|---|---|---|
| `Compile` | `mvn package -DskipTests` — быстрый smoke на компиляцию | ~2 мин |
| `Unit tests` | `mvn test` — Surefire по всем модулям, без Docker | ~3–4 мин |
| `Integration tests` | `mvn verify -Dsurefire.skip=true` — Failsafe (`*IT`), поднимает Postgres-контейнеры | ~5–7 мин |

Unit и integration джобы запускаются **параллельно** после успешного compile. При падении автоматически прикрепляются артефакты с surefire/failsafe-отчётами.

Concurrency-группа отменяет старый прогон при пуше нового коммита в ту же ветку.

---

## Что выпилено из v1 (НЕ переносится)

- `AiSummaryService` + `AiClient` + всё `ai.*` в конфиге.
- Старый синхронный `/api/v1/analysis` + `AnalysisRun`/`AnalysisInteractor`/`AnalysisService` — дублировал daily stats.
- Redis-кэш и `RedisConfig`.
- Ручной `AsyncConfig` + `Executors.newFixedThreadPool(5)` — заменено на virtual threads + `StructuredTaskScope`.
- Ручные `RestTemplate` + кастомный retry на каждый клиент — заменено на `@HttpExchange` + единый адаптивный rate-limiter.

См. подробности в [`REFACTORING.md`](./REFACTORING.md).

---

## Дальнейшая работа

Текущий прогресс и следующие шаги — в [`REFACTORING.md`](./REFACTORING.md), раздел «Roadmap».
