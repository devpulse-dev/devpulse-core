package ru.x5.devpulse.application.port.out;

/**
 * Port out: управление кэшем Kaiten cards (которые читаются live через {@link KaitenGateway}).
 *
 * <p>Использование: orchestrator зовёт {@link #invalidateAll()} после успешного сбора —
 * пользователь сразу видит свежие карточки в профиле, не дожидаясь истечения TTL.</p>
 *
 * <p>Существует как abstraction чтобы application-слой мог решить "когда инвалидировать",
 * не зная про Caffeine/Spring Cache. Реализация в adapter-kaiten — {@code @CacheEvict}.</p>
 */
public interface KaitenCardsCache {

    /** Полностью очистить кэш карточек Kaiten. Безопасно для concurrent calls. */
    void invalidateAll();
}
