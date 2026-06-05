package ru.x5.devpulse.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.ListTeamsUseCase;
import ru.x5.devpulse.application.port.in.SetTeamLeadUseCase;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.Team;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@WebMvcTest(TeamsController.class)
@Import(RestMappersTestConfig.class)
@DisplayName("TeamsController (/api/v2/teams)")
class TeamsControllerTest {

    private static final String TEAM = "Маркировка";

    @Autowired MockMvc mvc;

    @MockitoBean ListTeamsUseCase listTeams;
    @MockitoBean SetTeamLeadUseCase setTeamLead;

    @Test
    @DisplayName("GET /teams → 200: имя, лид и участники с isLead")
    void listsTeams() throws Exception {
        when(listTeams.list()).thenReturn(List.of(team()));

        mvc.perform(get("/api/v2/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(TEAM))
                .andExpect(jsonPath("$[0].lead.email").value("boris@x5.ru"))
                .andExpect(jsonPath("$[0].lead.isLead").value(true))
                .andExpect(jsonPath("$[0].members[0].isLead").value(true))
                .andExpect(jsonPath("$[0].members[1].email").value("anna@x5.ru"))
                .andExpect(jsonPath("$[0].members[1].isLead").value(false));
    }

    @Test
    @DisplayName("PUT /teams/lead → 200 с обновлённой командой")
    void setLeadOk() throws Exception {
        when(setTeamLead.setLead(eq(TEAM), eq("boris@x5.ru"))).thenReturn(Optional.of(team()));

        mvc.perform(put("/api/v2/teams/lead")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Маркировка\",\"email\":\"boris@x5.ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TEAM))
                .andExpect(jsonPath("$.lead.email").value("boris@x5.ru"));
    }

    @Test
    @DisplayName("PUT /teams/lead для несуществующей команды/пользователя → 404")
    void setLeadNotFound() throws Exception {
        when(setTeamLead.setLead(any(), any())).thenReturn(Optional.empty());

        mvc.perform(put("/api/v2/teams/lead")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Неизвестная\",\"email\":null}"))
                .andExpect(status().isNotFound());
    }

    private static Team team() {
        UnifiedUser lead = user("boris@x5.ru", "Boris", 7L, true);
        UnifiedUser member = user("anna@x5.ru", "Anna", 8L, false);
        return new Team(TEAM, lead, List.of(lead, member));
    }

    private static UnifiedUser user(String email, String name, long kaitenId, boolean lead) {
        var now = LocalDateTime.now();
        return new UnifiedUser(1L, new Email(email), name.toLowerCase(), name, null,
                new KaitenUserId(kaitenId), null, TEAM, lead, now, now, now);
    }
}
