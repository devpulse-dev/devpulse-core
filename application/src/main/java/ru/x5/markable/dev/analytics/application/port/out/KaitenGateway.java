package ru.x5.markable.dev.analytics.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenUser;
import ru.x5.markable.dev.analytics.domain.model.user.KaitenUserId;

/**
 * Port out: Kaiten HTTP API.
 *
 * <p>Реализуется {@code adapter-kaiten} через {@code @HttpExchange} interface client
 * с adaptive rate limiter и retry на 429/5xx.</p>
 */
public interface KaitenGateway {

    List<KaitenUser> fetchAllUsers();

    /**
     * Стримит карточки по batchHandler. Передаются страницы по мере получения,
     * чтобы вызывающий код мог коммитить прогресс пер-страницы (избегаем потерь при 429).
     */
    void streamCards(List<KaitenUserId> memberFilter,
                     LocalDateTime updatedAfter,
                     Consumer<List<KaitenCard>> pageHandler);
}
