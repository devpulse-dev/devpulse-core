package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.UnifiedUserRepository;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@ExtendWith(MockitoExtension.class)
@DisplayName("Сервисы команд (ListTeams / SetTeamLead)")
class TeamServicesTest {

    private static final String TEAM = "Маркировка";
    private static final Email BORIS = new Email("boris@x5.ru");

    @Mock private UnifiedUserRepository repo;

    @Nested
    @DisplayName("ListTeamsService")
    class ListTeams {

        @Test
        @DisplayName("группирует unified_user по команде через доменный ассемблер")
        void groupsTeams() {
            when(repo.findAll()).thenReturn(List.of(
                    user("a@x5.ru", "Anna", TEAM, false),
                    user("b@x5.ru", "Boris", TEAM, true),
                    user("c@x5.ru", "NoTeam", null, false)));

            List<Team> teams = new ListTeamsService(repo).list();

            assertAll("команды",
                    () -> assertThat(teams).extracting(Team::name).containsExactly(TEAM),
                    () -> assertThat(teams.get(0).lead().name()).isEqualTo("Boris"),
                    () -> assertThat(teams.get(0).members()).hasSize(2));
        }
    }

    @Nested
    @DisplayName("SetTeamLeadService")
    class SetLead {

        @Test
        @DisplayName("Назначение: добавляет в команду, снимает прежнего лида, ставит нового")
        void assignsLead() {
            when(repo.findByEmail(BORIS)).thenReturn(Optional.of(user("boris@x5.ru", "Boris", TEAM, true)));
            when(repo.findAll()).thenReturn(List.of(
                    user("boris@x5.ru", "Boris", TEAM, true),
                    user("a@x5.ru", "Anna", TEAM, false)));

            Optional<Team> result = new SetTeamLeadService(repo).setLead(TEAM, "boris@x5.ru");

            assertAll("назначение лида",
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().lead().email()).isEqualTo(BORIS),
                    () -> verify(repo).updateTeam(eq(BORIS), eq(TEAM)),
                    () -> verify(repo).clearLeadForTeam(eq(TEAM)),
                    () -> verify(repo).updateLead(eq(BORIS), eq(true)));
        }

        @Test
        @DisplayName("Назначение несуществующего пользователя → empty, лид не трогаем")
        void assignUnknownUser() {
            when(repo.findByEmail(BORIS)).thenReturn(Optional.empty());

            Optional<Team> result = new SetTeamLeadService(repo).setLead(TEAM, "boris@x5.ru");

            assertAll("нет пользователя",
                    () -> assertThat(result).isEmpty(),
                    () -> verify(repo, never()).updateLead(eq(BORIS), eq(true)),
                    () -> verify(repo, never()).clearLeadForTeam(eq(TEAM)));
        }

        @Test
        @DisplayName("Снятие лида (email=null): чистит признак у команды, updateLead не зовём")
        void removesLead() {
            when(repo.findAll()).thenReturn(List.of(
                    user("a@x5.ru", "Anna", TEAM, false)));

            Optional<Team> result = new SetTeamLeadService(repo).setLead(TEAM, null);

            assertAll("снятие лида",
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().lead()).isNull(),
                    () -> verify(repo).clearLeadForTeam(eq(TEAM)));
        }

        @Test
        @DisplayName("Снятие лида у несуществующей команды (нет участников) → empty")
        void removeLeadEmptyTeam() {
            when(repo.findAll()).thenReturn(List.of());

            Optional<Team> result = new SetTeamLeadService(repo).setLead(TEAM, null);

            assertThat(result).isEmpty();
            verify(repo, never()).clearLeadForTeam(eq(TEAM));
        }

        @Test
        @DisplayName("Пустое имя команды → empty, в репозиторий не ходим")
        void blankTeam() {
            Optional<Team> result = new SetTeamLeadService(repo).setLead("  ", "boris@x5.ru");

            assertThat(result).isEmpty();
            verifyNoInteractions(repo);
        }
    }

    private static UnifiedUser user(String email, String name, String team, boolean lead) {
        var now = LocalDateTime.now();
        return new UnifiedUser(1L, new Email(email), name.toLowerCase(), name, null,
                null, null, team, lead, now, now, now);
    }
}
