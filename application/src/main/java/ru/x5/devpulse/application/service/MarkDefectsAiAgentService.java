package ru.x5.devpulse.application.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.x5.devpulse.application.port.in.MarkDefectsAiAgentUseCase;
import ru.x5.devpulse.application.port.out.KaitenCardWriter;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.AiAgentMarkResult;

/**
 * Простановка флага «AI-Agent» карточкам Kaiten по списку id. Идёт последовательно (Kaiten
 * ограничен глобальным ~4 rps — параллелить бессмысленно). Частичный успех: ошибка одной
 * карточки не роняет всю пачку, id попадает в {@code failedIds}.
 */
@Slf4j
@RequiredArgsConstructor
public final class MarkDefectsAiAgentService implements MarkDefectsAiAgentUseCase {

    private final KaitenCardWriter kaitenCardWriter;

    @Override
    public AiAgentMarkResult mark(List<KaitenCardId> cardIds) {
        List<KaitenCardId> failed = new ArrayList<>();
        int updated = 0;
        for (KaitenCardId id : cardIds) {
            try {
                kaitenCardWriter.markAiAgent(id);
                updated++;
            } catch (RuntimeException e) {
                log.warn("AI-agent mark: карточка {} не обновлена: {}", id.value(), e.toString());
                failed.add(id);
            }
        }
        log.info("AI-agent mark: запрошено {}, обновлено {}, ошибок {}",
                cardIds.size(), updated, failed.size());
        return new AiAgentMarkResult(cardIds.size(), updated, failed);
    }
}
