package ru.x5.devpulse.adapter.rest.mapper;

import java.time.YearMonth;
import java.util.List;
import ru.x5.devpulse.adapter.rest.api.model.CohortActivityMatrix;
import ru.x5.devpulse.adapter.rest.api.model.CohortActivityMatrixDevelopersInner;
import ru.x5.devpulse.adapter.rest.api.model.CohortRetention;
import ru.x5.devpulse.adapter.rest.api.model.CohortRetentionCohortsInner;
import ru.x5.devpulse.adapter.rest.api.model.TierTransitions;
import ru.x5.devpulse.domain.model.cohort.DeveloperActivity;

/**
 * Domain когорт → сгенерённые DTO. Ручной маппер: вложенные {@code *Inner}-классы + enum'ы
 * ({@code IntervalEnum}/{@code TiersEnum}) + {@code YearMonth → 'YYYY-MM'} — MapStruct тут только
 * мешал бы. {@link YearMonth#toString()} даёт ISO {@code uuuu-MM}; имена {@code ActivityCategory}
 * совпадают с {@code TiersEnum}.
 */
public final class CohortResponseMapper {

    private CohortResponseMapper() {}

    public static CohortRetention toDto(ru.x5.devpulse.domain.model.cohort.CohortRetention r) {
        List<CohortRetentionCohortsInner> cohorts = r.cohorts().stream()
                .map(c -> new CohortRetentionCohortsInner(c.cohort().toString(), c.size(), c.retention()))
                .toList();
        return new CohortRetention(CohortRetention.IntervalEnum.MONTH, cohorts);
    }

    public static CohortActivityMatrix toDto(ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix m) {
        List<String> months = m.months().stream().map(YearMonth::toString).toList();
        List<CohortActivityMatrixDevelopersInner> developers = m.developers().stream()
                .map(CohortResponseMapper::toDeveloper)
                .toList();
        return new CohortActivityMatrix(months, developers);
    }

    public static TierTransitions toDto(ru.x5.devpulse.domain.model.cohort.TierTransitions t) {
        List<TierTransitions.TiersEnum> tiers = t.tiers().stream()
                .map(c -> TierTransitions.TiersEnum.valueOf(c.name()))
                .toList();
        return new TierTransitions(tiers, t.matrix());
    }

    private static CohortActivityMatrixDevelopersInner toDeveloper(DeveloperActivity d) {
        CohortActivityMatrixDevelopersInner dto = new CohortActivityMatrixDevelopersInner(
                d.email().value(), d.firstActive().toString(), d.lastActive().toString(), d.cells());
        dto.setDisplayName(d.displayName());
        dto.setAvatarUrl(d.avatarUrl());
        dto.setTeam(d.team());
        return dto;
    }
}
