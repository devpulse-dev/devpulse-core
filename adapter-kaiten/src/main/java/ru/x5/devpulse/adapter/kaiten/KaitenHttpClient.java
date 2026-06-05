package ru.x5.devpulse.adapter.kaiten;

import java.util.List;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenUserDto;

/**
 * Декларативный HTTP-клиент Kaiten на Spring 6+ {@code @HttpExchange}.
 *
 * <p>Реализация генерируется фабрикой {@link
 * org.springframework.web.client.support.RestClientAdapter RestClientAdapter} +
 * {@link org.springframework.web.service.invoker.HttpServiceProxyFactory HttpServiceProxyFactory},
 * см. {@link KaitenAdapterConfig}.</p>
 *
 * <p>Этот интерфейс знает только про HTTP-эндпоинты Kaiten — про rate-limiter ему знать незачем;
 * лимитированием/ретраями занимается {@link KaitenRateLimiter}, обёрнутый вокруг этого клиента
 * в {@link KaitenGatewayAdapter}.</p>
 */
@HttpExchange(accept = "application/json")
public interface KaitenHttpClient {

    /**
     * {@code GET /users?limit=&offset=} — пользователи Kaiten с пагинацией.
     *
     * <p>Начиная с середины июля 2026 года, API Kaiten возвращает максимум 100 пользователей
     * на запрос. Используйте этот метод для загрузки всех пользователей постранично.</p>
     */
    @GetExchange("/users")
    List<KaitenUserDto> getUsers(
            @org.springframework.web.bind.annotation.RequestParam int limit,
            @org.springframework.web.bind.annotation.RequestParam int offset
    );

    /**
     * {@code GET /users?ids=&limit=} — точечная выборка пользователей по id.
     *
     * <p>{@code ids} — id через запятую (≤100 за запрос: чанкинг на стороне адаптера).
     * Отсутствующие/деактивированные id просто не попадают в ответ.</p>
     */
    @GetExchange("/users")
    List<KaitenUserDto> getUsersByIds(
            @org.springframework.web.bind.annotation.RequestParam(name = "ids") String ids,
            @org.springframework.web.bind.annotation.RequestParam int limit
    );

    /**
     * {@code GET /cards?limit=&offset=&member_ids=&updated_after=}.
     *
     * <p>Все параметры опциональны: {@code memberIds=null} означает "не фильтровать по участникам",
     * {@code updatedAfter=null} — "за всё время".</p>
     *
     * <p><b>{@code updatedAfter} — {@code String}</b>: Kaiten валидирует параметр как OpenAPI
     * {@code format: date-time} (RFC 3339, с timezone-суффиксом). Spring-сериализация
     * {@code LocalDateTime}/{@code OffsetDateTime} в query-param варьирует по версиям —
     * проще явно сформировать строку в адаптере и передать её сюда as-is.</p>
     */
    @GetExchange("/cards")
    List<KaitenCardDto> getCards(
            @org.springframework.web.bind.annotation.RequestParam int limit,
            @org.springframework.web.bind.annotation.RequestParam int offset,
            @org.springframework.web.bind.annotation.RequestParam(required = false, name = "member_ids") String memberIds,
            @org.springframework.web.bind.annotation.RequestParam(required = false, name = "updated_after") String updatedAfter
    );
}
