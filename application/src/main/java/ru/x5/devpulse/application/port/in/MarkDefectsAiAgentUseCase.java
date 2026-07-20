package ru.x5.devpulse.application.port.in;

import java.util.List;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.AiAgentMarkResult;

/**
 * Проставить дефектам флаг «AI-Agent» в Kaiten (property {@code id_6064 = true}).
 * Обслуживает {@code POST /api/v2/stats/defects/ai-agent}. Set-only, идемпотентно.
 *
 * <p>Обрабатывает и одну карточку (построчно), и пачку (bulk) — один список id.</p>
 */
public interface MarkDefectsAiAgentUseCase {

    AiAgentMarkResult mark(List<KaitenCardId> cardIds);
}
