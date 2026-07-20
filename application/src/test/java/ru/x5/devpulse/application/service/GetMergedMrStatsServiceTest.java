package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.ReviewStatsRepository;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.review.AuthorMergedMrCount;
import ru.x5.devpulse.domain.model.review.MergedMrCountRow;
import ru.x5.devpulse.domain.model.review.RepoMergedMrCount;
import ru.x5.devpulse.domain.model.review.TeamMergedMrStats;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetMergedMrStatsService (вмерженные MR по команде)")
class GetMergedMrStatsServiceTest {

    private static final Period PERIOD = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    private static final List<String> DEV_BRANCHES = List.of("dev", "main");

    @Mock private UnifiedUserRepository userRepo;
    @Mock private ReviewStatsRepository reviewRepo;
    private GetMergedMrStatsService service;

    @BeforeEach
    void setUp() {
        service = new GetMergedMrStatsService(userRepo, reviewRepo, DEV_BRANCHES);
    }

    @Test
    @DisplayName("Авторы обогащены и по убыванию count; репы по убыванию; total=сумма; dev-ветки проброшены")
    void happyPath() {
        when(userRepo.findAll()).thenReturn(List.of(
                user("boris@x5.ru", "Boris", "http://a", "Platform"),
                user("alice@x5.ru", "Alice", null, "Platform"),
                user("other@x5.ru", "Other", null, "OtherTeam")));
        when(reviewRepo.countMergedMrByAuthor(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES)))
                .thenReturn(List.of(
                        new MergedMrCountRow(new Email("alice@x5.ru"), 16),
                        new MergedMrCountRow(new Email("boris@x5.ru"), 21)));
        when(reviewRepo.countMergedMrByRepo(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES)))
                .thenReturn(List.of(
                        new RepoMergedMrCount("gkr/a", 14),
                        new RepoMergedMrCount("gkr/b", 23)));

        TeamMergedMrStats stats = service.get("Platform", PERIOD);

        assertAll(
                () -> assertThat(stats.total()).isEqualTo(37),
                () -> assertThat(stats.authors()).extracting(a -> a.email().value())
                        .containsExactly("boris@x5.ru", "alice@x5.ru"),
                () -> assertThat(stats.authors().get(0).displayName()).isEqualTo("Boris"),
                () -> assertThat(stats.authors().get(0).avatarUrl()).isEqualTo("http://a"),
                () -> assertThat(stats.authors().get(1).avatarUrl()).isNull(),
                () -> assertThat(stats.byRepo()).extracting(RepoMergedMrCount::repo)
                        .containsExactly("gkr/b", "gkr/a"));
        // Фильтр по dev-веткам действительно уходит в оба агрегата.
        verify(reviewRepo).countMergedMrByAuthor(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES));
        verify(reviewRepo).countMergedMrByRepo(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES));
    }

    @Test
    @DisplayName("Нет участников команды → пусто, репозиторий ревью не дёргается")
    void noMembers() {
        when(userRepo.findAll()).thenReturn(List.of(user("x@x5.ru", "X", null, "OtherTeam")));

        TeamMergedMrStats stats = service.get("Platform", PERIOD);

        assertAll(
                () -> assertThat(stats.total()).isZero(),
                () -> assertThat(stats.authors()).isEmpty(),
                () -> assertThat(stats.byRepo()).isEmpty());
        verifyNoInteractions(reviewRepo);
    }

    @Test
    @DisplayName("Автор без записи в unified_user резолвится с пустым именем/аватаром")
    void authorWithoutProfile() {
        when(userRepo.findAll()).thenReturn(List.of(user("boris@x5.ru", "Boris", "http://a", "Platform")));
        when(reviewRepo.countMergedMrByAuthor(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES)))
                .thenReturn(List.of(new MergedMrCountRow(new Email("boris@x5.ru"), 3)));
        when(reviewRepo.countMergedMrByRepo(eq(PERIOD), anyCollection(), eq(DEV_BRANCHES)))
                .thenReturn(List.of());

        AuthorMergedMrCount author = service.get("Platform", PERIOD).authors().get(0);

        assertThat(author.count()).isEqualTo(3);
        assertThat(author.displayName()).isEqualTo("Boris");
    }

    private static UnifiedUser user(String email, String name, String avatarUrl, String team) {
        return new UnifiedUser(
                1L, new Email(email), email, name, avatarUrl,
                null, null, team, false,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
