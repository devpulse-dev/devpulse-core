package ru.x5.devpulse.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.in.CancellationSignal;
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
    @DisplayName("Батч проекта прокидывается в upsert")
    void delegatesToWriteRepository() {
        var mr = new CollectedMergeRequest(
                42L, 7L, new Email("boris@x5.ru"), "fix", "https://scm/mr/7", "merged",
                SINCE, SINCE.plusHours(4), "dev", List.of());
        // gateway стримит один project-batch в handler (handler — 3-й аргумент: since, cancelled, handler)
        doAnswer(inv -> {
            Consumer<List<CollectedMergeRequest>> handler = inv.getArgument(2);
            handler.accept(List.of(mr));
            return null;
        }).when(reviewGateway).streamMergeRequests(eq(SINCE), any(), any());

        new CollectReviewsService(reviewGateway, reviewWriteRepository)
                .collect(SINCE, CancellationSignal.NEVER);

        verify(reviewWriteRepository).upsert(List.of(mr));
    }

    @Test
    @DisplayName("Пустой батч ⇒ upsert не зовём")
    void emptyBatchSkipsWrite() {
        doAnswer(inv -> {
            Consumer<List<CollectedMergeRequest>> handler = inv.getArgument(2);
            handler.accept(List.of());
            return null;
        }).when(reviewGateway).streamMergeRequests(eq(SINCE), any(), any());

        new CollectReviewsService(reviewGateway, reviewWriteRepository)
                .collect(SINCE, CancellationSignal.NEVER);

        verify(reviewWriteRepository, never()).upsert(any());
        verifyNoInteractions(reviewWriteRepository);
    }
}
