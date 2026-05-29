package ru.x5.devpulse.adapter.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.SyncKaitenUsersUseCase;

@WebMvcTest(KaitenController.class)
@DisplayName("KaitenController (/api/v2/kaiten)")
class KaitenControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean SyncKaitenUsersUseCase syncKaitenUsers;

    @Test
    @DisplayName("POST /sync-users возвращает число синхронизированных")
    void syncUsersReturnsCount() throws Exception {
        when(syncKaitenUsers.syncAll()).thenReturn(42);

        mvc.perform(post("/api/v2/kaiten/sync-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(42));
    }
}
