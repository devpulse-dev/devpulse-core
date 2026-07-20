package ru.x5.devpulse.domain.model.performance;

import java.util.List;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;

/**
 * Результат простановки флага «AI-Agent» пачке карточек: сколько запрошено/обновлено и какие
 * карточки не удалось обновить (ошибка Kaiten). Частичный успех — норма (bulk продолжается).
 */
public record AiAgentMarkResult(int requested, int updated, List<KaitenCardId> failedIds) {

    public AiAgentMarkResult {
        failedIds = failedIds == null ? List.of() : List.copyOf(failedIds);
    }
}
