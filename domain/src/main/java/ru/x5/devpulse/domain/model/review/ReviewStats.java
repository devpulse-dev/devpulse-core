package ru.x5.devpulse.domain.model.review;

import java.util.List;
import java.util.Objects;
import ru.x5.devpulse.domain.common.Period;

/**
 * Сводка ревью-метрик за период по всем авторам. Обслуживает {@code GET /api/v2/stats/reviews}.
 */
public record ReviewStats(Period period, List<ReviewAuthorStats> authors) {

    public ReviewStats {
        Objects.requireNonNull(period, "period required");
        authors = authors == null ? List.of() : List.copyOf(authors);
    }

    /** Копия с новым списком авторов (для enrichment displayName/avatarUrl в use case). */
    public ReviewStats withAuthors(List<ReviewAuthorStats> newAuthors) {
        return new ReviewStats(period, newAuthors);
    }
}
