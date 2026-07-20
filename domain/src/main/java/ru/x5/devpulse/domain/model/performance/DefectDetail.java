package ru.x5.devpulse.domain.model.performance;

import java.time.LocalDateTime;
import java.util.List;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;

/**
 * Отдельный дефект для детальной таблицы (уникален по id).
 *
 * <p>Собирается из уникальных карточек-дефектов, попавших хотя бы в один период (по {@code createdAt}).
 * {@code members} — резолвнутые в {@code unified_user} участники карточки (для аватарок «кто был»).</p>
 */
public record DefectDetail(
        KaitenCardId id,
        String title,
        String url,
        LocalDateTime createdAt,
        boolean aiAgent,
        List<DefectMember> members) {

    public DefectDetail {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
