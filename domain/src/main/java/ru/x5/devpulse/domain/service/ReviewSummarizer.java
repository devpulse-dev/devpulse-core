package ru.x5.devpulse.domain.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.review.ReviewAuthorStats;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Доменная агрегация ревью-метрик из набора {@link MergeRequest} в список
 * {@link ReviewAuthorStats} (по одному на участника — автора MR или ревьюера).
 *
 * <p>Правила (см. контракт {@code ReviewAuthor}):</p>
 * <ul>
 *   <li><b>reviewsGiven</b> — distinct MR, где человек нажал Approve;</li>
 *   <li><b>commentsGiven</b> — сумма ревью-комментов человека по всем чужим MR (объём);</li>
 *   <li><b>reviewsReceived</b> — сколько MR автора были отревьюены (≥ 1 ревьюер);</li>
 *   <li><b>avgTimeToMergeHours</b> — среднее open→merge по смерженным MR автора;</li>
 *   <li><b>mergedMrCount</b> — сколько MR автора смержено.</li>
 * </ul>
 *
 * <p>Саморевью уже отфильтрованы при сборе ({@code reviewer != author}), поэтому здесь
 * given/received не пересекаются по одному MR.</p>
 *
 * <p>Сортировка результата — по убыванию «вовлечённости в ревью» ({@code reviewsGiven +
 * commentsGiven}), tie-break по email: «кто тащит ревью» сверху.</p>
 */
public final class ReviewSummarizer {

    private ReviewSummarizer() {}

    public static List<ReviewAuthorStats> summarize(List<MergeRequest> mergeRequests) {
        if (mergeRequests == null || mergeRequests.isEmpty()) return List.of();

        Map<Email, Acc> byEmail = new LinkedHashMap<>();

        for (MergeRequest mr : mergeRequests) {
            // received-сторона: метрики автора MR
            Acc author = byEmail.computeIfAbsent(mr.author(), e -> new Acc());
            if (mr.wasReviewed()) {
                author.reviewsReceived++;
            }
            if (mr.isMerged()) {
                author.mergedMrCount++;
                author.totalTimeToMergeHours +=
                        Duration.between(mr.createdAt(), mr.mergedAt()).toMinutes() / 60.0;
            }

            // given-сторона: вклад каждого ревьюера
            for (MrReview r : mr.reviews()) {
                Acc reviewer = byEmail.computeIfAbsent(r.reviewer(), e -> new Acc());
                if (r.approved()) {
                    reviewer.reviewsGiven++;
                }
                reviewer.commentsGiven += r.commentCount();
            }
        }

        List<ReviewAuthorStats> result = new ArrayList<>(byEmail.size());
        for (Map.Entry<Email, Acc> e : byEmail.entrySet()) {
            Acc a = e.getValue();
            double avgTtm = a.mergedMrCount > 0 ? a.totalTimeToMergeHours / a.mergedMrCount : 0.0;
            result.add(new ReviewAuthorStats(
                    e.getKey(), null, null,
                    a.reviewsGiven, a.commentsGiven, a.reviewsReceived,
                    avgTtm, a.mergedMrCount, null, false));
        }

        result.sort(Comparator
                .comparingInt((ReviewAuthorStats s) -> s.reviewsGiven() + s.commentsGiven())
                .reversed()
                .thenComparing(s -> s.email().value()));
        return result;
    }

    /** Мутабельный аккумулятор на одного человека. */
    private static final class Acc {
        int reviewsGiven;
        int commentsGiven;
        int reviewsReceived;
        int mergedMrCount;
        double totalTimeToMergeHours;
    }
}
