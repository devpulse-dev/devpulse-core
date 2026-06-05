package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetReviewStatsService")
class GetReviewStatsServiceTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final Email BORIS = new Email("boris@x5.ru");
    private static final Email ALICE = new Email("alice@x5.ru");

    @Mock ReviewStatsRepository reviewStatsRepository;
    @Mock UnifiedUserRepository unifiedUserRepository;

    @Test
    @DisplayName("Пусто → пустой ReviewStats, unified_user не зовём")
    void emptyPeriod() {
        when(reviewStatsRepository.findMergeRequestsByPeriod(PERIOD)).thenReturn(List.of());

        var stats = service().get(PERIOD);

        assertAll("пусто",
                () -> assertThat(stats.period()).isEqualTo(PERIOD),
                () -> assertThat(stats.authors()).isEmpty(),
                () -> verifyNoInteractions(unifiedUserRepository));
    }

    @Test
    @DisplayName("Агрегирует + enrich displayName/avatarUrl из unified_user (кого знает)")
    void aggregatesAndEnriches() {
        // boris автор смерженного MR, alice его отревьюила (approve + 2 коммента)
        MergeRequest mr = new MergeRequest(BORIS,
                LocalDateTime.of(2026, 5, 10, 10, 0),
                LocalDateTime.of(2026, 5, 10, 14, 0),
                List.of(new MrReview(ALICE, true, 2)));
        when(reviewStatsRepository.findMergeRequestsByPeriod(PERIOD)).thenReturn(List.of(mr));
        // unified_user знает только alice
        when(unifiedUserRepository.findByEmails(anyCollection())).thenReturn(List.of(
                userWithProfile(ALICE, "Alice", "https://avatar/a")));

        var authors = service().get(PERIOD).authors();

        var byEmail = authors.stream().collect(
                java.util.stream.Collectors.toMap(a -> a.email(), a -> a));
        assertAll("enriched",
                () -> assertThat(byEmail.get(ALICE).reviewsGiven()).isEqualTo(1),
                () -> assertThat(byEmail.get(ALICE).commentsGiven()).isEqualTo(2),
                () -> assertThat(byEmail.get(ALICE).displayName()).isEqualTo("Alice"),
                () -> assertThat(byEmail.get(ALICE).avatarUrl()).isEqualTo("https://avatar/a"),
                () -> assertThat(byEmail.get(BORIS).reviewsReceived()).isEqualTo(1),
                () -> assertThat(byEmail.get(BORIS).displayName())
                        .as("boris не в unified_user → null").isNull());
    }

    private GetReviewStatsService service() {
        return new GetReviewStatsService(reviewStatsRepository, unifiedUserRepository);
    }

    private static UnifiedUser userWithProfile(Email email, String name, String avatarUrl) {
        LocalDateTime now = LocalDateTime.now();
        return new UnifiedUser(1L, email, email.localPart(), name, avatarUrl,
                null, null, null, false, now, now, now);
    }
}
