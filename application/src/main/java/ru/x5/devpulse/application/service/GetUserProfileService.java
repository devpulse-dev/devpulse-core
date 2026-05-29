package ru.x5.devpulse.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.out.CommitRepository;
import ru.x5.devpulse.application.port.out.DailyStatsRepository;
import ru.x5.devpulse.application.port.out.KaitenGateway;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.DailyAuthorStats;
import ru.x5.devpulse.domain.model.stats.UserProfile;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Профиль пользователя: {@link UnifiedUser} + агрегированная {@link AuthorSummary}
 * за период + список коммитов (первая страница, {@value #PROFILE_PAGE_SIZE}) + карточки Kaiten.
 *
 * <p><b>Карточки Kaiten тянутся live</b> через {@link KaitenGateway}, а не из локальной БД.
 * Это намеренно: массовый сбор карточек выключен (бюджет Kaiten-API тратится только когда
 * фронт реально открывает профиль).</p>
 *
 * <p><b>Фильтр карточек:</b> показываем только релевантные пользователю за период:
 * <ul>
 *   <li>есть коммит автора по этой карточке за период (kaitenCardId из таскера коммита совпал
 *       с id карточки) — даже если карточка уже закрыта, мы по ней работали; ИЛИ</li>
 *   <li>карточка не закрыта ({@code columnStatus != DONE} и {@code closedAt == null}) —
 *       висит в работе у пользователя, отображаем.</li>
 * </ul>
 *
 * Карточки закрытые без коммитов автора — отсеиваем (вероятно «затянуло» по
 * {@code updated_after}-фильтру Kaiten, к этой работе пользователь напрямую не относится).</p>
 *
 * <p>Если пользователя нет в {@code unified_user} — возвращает {@link Optional#empty()}.</p>
 */
@RequiredArgsConstructor
public final class GetUserProfileService implements GetUserProfileUseCase {

    private static final int PROFILE_PAGE_SIZE = 500;

    private final UnifiedUserRepository unifiedUserRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final CommitRepository commitRepository;
    private final KaitenGateway kaitenGateway;

    @Override
    public Optional<UserProfile> findProfile(Email email, Period period) {
        Optional<UnifiedUser> userOpt = unifiedUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UnifiedUser user = userOpt.get();

        List<DailyAuthorStats> daily = dailyStatsRepository.findByAuthorAndPeriod(email, period);
        AuthorSummary summary = aggregate(email, daily);

        List<Commit> commits = commitRepository.findByAuthor(email, period, new PageRequest(0, PROFILE_PAGE_SIZE));

        // Карточки — live из Kaiten API. Только если у пользователя есть kaiten_id.
        List<KaitenCard> cards = user.kaiten()
                .map(kid -> kaitenGateway.fetchCardsForMember(kid, period.fromAtStartOfDay()))
                .orElseGet(List::of)
                .stream()
                .filter(filterByRelevance(commits))
                .toList();

        return Optional.of(new UserProfile(user, summary, commits, cards));
    }

    /**
     * Фильтр карточек: «по карточке есть коммит автора ИЛИ карточка не закрыта».
     * Карточки закрытые без коммитов автора отсеиваются — они «попали» в выборку из-за
     * {@code updated_after}-фильтра Kaiten (например кто-то заархивировал карточку, в которой
     * автор когда-то был участником), но к работе автора в этот период не относятся.
     */
    private static java.util.function.Predicate<KaitenCard> filterByRelevance(List<Commit> commits) {
        Set<Long> committedCardIds = extractKaitenCardIds(commits);
        return card -> committedCardIds.contains(card.id().value()) || !card.isClosed();
    }

    /** Собирает {@code kaiten_card_id} из коммитов (берём из taskNumber через asKaitenCardId). */
    private static Set<Long> extractKaitenCardIds(List<Commit> commits) {
        Set<Long> ids = new HashSet<>();
        for (Commit c : commits) {
            c.task().ifPresent(task -> {
                var maybeId = task.asKaitenCardId();
                if (maybeId.isPresent()) ids.add(maybeId.getAsLong());
            });
        }
        return ids;
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
        return new AuthorSummary(email, null, null, commits, mergeCommits, added, deleted, testAdded, null);
    }
}
