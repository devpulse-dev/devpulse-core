package ru.x5.devpulse.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.kaiten.KaitenUser;
import ru.x5.devpulse.domain.model.user.KaitenUserId;

/**
 * Port out: Kaiten HTTP API.
 *
 * <p>Реализуется {@code adapter-kaiten} через {@code @HttpExchange} interface client
 * с adaptive rate limiter и retry на 429/5xx.</p>
 */
public interface KaitenGateway {

    /**
     * Точечная загрузка пользователей Kaiten по их id ({@code GET /users?ids=...}).
     *
     * <p>Адаптер чанкует список (Kaiten отдаёт максимум 100 на запрос) и склеивает результат.
     * Используется для рефреша уже привязанных {@code unified_user} (name/avatar) без выкачки
     * всего оргсправочника. Отсутствующие/деактивированные id просто не возвращаются.</p>
     */
    List<KaitenUser> fetchUsersByIds(Collection<KaitenUserId> ids);

    /**
     * Стримит пользователей по batchHandler. Передаются страницы по мере получения,
     * чтобы вызывающий код мог коммитить прогресс пер-страницы (избегаем потерь при 429).
     *
     * <p>Полный скан оргсправочника. Используется только для резолва ещё не привязанных
     * {@code unified_user} по email — когда их kaiten id нам неизвестен.</p>
     */
    void streamUsers(Consumer<List<KaitenUser>> pageHandler);

    /**
     * Стримит карточки по batchHandler. Передаются страницы по мере получения,
     * чтобы вызывающий код мог коммитить прогресс пер-страницы (избегаем потерь при 429).
     */
    void streamCards(List<KaitenUserId> memberFilter,
                     LocalDateTime updatedAfter,
                     Consumer<List<KaitenCard>> pageHandler);

    /**
     * Live-fetch карточек одного участника, обновлённых после {@code updatedAfter}.
     *
     * <p>В отличие от {@link #streamCards}, собирает все страницы в список и возвращает целиком.
     * Используется query-сценариями (например профиль пользователя), где нужен синхронный ответ.</p>
     */
    List<KaitenCard> fetchCardsForMember(KaitenUserId memberId, LocalDateTime updatedAfter);
}
