package ru.x5.devpulse.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@DisplayName("TeamAssembler (сборка команд из unified_user)")
class TeamAssemblerTest {

    @Test
    @DisplayName("groupByTeam: группирует по team, выделяет лида, исключает пустые team, сортирует")
    void groupsByTeam() {
        List<UnifiedUser> users = List.of(
                u(1, "Boris", "Маркировка", true),
                u(2, "Anna", "Маркировка", false),
                u(3, "Zed", "Ядро", false),
                u(4, "NoTeam", null, false),
                u(5, "Blank", "  ", false));

        List<Team> teams = TeamAssembler.groupByTeam(users);

        assertAll("команды",
                () -> assertThat(teams).extracting(Team::name)
                        .as("отсортированы по имени, пустые team исключены")
                        .containsExactly("Маркировка", "Ядро"),
                () -> assertThat(teams.get(0).lead()).as("лид Маркировки").isNotNull(),
                () -> assertThat(teams.get(0).lead().name()).isEqualTo("Boris"),
                () -> assertThat(teams.get(0).members()).extracting(UnifiedUser::name)
                        .as("лид первым, затем по имени")
                        .containsExactly("Boris", "Anna"),
                () -> assertThat(teams.get(1).lead()).as("у Ядра лида нет").isNull(),
                () -> assertThat(teams.get(1).members()).hasSize(1));
    }

    @Test
    @DisplayName("build: лид первым в members даже если по алфавиту он позже")
    void buildPutsLeadFirst() {
        Team team = TeamAssembler.build("X", List.of(
                u(1, "Anna", "X", false),
                u(2, "Zed", "X", true)));

        assertAll("одна команда",
                () -> assertThat(team.lead().name()).isEqualTo("Zed"),
                () -> assertThat(team.members()).extracting(UnifiedUser::name)
                        .containsExactly("Zed", "Anna"));
    }

    private static UnifiedUser u(long id, String name, String team, boolean lead) {
        var now = LocalDateTime.now();
        return new UnifiedUser(id, new Email("u" + id + "@x5.ru"), "u" + id, name, null,
                null, null, team, lead, now, now, now);
    }
}
