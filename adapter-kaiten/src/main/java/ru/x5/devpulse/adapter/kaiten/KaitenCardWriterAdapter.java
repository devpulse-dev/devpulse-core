package ru.x5.devpulse.adapter.kaiten;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.adapter.kaiten.dto.KaitenCardUpdateDto;
import ru.x5.devpulse.application.port.out.KaitenCardWriter;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;

/**
 * Реализация {@link KaitenCardWriter} поверх {@code PATCH /cards/{id}} — через тот же
 * {@link KaitenRateLimiter} (глобальный ~4 rps + retry на 429/5xx), что и чтение.
 */
@Component
@RequiredArgsConstructor
class KaitenCardWriterAdapter implements KaitenCardWriter {

    /** Custom property Kaiten для флага «AI-Agent» (см. KaitenCardMapper.aiAgentFrom). */
    static final String AI_AGENT_PROPERTY = "id_6064";

    private final KaitenHttpClient http;
    private final KaitenRateLimiter rateLimiter;

    @Override
    public void markAiAgent(KaitenCardId cardId) {
        rateLimiter.execute("PATCH /cards/" + cardId.value() + " ai-agent", () -> {
            http.updateCard(cardId.value(), new KaitenCardUpdateDto(Map.of(AI_AGENT_PROPERTY, true)));
            return null;
        });
    }
}
