package ru.x5.devpulse.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.ReviewGateway;
import ru.x5.devpulse.application.port.out.ReviewWriteRepository;
import ru.x5.devpulse.domain.model.review.CollectedMergeRequest;
import ru.x5.devpulse.domain.model.user.Email;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectReviewsService")
class CollectReviewsServiceTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock ReviewGateway reviewGateway;
    @Mock ReviewWriteRepository reviewWriteRepository;

    @Test
    @DisplayName("Собранные MR прокидываются в upsert")
    void delegatesToWriteRepository() {
        var mr = new CollectedMergeRequest(
                42L, 7L, new Email("boris@x5.ru"), "fix", "https://scm/mr/7", "merged",
                SINCE, SINCE.plusHours(4), List.of());
        when(reviewGateway.fetchMergeRequests(SINCE)).thenReturn(List.of(mr));

        new CollectReviewsService(reviewGateway, reviewWriteRepository).collect(SINCE);

        verify(reviewWriteRepository).upsert(List.of(mr));
    }

    @Test
    @DisplayName("Пусто из GitLab ⇒ upsert не зовём")
    void emptyGatewaySkipsWrite() {
        when(reviewGateway.fetchMergeRequests(any())).thenReturn(List.of());

        new CollectReviewsService(reviewGateway, reviewWriteRepository).collect(SINCE);

        verifyNoInteractions(reviewWriteRepository);
        verify(reviewWriteRepository, never()).upsert(any());
    }
}
