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
import org.springframework.web.client.RestTemplate;
import ru.x5.markable.dev.analytics.kaiten.config.KaitenProperties;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenCardDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenSpaceDto;
import ru.x5.markable.dev.analytics.kaiten.rest.dto.KaitenUserDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;

@Component
@Log4j2
@RequiredArgsConstructor
public class KaitenClient {

    private final KaitenProperties properties;
    private final RestTemplate kaitenRestTemplate;

    public List<KaitenUserDto> getUsers() {
        String url = properties.getUrl() + "/users";
        return executeGet(url, new ParameterizedTypeReference<List<KaitenUserDto>>() {});
    }

    /**
     * Найти пользователя по email (через API поиска)
     */
    /**
     * Найти пользователя по email (используя параметр query API)
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

            if (hasMore) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep interrupted while fetching cards from space {}", spaceId);
                    break;
                }
            }
        }

        log.info("Fetched {} cards from Kaiten for space {}", allCards.size(), spaceId);
        return allCards;
    }

    public List<KaitenSpaceDto> getSpaces() {
        String url = properties.getUrl() + "/spaces";
        return executeGet(url, new ParameterizedTypeReference<List<KaitenSpaceDto>>() {});
    }

    private <T> T executeGet(String url, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<T> response = kaitenRestTemplate.exchange(
                url, HttpMethod.GET, entity, responseType
        );

        return response.getBody();
    }
}
