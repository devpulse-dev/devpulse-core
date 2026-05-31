# API documentation has moved

REST API больше не описывается в этом файле. Source of truth — **OpenAPI 3.0 контракты**
в отдельном репо:

📘 **[devpulse-dev/devpulse-oas](https://github.com/devpulse-dev/devpulse-oas)**

Контракты разбиты по доменам и публикуются в GitHub Packages как Maven-артефакты:

| Контракт | Что описывает |
|---|---|
| `shared-contract` | Общие schemas (Email, Period, Page, AuthorSummary, ProblemDetails…) |
| `collection-contract` | `POST/GET /api/v2/collection/runs` |
| `dashboard-contract` | `GET /api/v2/dashboard` |
| `stats-contract` | `GET /api/v2/stats/{daily,weekly,summary}` |
| `users-contract` | `GET /api/v2/users/{email}/{profile,commits}` |
| `kaiten-contract` | `POST /api/v2/kaiten/sync-users` |

Бэк implement'ит контракты — generated `*Api` интерфейсы используются как
compile-time guarantee соответствия. См. раздел
[«REST API: contract-first через OpenAPI»](./README.md#rest-api-contract-first-через-openapi)
в README.md.

**Просмотр контрактов:**
- Клонируй [devpulse-oas](https://github.com/devpulse-dev/devpulse-oas), открой
  `<module>/src/main/resources/openapi/*.yaml`.
- Или открой YAML напрямую в [Swagger Editor](https://editor.swagger.io/).

**История**: до перехода на contract-first (commits f505909, 041b488, 48967b3) этот
файл содержал ручное описание эндпоинтов. Удалено — было дублированием с теперь
автогенерируемой документацией контракта.
