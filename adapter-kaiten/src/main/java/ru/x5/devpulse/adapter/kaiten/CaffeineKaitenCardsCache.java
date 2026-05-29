package ru.x5.devpulse.adapter.kaiten;

import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.out.KaitenCardsCache;

/**
 * Реализация {@link KaitenCardsCache} через Spring Cache abstraction (Caffeine).
 *
 * <p>Имя кэша должно совпадать с тем что указано в
 * {@link KaitenGatewayAdapter#fetchCardsForMember} ({@code "kaiten-cards-by-member"}).</p>
 */
@Component
@Log4j2
class CaffeineKaitenCardsCache implements KaitenCardsCache {

    @Override
    @CacheEvict(value = "kaiten-cards-by-member", allEntries = true)
    public void invalidateAll() {
        log.info("Кэш kaiten-cards-by-member очищен — фронт получит свежие карточки на следующем /profile");
    }
}
