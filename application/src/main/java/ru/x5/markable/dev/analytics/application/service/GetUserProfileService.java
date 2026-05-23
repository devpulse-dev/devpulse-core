package ru.x5.markable.dev.analytics.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ru.x5.markable.dev.analytics.application.port.in.GetUserProfileUseCase;
import ru.x5.markable.dev.analytics.application.port.out.CommitRepository;
import ru.x5.markable.dev.analytics.application.port.out.DailyStatsRepository;
import ru.x5.markable.dev.analytics.application.port.out.KaitenCardRepository;
import ru.x5.markable.dev.analytics.application.port.out.UnifiedUserRepository;
import ru.x5.markable.dev.analytics.domain.common.PageRequest;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.git.Commit;
import ru.x5.markable.dev.analytics.domain.model.kaiten.KaitenCard;
import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;
import ru.x5.markable.dev.analytics.domain.model.stats.DailyAuthorStats;
import ru.x5.markable.dev.analytics.domain.model.user.Email;
import ru.x5.markable.dev.analytics.domain.model.user.UnifiedUser;

/**
 * Профиль пользователя: {@link UnifiedUser} + агрегированная {@link AuthorSummary}
 * за период + список коммитов (первая страница, {@value #PROFILE_PAGE_SIZE}) + карточки Kaiten,
 * где автор — участник (если у пользователя есть {@code kaiten_id}).
 *
 * <p>Если пользователя нет в {@code unified_user} — возвращает {@link Optional#empty()}.</p>
 */
@RequiredArgsConstructor
public final class GetUserProfileService implements GetUserProfileUseCase {

    private static final int PROFILE_PAGE_SIZE = 500;

    private final UnifiedUserRepository unifiedUserRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final CommitRepository commitRepository;
    private final KaitenCardRepository kaitenCardRepository;

    @Override
    public Optional<Profile> findProfile(Email email, Period period) {
        Optional<UnifiedUser> userOpt = unifiedUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UnifiedUser user = userOpt.get();

        List<DailyAuthorStats> daily = dailyStatsRepository.findByAuthorAndPeriod(email, period);
        AuthorSummary summary = aggregate(email, daily);

        List<Commit> commits = commitRepository.findByAuthor(email, period, new PageRequest(0, PROFILE_PAGE_SIZE));

        List<KaitenCard> cards = user.kaiten()
                .map(kid -> kaitenCardRepository.findByMemberAndPeriod(kid, period))
                .orElseGet(List::of);

        return Optional.of(new Profile(user, summary, commits, cards));
    }

    private static AuthorSummary aggregate(Email email, List<DailyAuthorStats> daily) {
        long commits = 0;
        long mergeCommits = 0;
        long added = 0;
        long deleted = 0;
        long testAdded = 0;
        for (DailyAuthorStats s : daily) {
            commits += s.commits();
            mergeCommits += s.mergeCommits();
            added += s.addedLines();
            deleted += s.deletedLines();
            testAdded += s.testAddedLines();
        }
        return new AuthorSummary(email, commits, mergeCommits, added, deleted, testAdded);
    }
}
