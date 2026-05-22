package ru.x5.markable.dev.analytics.kaiten.client;

import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ru.x5.markable.dev.analytics.kaiten.config.KaitenProperties;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент для взаимодействия с API Kaiten.
 * 
 * <p>Предоставляет методы для получения пользователей, карточек и пространств из Kaiten.
 * Поддерживает пагинацию при получении карточек и поиск пользователей по email.</p>
 * 
 * @author Markable Development Team
 * @version 1.0
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class KaitenClient {

    /**
     * Конфигурация подключения к Kaiten API.
     */
    private final KaitenProperties properties;

    /**
     * RestTemplate для выполнения HTTP-запросов к Kaiten API.
     */
    private final RestTemplate kaitenRestTemplate;

    /**
     * Получает список всех пользователей из Kaiten.
     * 
     * @return список пользователей Kaiten
     */
    public List<KaitenUserDto> getUsers() {
        String url = properties.getUrl() + "/users";
        return executeGet(url, new ParameterizedTypeReference<List<KaitenUserDto>>() {});
    }

    /**
     * Найти пользователя по email (используя параметр query API).
     * 
     * @param email email пользователя для поиска
     * @return Optional с найденным пользователем или пустой, если пользователь не найден
     */
    public Optional<KaitenUserDto> findUserByEmail(String email) {
        try {
            // Используем параметр query для поиска по email
            String url = properties.getUrl() + "/users?query=" + email;
            List<KaitenUserDto> users = executeGet(url,
                    new ParameterizedTypeReference<List<KaitenUserDto>>() {});

            // Фильтруем результат для точного совпадения email
            return users.stream()
                    .filter(user -> user.getEmail() != null &&
                            user.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        } catch (Exception e) {
            log.error("Error finding user by email: {}", email, e);
            return Optional.empty();
        }
    }

    /**
     * Получает карточки из указанного пространства Kaiten с поддержкой пагинации.
     * 
     * @param spaceId идентификатор пространства Kaiten
     * @param memberIds список идентификаторов участников для фильтрации (может быть null)
     * @param since дата и время, с которой искать обновлённые карточки (может быть null)
     * @return список карточек из указанного пространства
     */
    public List<KaitenCardDto> getCardsBySpace(Long spaceId, List<Long> memberIds, LocalDateTime since) {
        List<KaitenCardDto> allCards = new ArrayList<>();
        int limit = 100;
        int offset = 0;
        boolean hasMore = true;

        while (hasMore) {
            StringBuilder url = new StringBuilder(properties.getUrl() + "/cards");
            url.append("?space_id=").append(spaceId);
            url.append("&limit=").append(limit);
            url.append("&offset=").append(offset);

            // Используем member_ids для поиска карточек, где пользователь участник
            if (memberIds != null && !memberIds.isEmpty()) {
                String memberIdsParam = memberIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                url.append("&member_ids=").append(memberIdsParam);
            }

            if (since != null) {
                url.append("&updated_after=").append(since.toString());
            }

            List<KaitenCardDto> cards = executeGet(url.toString(),
                    new ParameterizedTypeReference<List<KaitenCardDto>>() {});

            allCards.addAll(cards);
            hasMore = cards.size() == limit;
            offset += limit;
        }

        log.info("Fetched {} cards from Kaiten for space {}", allCards.size(), spaceId);
        return allCards;
    }

    /**
     * Получает карточки из всех пространств с поддержкой пагинации (буферизирует всё в память).
     * Не привязан к конкретному пространству — возвращает все карточки пользователей.
     */
    public List<KaitenCardDto> getCards(List<Long> memberIds, LocalDateTime since) {
        List<KaitenCardDto> allCards = new ArrayList<>();
        streamCards(memberIds, since, allCards::addAll);
        log.info("Fetched {} total cards from Kaiten", allCards.size());
        return allCards;
    }

    /**
     * Стриминговый вариант: вызывает {@code pageHandler} на каждой странице.
     *
     * <p>Используется когда нужно сохранять прогресс по мере получения данных
     * (например, чтобы не терять уже выгруженные карточки при попадании в rate limit
     * на поздних страницах).</p>
     *
     * @param memberIds список ID участников для фильтрации
     * @param since дата, с которой искать обновлённые карточки
     * @param pageHandler обработчик каждой полученной страницы
     */
    public void streamCards(List<Long> memberIds, LocalDateTime since,
                            java.util.function.Consumer<List<KaitenCardDto>> pageHandler) {
        int limit = 100;
        int offset = 0;
        int total = 0;

        while (true) {
            StringBuilder url = new StringBuilder(properties.getUrl() + "/cards");
            url.append("?limit=").append(limit);
            url.append("&offset=").append(offset);

            if (memberIds != null && !memberIds.isEmpty()) {
                String memberIdsParam = memberIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                url.append("&member_ids=").append(memberIdsParam);
            }

            if (since != null) {
                url.append("&updated_after=").append(since.toString());
            }

            List<KaitenCardDto> page = executeGet(url.toString(),
                    new ParameterizedTypeReference<List<KaitenCardDto>>() {});

            if (!page.isEmpty()) {
                pageHandler.accept(page);
                total += page.size();
            }

            if (page.size() < limit) {
                break;
            }
            offset += limit;
        }
        log.info("Streamed {} total cards from Kaiten", total);
    }

    /**
     * Получает список всех пространств из Kaiten.
     *
     * @return список пространств Kaiten
     */
    public List<KaitenSpaceDto> getSpaces() {
        String url = properties.getUrl() + "/spaces";
        return executeGet(url, new ParameterizedTypeReference<List<KaitenSpaceDto>>() {});
    }

    /**
     * Момент последнего запроса (для троттлинга между вызовами).
     */
    private volatile long lastRequestAt = 0L;

    /**
     * До какого момента не делать ни одного запроса (взвели после 429 или 5xx).
     * Работает как глобальная пауза для всех потоков, использующих этот клиент.
     */
    private volatile long pauseUntil = 0L;

    /**
     * Выполняет GET-запрос к Kaiten API с троттлингом и retry на 429/5xx.
     *
     * <p>Между запросами выдерживает паузу {@code requestDelayMs} для соблюдения rate limit.
     * При получении 429 учитывает заголовок {@code Retry-After}; при отсутствии —
     * экспоненциальный backoff с потолком {@code retryMaxBackoffMs}.</p>
     */
    private <T> T executeGet(String url, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        int attempt = 0;
        long backoff = properties.getRetryInitialBackoffMs();

        while (true) {
            throttle();
            try {
                ResponseEntity<T> response = kaitenRestTemplate.exchange(
                        url, HttpMethod.GET, entity, responseType);
                return response.getBody();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= properties.getMaxRetries()) {
                    log.error("Kaiten 429 after {} retries, giving up: {}", attempt, url);
                    throw e;
                }
                long wait = retryAfterMillis(e.getResponseHeaders())
                        .orElse(Math.min(backoff, properties.getRetryMaxBackoffMs()));
                pauseUntil = System.currentTimeMillis() + wait;
                log.warn("Kaiten 429 Too Many Requests, pausing for {} ms (attempt {}/{})",
                        wait, attempt + 1, properties.getMaxRetries());
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, properties.getRetryMaxBackoffMs());
                attempt++;
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt >= properties.getMaxRetries()) {
                    log.error("Kaiten transient error after {} retries: {}", attempt, url, e);
                    throw e;
                }
                long wait = Math.min(backoff, properties.getRetryMaxBackoffMs());
                pauseUntil = System.currentTimeMillis() + wait;
                log.warn("Kaiten transient error ({}), pausing for {} ms (attempt {}/{})",
                        e.getClass().getSimpleName(), wait, attempt + 1, properties.getMaxRetries());
                sleepQuietly(wait);
                backoff = Math.min(backoff * 2, properties.getRetryMaxBackoffMs());
                attempt++;
            }
        }
    }

    /**
     * Парсит заголовок Retry-After (секунды или HTTP-date) в миллисекунды.
     */
    private Optional<Long> retryAfterMillis(HttpHeaders headers) {
        if (headers == null) return Optional.empty();
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignore) {
            return Optional.empty();
        }
    }

    /**
     * Глобальный троттлинг: выдерживает {@code requestDelayMs} между запросами
     * И ждёт окончания глобальной паузы {@code pauseUntil}, если та активна (взводится при 429).
     */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long pauseWait = pauseUntil - now;
        if (pauseWait > 0) {
            sleepQuietly(pauseWait);
            now = System.currentTimeMillis();
        }

        long delay = properties.getRequestDelayMs();
        if (delay > 0) {
            long wait = lastRequestAt + delay - now;
            if (wait > 0) {
                sleepQuietly(wait);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
