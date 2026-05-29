package ru.x5.devpulse.domain.model.stats;

import java.util.List;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.kaiten.KaitenCard;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

/**
 * Read-side агрегат «профиль пользователя за период» — результат
 * {@code GetUserProfileUseCase.findProfile()}.
 *
 * <p>Объединяет четыре независимых источника:
 * <ul>
 *   <li>{@link UnifiedUser user} — личные данные (display name, avatar, kaiten id);</li>
 *   <li>{@link AuthorSummary summary} — агрегированная статистика commits/lines за период;</li>
 *   <li>{@code commits} — первая страница коммитов автора (для отображения списка);</li>
 *   <li>{@code cards} — релевантные карточки Kaiten (live или из кэша).</li>
 * </ul></p>
 *
 * <p>Живёт в {@code domain.model.stats} рядом с другими query-DTO ({@link Dashboard},
 * {@link WeeklyStats}, {@link PeriodSummary}). См. ADR-9 в REFACTORING.md о том, почему
 * это не «rich domain», а transaction-script DTO.</p>
 */
public record UserProfile(
        UnifiedUser user,
        AuthorSummary summary,
        List<Commit> commits,
        List<KaitenCard> cards
) {}
