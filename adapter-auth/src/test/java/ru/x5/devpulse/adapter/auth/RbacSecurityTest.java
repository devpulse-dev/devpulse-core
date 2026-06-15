package ru.x5.devpulse.adapter.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.domain.model.user.Role;

/**
 * RBAC на уровне SecurityFilterChain (ADR-13). Целевые контроллеры (adapter-rest) в слайс не
 * подняты — authz отрабатывает до dispatch: отказ → 403, проход → 404 (нет handler'а). Этого
 * достаточно, чтобы проверить, что правило дискриминирует по роли.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("RBAC: гейтинг аналитических разделов")
class RbacSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthenticateUseCase authenticate;

    @Test
    @DisplayName("MEMBER → /cohorts → 403; ADMIN → проходит authz (404, нет handler в слайсе)")
    void cohortsElevatedOnly() throws Exception {
        mvc.perform(get("/api/v2/cohorts/retention").with(user("m").roles("MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/cohorts/retention").with(user("a").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("MEMBER → /teams → 403; TEAMLEAD → проходит")
    void teamsElevatedOnly() throws Exception {
        mvc.perform(get("/api/v2/teams").with(user("m").roles("MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/teams").with(user("l").roles("TEAMLEAD")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("perf-review: MEMBER по себе — проходит; по чужому — 403")
    void perfReviewSelfOnlyForMember() throws Exception {
        var self = new DevpulsePrincipal("boris@x5.ru", Role.MEMBER, "Boris", null, null);
        var memberAuth = new UsernamePasswordAuthenticationToken(
                self, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        mvc.perform(get("/api/v2/performance/review").param("email", "boris@x5.ru")
                        .with(authentication(memberAuth)))
                .andExpect(status().isNotFound()); // authz пройден, handler'а в слайсе нет

        mvc.perform(get("/api/v2/performance/review").param("email", "alice@x5.ru")
                        .with(authentication(memberAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("perf-review: ADMIN — по любому разработчику")
    void perfReviewAnyForElevated() throws Exception {
        mvc.perform(get("/api/v2/performance/review").param("email", "alice@x5.ru")
                        .with(user("a").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
