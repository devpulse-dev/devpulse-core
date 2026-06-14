package ru.x5.devpulse.adapter.rest;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.x5.devpulse.adapter.rest.api.CohortsApi;
import ru.x5.devpulse.adapter.rest.api.model.CohortActivityMatrix;
import ru.x5.devpulse.adapter.rest.api.model.CohortRetention;
import ru.x5.devpulse.adapter.rest.api.model.TierTransitions;
import ru.x5.devpulse.adapter.rest.mapper.CohortResponseMapper;
import ru.x5.devpulse.application.port.in.GetCohortsUseCase;

/**
 * Когортные/retention-вью ({@code /api/v2/cohorts/*}). Контракт — {@link CohortsApi}.
 *
 * <p>Чистое чтение поверх истории коммитов; ни сбора, ни Kaiten. Окно {@code from/to} опционально
 * (по умолчанию вся история), {@code team} — фильтр по текущей команде.</p>
 */
@RestController
@RequiredArgsConstructor
class CohortsController implements CohortsApi {

    private final GetCohortsUseCase getCohorts;

    @Override
    public ResponseEntity<CohortRetention> getCohortRetention(
            LocalDate from, LocalDate to, String team, Integer minCommits) {
        int threshold = minCommits == null ? 1 : minCommits;
        return ResponseEntity.ok(CohortResponseMapper.toDto(
                getCohorts.retention(from, to, team, threshold)));
    }

    @Override
    public ResponseEntity<CohortActivityMatrix> getCohortActivityMatrix(
            LocalDate from, LocalDate to, String team) {
        return ResponseEntity.ok(CohortResponseMapper.toDto(
                getCohorts.activityMatrix(from, to, team)));
    }

    @Override
    public ResponseEntity<TierTransitions> getTierTransitions(
            LocalDate from, LocalDate to, String team) {
        return ResponseEntity.ok(CohortResponseMapper.toDto(
                getCohorts.tierTransitions(from, to, team)));
    }
}
