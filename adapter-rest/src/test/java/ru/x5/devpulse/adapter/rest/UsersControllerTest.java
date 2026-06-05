package ru.x5.devpulse.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import ru.x5.devpulse.application.port.in.GetUserCommitsUseCase;
import ru.x5.devpulse.application.port.in.GetUserProfileUseCase;
import ru.x5.devpulse.application.port.in.ListUsersUseCase;
import ru.x5.devpulse.application.port.in.SetUserTeamUseCase;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.UserProfile;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.KaitenUserId;
import ru.x5.devpulse.domain.model.user.UnifiedUser;

@WebMvcTest(UsersController.class)
@Import(RestMappersTestConfig.class)
@DisplayName("UsersController (/api/v2/users)")
class UsersControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetUserProfileUseCase getUserProfile;
    @MockitoBean GetUserCommitsUseCase getUserCommits;
    @MockitoBean ListUsersUseCase listUsers;
    @MockitoBean SetUserTeamUseCase setUserTeam;

    private static UnifiedUser userWithTeam(String team) {
        var now = LocalDateTime.now();
        return new UnifiedUser(1L, new Email("boris@x5.ru"), "boris", "Boris", null,
                new KaitenUserId(7L), null, team, false, now, now, now);
    }

    @Test
    @DisplayName("GET /{email}/profile найден → 200 с user/summary/commits/cards")
    void profileFound() throws Exception {
        var email = new Email("boris@x5.ru");
        var user = new UnifiedUser(1L, email, "boris", "Boris", null,
                new KaitenUserId(7L), null, null, false,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        var summary = new AuthorSummary(email, "Boris", null, 10, 1, 100, 50, 20, null, null, false);

        when(getUserProfile.findProfile(eq(email), any()))
                .thenReturn(Optional.of(new UserProfile(
                        user, summary, List.of(), List.of())));

        mvc.perform(get("/api/v2/users/boris@x5.ru/profile")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.user.kaitenId").value(7))
                .andExpect(jsonPath("$.summary.commits").value(10))
                .andExpect(jsonPath("$.commits").isArray())
                .andExpect(jsonPath("$.cards").isArray());
    }

    @Test
    @DisplayName("GET /{email}/profile не найден → 404")
    void profileNotFound() throws Exception {
        when(getUserProfile.findProfile(any(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v2/users/unknown@x5.ru/profile")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{email}/commits с page/size → передаёт PageRequest в use case")
    void commitsRespectsPagination() throws Exception {
        when(getUserCommits.find(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v2/users/boris@x5.ru/commits")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Period.to раньше Period.from → 400 RFC 7807")
    void invalidPeriodReturns400() throws Exception {
        mvc.perform(get("/api/v2/users/boris@x5.ru/profile")
                        .param("from", "2026-05-31").param("to", "2026-05-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /users?team= → 200 список с полем team")
    void listUsersByTeam() throws Exception {
        when(listUsers.list(any())).thenReturn(List.of(userWithTeam("Platform")));

        mvc.perform(get("/api/v2/users").param("team", "Platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("boris@x5.ru"))
                .andExpect(jsonPath("$[0].team").value("Platform"));
    }

    @Test
    @DisplayName("PUT /{email}/team → 200 с обновлённой командой")
    void setTeamOk() throws Exception {
        when(setUserTeam.setTeam(eq(new Email("boris@x5.ru")), any()))
                .thenReturn(Optional.of(userWithTeam("Platform")));

        mvc.perform(put("/api/v2/users/boris@x5.ru/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.team").value("Platform"));
    }

    @Test
    @DisplayName("PUT /{email}/team для несуществующего → 404")
    void setTeamNotFound() throws Exception {
        when(setUserTeam.setTeam(any(), any())).thenReturn(Optional.empty());

        mvc.perform(put("/api/v2/users/unknown@x5.ru/team")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Platform\"}"))
                .andExpect(status().isNotFound());
    }
}
