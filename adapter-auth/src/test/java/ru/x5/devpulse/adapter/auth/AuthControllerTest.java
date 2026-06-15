package ru.x5.devpulse.adapter.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.AuthenticateUseCase;
import ru.x5.devpulse.application.port.out.InvalidGitTokenException;
import ru.x5.devpulse.application.port.out.ProjectAccessDeniedException;
import ru.x5.devpulse.domain.model.user.AuthenticatedUser;
import ru.x5.devpulse.domain.model.user.Email;
import ru.x5.devpulse.domain.model.user.GitTokenType;
import ru.x5.devpulse.domain.model.user.Role;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController — PAT-логин")
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthenticateUseCase authenticate;

    @Test
    @DisplayName("POST /auth/login (валидный PAT) → 200 + профиль и роль")
    void loginOk() throws Exception {
        when(authenticate.authenticate(eq("tok"), eq(GitTokenType.PAT))).thenReturn(
                new AuthenticatedUser(new Email("boris@x5.ru"), Role.MEMBER, "Boris", null, "Platform"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.team").value("Platform"));
    }

    @Test
    @DisplayName("Невалидный токен → 401")
    void invalidToken() throws Exception {
        when(authenticate.authenticate(any(), any()))
                .thenThrow(new InvalidGitTokenException("bad token"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Нет доступа к проектам → 403")
    void noProjectAccess() throws Exception {
        when(authenticate.authenticate(any(), any()))
                .thenThrow(new ProjectAccessDeniedException("no access"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /auth/me без сессии → 401")
    void meUnauthenticated() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
