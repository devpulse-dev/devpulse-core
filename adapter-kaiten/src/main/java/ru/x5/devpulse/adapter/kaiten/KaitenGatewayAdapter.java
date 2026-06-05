package ru.x5.devpulse.adapter.kaiten;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardDto;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenUserDto;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Реализация {@link KaitenGateway} поверх HTTP-клиента Kaiten.
 *
 * <p>Все запросы идут через {@link KaitenRateLimiter} — он гарантирует выдержку RPS,
 * глобальные паузы после 429 и retry на 429/5xx с exp backoff.</p>
 *
 * <p>{@link #streamCards} отдаёт страницы через {@code pageHandler} сразу — чтобы
 * вызывающий код (use case) мог коммитить прогресс пер-страницы и не терять уже
 * выгруженные карточки при попадании в rate-limit на поздних страницах.</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
class KaitenGatewayAdapter implements KaitenGateway {

    private final KaitenHttpClient http;
    private final KaitenRateLimiter rateLimiter;
    private final KaitenCardMapper mapper;
    private final KaitenProperties properties;

    /** Kaiten отдаёт максимум 100 пользователей на запрос — чанкуем список id под этот предел. */
    private static final int IDS_CHUNK = 100;

    @Override
    public List<KaitenUser> fetchUsersByIds(Collection<KaitenUserId> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        List<Long> distinct = ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(KaitenUserId::value)
                .distinct()
                .toList();
        if (distinct.isEmpty()) return List.of();

        List<KaitenUser> result = new ArrayList<>(distinct.size());
        for (int from = 0; from < distinct.size(); from += IDS_CHUNK) {
            List<Long> chunk = distinct.subList(from, Math.min(from + IDS_CHUNK, distinct.size()));
            String idsCsv = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<KaitenUserDto> raw = rateLimiter.execute(
                    "GET /users?ids=" + idsCsv,
                    () -> http.getUsersByIds(idsCsv, chunk.size()));
            for (KaitenUserDto dto : raw) {
                result.add(mapper.toDomain(dto));
            }
        }
        log.info("Загружено {} пользователей Kaiten по {} запрошенным id", result.size(), distinct.size());
        return result;
    }

    @Override
    public void streamUsers(Consumer<List<KaitenUser>> pageHandler) {
        int limit = properties.pageSize();
        int offset = 0;
        int total = 0;

        while (true) {
            int currentOffset = offset;
            List<KaitenUserDto> rawPage = rateLimiter.execute(
                    "GET /users?limit=" + limit + "&offset=" + currentOffset,
                    () -> http.getUsers(limit, currentOffset));

            if (!rawPage.isEmpty()) {
                List<KaitenUser> mapped = new ArrayList<>(rawPage.size());
                for (KaitenUserDto dto : rawPage) {
                    mapped.add(mapper.toDomain(dto));
                }
                pageHandler.accept(mapped);
                total += mapped.size();
            }

            if (rawPage.size() < limit) break;
            offset += limit;
        }
        log.info("Стримили {} пользователей Kaiten", total);
    }

    @Override
    public void streamCards(List<KaitenUserId> memberFilter,
                            LocalDateTime updatedAfter,
                            Consumer<List<KaitenCard>> pageHandler) {

        String memberIdsCsv = (memberFilter == null || memberFilter.isEmpty())
                ? null
                : memberFilter.stream().map(KaitenUserId::value).map(String::valueOf)
                        .collect(Collectors.joining(","));

        int limit = properties.pageSize();
        int offset = 0;
        int total = 0;

        // Kaiten валидирует updated_after как OpenAPI format: date-time (RFC 3339).
        // Формируем строку ЯВНО: 2026-04-23T00:00:00Z. Use case даёт LocalDateTime без TZ —
        // трактуем как UTC. ISO_OFFSET_DATE_TIME с UTC offset выводит ровно "...Z".
        String updatedAfterStr = updatedAfter == null
                ? null
                : updatedAfter.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        log.debug("Kaiten /cards updated_after={}", updatedAfterStr);

        while (true) {
            int currentOffset = offset;
            List<KaitenCardDto> rawPage = rateLimiter.execute(
                    "GET /cards?limit=" + limit + "&offset=" + currentOffset,
                    () -> http.getCards(limit, currentOffset, memberIdsCsv, updatedAfterStr));

            if (!rawPage.isEmpty()) {
                // url собирает маппер из properties.webBaseUrl() — никакой захардкоженной строки.
                String webBaseUrl = properties.webBaseUrl();
                List<KaitenCard> mapped = new ArrayList<>(rawPage.size());
                for (KaitenCardDto dto : rawPage) {
                    mapped.add(mapper.toDomain(dto, webBaseUrl));
                }
                pageHandler.accept(mapped);
                total += mapped.size();
            }

            if (rawPage.size() < limit) break;
            offset += limit;
        }
        log.info("Стримили {} карточек Kaiten", total);
    }

    /**
     * Кэшируем результат на TTL (см. {@code spring.cache.caffeine.spec} в application.yml).
     *
     * <p><b>Ключ:</b> {@code memberId.value() + ':' + updatedAfter}. Один пользователь с одним
     * фильтром по периоду — один кэш-bucket. При refresh страницы фронт получит cached версию,
     * Kaiten API не дёргается.</p>
     *
     * <p><b>Что НЕ кэшируем:</b> {@code streamCards()} (используется при сборе — там нужна
     * свежая выборка), {@code streamUsers()}/{@code fetchUsersByIds()} (тоже сбор-side).</p>
     */
    @Override
    @Cacheable(value = "kaiten-cards-by-member",
               key = "#memberId.value() + ':' + #updatedAfter")
    public List<KaitenCard> fetchCardsForMember(KaitenUserId memberId, LocalDateTime updatedAfter) {
        List<KaitenCard> accumulator = new ArrayList<>();
        streamCards(List.of(memberId), updatedAfter, accumulator::addAll);
        return accumulator;
    }
}
