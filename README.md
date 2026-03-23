# Markable Dev Analytics

Система аналитики активности разработчиков в Git-репозиториях с интеграцией AI для генерации сводок.

## Описание проекта

Markable Dev Analytics — это Spring Boot приложение для сбора, анализа и визуализации статистики работы разработчиков с Git-репозиториями. Система автоматически анализирует коммиты, собирает метрики активности и предоставляет REST API для получения аналитических данных. Также поддерживается генерация AI-сводок на основе статистики пользователя.

## Основные возможности

- 📊 **Сбор статистики коммитов** — автоматический анализ Git-репозиториев с подсчётом добавленных/удалённых строк
- 👤 **Профили пользователей** — детальная статистика по каждому разработчику
- 📅 **Ежедневная статистика** — отслеживание активности по дням и неделям
- 🤖 **AI-сводки** — генерация аналитических отчётов с помощью искусственного интеллекта
- 🔍 **Детализация коммитов** — информация о задачах, сообщениях и изменениях
- 📈 **Агрегированная статистика** — суммарные показатели за период
- ⚡ **Асинхронная обработка** — параллельный анализ нескольких репозиториев

## Технологический стек

### Backend
- **Java 17** — язык программирования
- **Spring Boot 3.2.4** — фреймворк
- **Spring Data JPA** — работа с базой данных
- **Spring Web** — REST API
- **Lombok** — сокращение кода
- **MapStruct 1.5.5** — маппинг объектов

### База данных
- **PostgreSQL** — основная база данных
- **Liquibase 4.20.0** — управление миграциями

### Интеграции
- **Git** — анализ репозиториев через командную строку
- **Redis** — кэширование
- **AI API** — генерация сводок (OpenAI-совместимый формат)

### Тестирование
- **JUnit 5.10.2** — модульное тестирование
- **Spring Boot Test** — интеграционное тестирование

## Архитектура проекта

```
src/main/java/ru/x5/markable/dev/analytics/
├── Main.java                          # Точка входа приложения
├── commons/                           # Общие компоненты
│   ├── config/                        # Конфигурация (WebConfig)
│   └── exceptions/                    # Исключения (ApiException, UnprocessableEntityException)
└── gitlab/                            # Основной функционал
    ├── client/                        # Внешние клиенты
    │   ├── AiClient.java             # Клиент для AI API
    │   └── GitClient.java            # Клиент для Git (через CLI)
    ├── config/                        # Конфигурации
    │   ├── AiProperties.java         # Настройки AI
    │   ├── AsyncConfig.java          # Асинхронное выполнение
    │   ├── GitProperties.java        # Настройки Git
    │   ├── RedisConfig.java          # Настройки Redis
    │   └── RestTemplateConfig.java   # Конфигурация HTTP клиента
    ├── exception/                     # Исключения домена
    ├── interactor/                    # Интеракторы (AnalysisInteractor)
    ├── mapper/                        # Мапперы (AuthorStatsMapper)
    ├── model/                         # Доменные модели
    │   ├── AuthorAggregate.java      # Агрегат статистики автора
    │   └── CommitDetail.java         # Детали коммита
    ├── persistence/                    # Слой персистентности
    │   ├── entity/                   # Сущности БД
    │   │   ├── AnalysisRun.java
    │   │   ├── AnalysisStatus.java
    │   │   ├── AuthorStats.java
    │   │   ├── CommitDetails.java
    │   │   ├── DailyAuthorStats.java
    │   │   ├── LastExportTracker.java
    │   │   └── RepoStats.java
    │   └── repository/               # Репозитории JPA
    ├── rest/                          # REST API
    │   ├── controller/               # Контроллеры
    │   │   ├── AnalysisController.java
    │   │   ├── AiSummaryController.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── UserProfileController.java
    │   └── dto/                      # DTO объекты
    ├── service/                       # Бизнес-логика
    │   ├── AiSummaryService.java
    │   ├── AnalysisService.java
    │   ├── CommitDetailsService.java
    │   ├── DailyStatsService.java
    │   ├── ExportTrackerService.java
    │   ├── UserProfileService.java
    │   └── impl/                     # Реализации сервисов
    └── utill/                         # Утилиты
        └── CommitMessageParser.java  # Парсер сообщений коммитов
```

## Схема базы данных

### Таблицы

- **analysis_run** — информация о запусках анализа
- **author_stats** — агрегированная статистика авторов
- **repo_stats** — статистика по репозиториям
- **daily_author_stats** — ежедневная статистика авторов
- **commit_details** — детальная информация о коммитах
- **last_export_tracker** — трекер последней выгрузки данных

### Миграции

Миграции базы данных управляются через Liquibase и находятся в `src/main/resources/liquibase/migration/`:

- `001-init.yaml` — инициализация основных таблиц
- `002-create-last-export-tracker.yaml` — трекер выгрузок
- `003-create-daily-author-stats.yaml` — ежедневная статистика
- `004-add-repository-name-to-daily-stats.yaml` — добавление имени репозитория
- `005-create-commit-details.yaml` — детали коммитов
- `006-add-task-and-message-to-commit-details.yaml` — задачи и сообщения

## REST API

### Анализ и статистика

#### Запуск анализа
```http
POST /api/v1/analysis
Content-Type: application/json

{
  "since": "2024-01-01",
  "until": "2024-12-31"
}
```

#### Сбор ежедневной статистики
```http
POST /api/v1/analysis/daily/collect
```

#### Получение ежедневной статистики коммитов
```http
GET /api/v1/analysis/daily
```

#### Получение ежедневной статистики по пользователям
```http
GET /api/v1/analysis/daily/detailed
```

#### Суммарная статистика за период
```http
GET /api/v1/analysis/summary
```

#### Недельная статистика
```http
GET /api/v1/analysis/weekly
```

### Профили пользователей

#### Получение профиля пользователя
```http
GET /api/v1/users/{email}?start=2024-01-01&end=2024-12-31
```

#### Получение коммитов пользователя
```http
GET /api/v1/users/{email}/commits
```

### AI-сводки

#### Генерация AI-сводки для пользователя
```http
GET /api/v1/users/{email}/summary
```

## Конфигурация

### application.yml

```yaml
spring:
  application:
    name: markable-dev-analytics
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/devanalytics
    username: postgres
    password: postgres
  jpa:
    generate-ddl: false
    open-in-view: false
    show-sql: false
    hibernate:
      ddl-auto: none
  liquibase:
    enabled: true
    change-log: liquibase/changelog.master.yml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379

git:
  repositories:
    - https://scm.x5.ru/gkr/xrg-core.git
    - https://scm.x5.ru/gkr/xrg-markable.git
  token: your-gitlab-token
  cache-directory: /path/to/git-cache/

ai:
  url: https://api-copilot.x5.ru/aigw/v1
  api-key: your-api-key
  model: copilot-code-large
  timeout: 30000

server:
  port: 8080
```

### Переменные окружения

- `REDIS_HOST` — хост Redis (по умолчанию: localhost)
- `SPRING_DATASOURCE_URL` — URL базы данных
- `SPRING_DATASOURCE_USERNAME` — пользователь БД
- `SPRING_DATASOURCE_PASSWORD` — пароль БД

## Установка и запуск

### Требования

- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- Redis 6+
- Git (для анализа репозиториев)

### Сборка проекта

```bash
mvn clean install
```

### Запуск приложения

```bash
mvn spring-boot:run
```

Или через JAR:

```bash
java -jar target/markable-dev-analytics-1.0-SNAPSHOT.jar
```

### Настройка базы данных

1. Создайте базу данных PostgreSQL:

```sql
CREATE DATABASE devanalytics;
```

2. Приложение автоматически применит миграции Liquibase при первом запуске

### Настройка Redis

Убедитесь, что Redis запущен:

```bash
redis-server
```

## Использование

### 1. Настройка репозиториев

Добавьте URL анализируемых Git-репозиториев в `application.yml`:

```yaml
git:
  repositories:
    - https://your-gitlab.com/group/project.git
  token: your-access-token
```

### 2. Запуск анализа

Отправьте запрос на анализ за период:

```bash
curl -X POST http://localhost:8080/api/v1/analysis \
  -H "Content-Type: application/json" \
  -d '{"since":"2024-01-01","until":"2024-12-31"}'
```

### 3. Сбор ежедневной статистики

Запустите сбор ежедневной статистики:

```bash
curl -X POST http://localhost:8080/api/v1/analysis/daily/collect
```

### 4. Получение профиля пользователя

```bash
curl http://localhost:8080/api/v1/users/user@example.com
```

### 5. Генерация AI-сводки

```bash
curl http://localhost:8080/api/v1/users/user@example.com/summary
```

## Разработка

### Структура кода

Проект следует принципам чистой архитектуры с разделением на слои:

- **Controller** — обработка HTTP запросов
- **Service** — бизнес-логика
- **Repository** — доступ к данным
- **Client** — внешние интеграции
- **Mapper** — преобразование сущностей

### Добавление новых фич

1. Создайте DTO в `rest/dto/`
2. Добавьте метод в контроллер
3. Реализуйте бизнес-логику в сервисе
4. При необходимости добавьте репозиторий
5. Создайте миграцию Liquibase для изменений в БД

### Тестирование

```bash
mvn test
```

## Особенности реализации

### Асинхронная обработка

Анализ репозиториев выполняется асинхронно с использованием `CompletableFuture` для параллельной обработки нескольких репозиториев.

### Кэширование Git-репозиториев

Репозитории клонируются в локальную директорию и обновляются при последующих запусках для оптимизации производительности.

### AI-интеграция

Система поддерживает OpenAI-совместимый API для генерации сводок. При недоступности AI используется fallback-механизм.

### Парсинг коммитов

Сообщения коммитов анализируются для извлечения информации о задачах и контексте изменений.

## Лицензия

[Укажите лицензию проекта]

## Контакты

Для вопросов и предложений обращайтесь к команде разработки.
