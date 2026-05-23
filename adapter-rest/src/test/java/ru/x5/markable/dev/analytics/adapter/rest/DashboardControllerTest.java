package ru.x5.markable.dev.analytics.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.markable.dev.analytics.application.port.in.GetDashboardUseCase;
import ru.x5.markable.dev.analytics.domain.common.Period;
import ru.x5.markable.dev.analytics.domain.model.stats.AuthorSummary;
import ru.x5.markable.dev.analytics.domain.model.stats.Dashboard;
import ru.x5.markable.dev.analytics.domain.model.user.Email;

@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController (/api/v2/dashboard)")
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GetDashboardUseCase getDashboard;

    @Test
    @DisplayName("GET без from/to → бэк подставляет period = [today-30..today]")
    void defaultsToLast30Days() {
        when(getDashboard.get(any(), anyInt(), anyInt())).thenReturn(
                new Dashboard(new Period(LocalDate.now().minusDays(30), LocalDate.now()),
                        List.of(new AuthorSummary(new Email("a@x5.ru"), 5, 0, 0, 0, 0)),
                        List.of()));

        try {
            mvc.perform(get("/api/v2/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topActive[0].email").value("a@x5.ru"))
                    .andExpect(jsonPath("$.outsiders").isArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ArgumentCaptor<Period> periodCap = ArgumentCaptor.forClass(Period.class);
        verify(getDashboard).get(periodCap.capture(), anyInt(), anyInt());
        org.assertj.core.api.Assertions.assertThat(periodCap.getValue().from())
                .isEqualTo(LocalDate.now().minusDays(30));
    }

    @Test
    @DisplayName("GET с явным period и topN/outsiderN → значения прокинуты в use case")
    void explicitArgsArePropagated() throws Exception {
        when(getDashboard.get(any(), anyInt(), anyInt())).thenReturn(
                new Dashboard(new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                        List.of(), List.of()));

        mvc.perform(get("/api/v2/dashboard")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31")
                        .param("topN", "5")
                        .param("outsiderN", "3"))
                .andExpect(status().isOk());

        verify(getDashboard).get(
                new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                5, 3);
    }
}
