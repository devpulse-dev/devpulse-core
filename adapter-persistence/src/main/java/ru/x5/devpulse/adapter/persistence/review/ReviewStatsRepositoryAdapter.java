package ru.x5.devpulse.adapter.persistence.review;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.MergeRequest;
import ru.x5.devpulse.domain.model.review.MergedMrCountRow;
import ru.x5.devpulse.domain.model.review.MrReview;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.user.Email;

/**
 * Read-адаптер ревью-метрик: грузит MR за период + их ревью двумя запросами
 * (без JPA @OneToMany), группирует ревью по MR в памяти и собирает доменные
 * {@link MergeRequest}. Агрегация по авторам — уже в домене ({@code ReviewSummarizer}).
 */
@Component
@RequiredArgsConstructor
class ReviewStatsRepositoryAdapter implements ReviewStatsRepository {

    private final MergeRequestJpaRepository mrJpa;
    private final MrReviewJpaRepository reviewJpa;

    @Override
    public List<MergeRequest> findMergeRequestsByPeriod(Period period) {
        List<MergeRequestEntity> mrs = mrJpa.findByCreatedAtBetween(
                period.fromAtStartOfDay(), period.toAtEndOfDay());
        if (mrs.isEmpty()) return List.of();

        List<Long> ids = mrs.stream().map(MergeRequestEntity::getId).toList();
        Map<Long, List<MrReview>> reviewsByMr = reviewJpa.findByMergeRequestIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        MrReviewEntity::getMergeRequestId,
                        Collectors.mapping(ReviewStatsRepositoryAdapter::toDomain, Collectors.toList())));

        List<MergeRequest> result = new ArrayList<>(mrs.size());
        for (MergeRequestEntity m : mrs) {
            result.add(new MergeRequest(
                    new Email(m.getAuthorEmail()),
                    m.getCreatedAt(),
                    m.getMergedAt(),
                    reviewsByMr.getOrDefault(m.getId(), List.of())));
        }
        return result;
    }

    @Override
    public List<MergedMrCountRow> countMergedMrByAuthor(
            Period period, Collection<Email> authorEmails, Collection<String> targetBranches) {
        if (authorEmails.isEmpty() || targetBranches.isEmpty()) {
            return List.of();
        }
        List<String> emails = authorEmails.stream().map(Email::value).toList();
        return mrJpa.countMergedByAuthor(
                        period.fromAtStartOfDay(), period.toAtEndOfDay(), emails, targetBranches).stream()
                .map(v -> new MergedMrCountRow(new Email(v.getAuthorEmail()), v.getMergedCount()))
                .toList();
    }

    @Override
    public List<RepoMergedMrCount> countMergedMrByRepo(
            Period period, Collection<Email> authorEmails, Collection<String> targetBranches) {
        if (authorEmails.isEmpty() || targetBranches.isEmpty()) {
            return List.of();
        }
        List<String> emails = authorEmails.stream().map(Email::value).toList();
        return mrJpa.countMergedByRepo(
                        period.fromAtStartOfDay(), period.toAtEndOfDay(), emails, targetBranches).stream()
                .map(v -> new RepoMergedMrCount(
                        repoName(v.getSampleWebUrl(), v.getProjectId()), (int) v.getMergedCount()))
                .toList();
    }

    /**
     * Путь репо ({@code namespace/repo}) из web_url MR: отбрасывает scheme+host слева и
     * {@code /-/merge_requests/...} справа. Фолбэк — {@code project-<id>}, если url пуст/непарсибелен
     * (web_url nullable; тогда хотя бы стабильная метка по проекту).
     */
    static String repoName(String webUrl, Long projectId) {
        String fallback = "project-" + projectId;
        if (webUrl == null || webUrl.isBlank()) {
            return fallback;
        }
        int marker = webUrl.indexOf("/-/merge_requests");
        String path = marker >= 0 ? webUrl.substring(0, marker) : webUrl;
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int hostSlash = path.indexOf('/', scheme + 3);
            path = hostSlash >= 0 ? path.substring(hostSlash + 1) : "";
        }
        path = path.replaceAll("^/+", "").replaceAll("/+$", "");
        return path.isBlank() ? fallback : path;
    }

    private static MrReview toDomain(MrReviewEntity e) {
        return new MrReview(new Email(e.getReviewerEmail()), e.isApproved(), e.getCommentCount());
    }
}
