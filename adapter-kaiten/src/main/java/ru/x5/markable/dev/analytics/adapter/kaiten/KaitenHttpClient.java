package ru.x5.markable.dev.analytics.adapter.kaiten;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import ru.x5.markable.dev.analytics.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.adapter.kaiten.dto.KaitenUserDto;

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
interface KaitenHttpClient {

    /** {@code GET /users} — все пользователи Kaiten одним запросом. */
    @GetExchange("/users")
    List<KaitenUserDto> getUsers();

    /**
     * {@code GET /cards?limit=&offset=&member_ids=&updated_after=}.
     *
     * <p>Все параметры опциональны: {@code memberIds=null} означает "не фильтровать по участникам",
     * {@code updatedAfter=null} — "за всё время".</p>
     */
    @GetExchange("/cards")
    List<KaitenCardDto> getCards(
            @org.springframework.web.bind.annotation.RequestParam int limit,
            @org.springframework.web.bind.annotation.RequestParam int offset,
            @org.springframework.web.bind.annotation.RequestParam(required = false, name = "member_ids") String memberIds,
            @org.springframework.web.bind.annotation.RequestParam(required = false, name = "updated_after") LocalDateTime updatedAfter
    );
}
