package ru.x5.devpulse.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.GetCohortsUseCase;
import ru.x5.devpulse.domain.model.cohort.CohortActivityMatrix;
import ru.x5.devpulse.domain.model.cohort.CohortRetention;
import ru.x5.devpulse.domain.model.cohort.DeveloperActivity;
import ru.x5.devpulse.domain.model.user.Email;

@WebMvcTest(CohortsController.class)
@DisplayName("CohortsController (/api/v2/cohorts)")
class CohortsControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetCohortsUseCase getCohorts;

    @Test
    @DisplayName("GET /cohorts/retention → 200, interval=month, маппинг когорт")
    void retention() throws Exception {
        when(getCohorts.retention(any(), any(), any(), anyInt())).thenReturn(
                new CohortRetention(List.of(
                        new CohortRetention.CohortRow(YearMonth.parse("2026-01"), 2, List.of(1.0, 0.5)))));

        mvc.perform(get("/api/v2/cohorts/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("month"))
                .andExpect(jsonPath("$.cohorts[0].cohort").value("2026-01"))
                .andExpect(jsonPath("$.cohorts[0].size").value(2))
                .andExpect(jsonPath("$.cohorts[0].retention[1]").value(0.5));
    }

    @Test
    @DisplayName("GET /cohorts/activity-matrix → 200, месяцы + enriched developer")
    void activityMatrix() throws Exception {
        when(getCohorts.activityMatrix(any(), any(), any())).thenReturn(
                new CohortActivityMatrix(
                        List.of(YearMonth.parse("2026-01")),
                        List.of(new DeveloperActivity(new Email("a@x5.ru"), "Alice", null, "alpha",
                                YearMonth.parse("2026-01"), YearMonth.parse("2026-01"), List.of(5)))));

        mvc.perform(get("/api/v2/cohorts/activity-matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0]").value("2026-01"))
                .andExpect(jsonPath("$.developers[0].email").value("a@x5.ru"))
                .andExpect(jsonPath("$.developers[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.developers[0].team").value("alpha"))
                .andExpect(jsonPath("$.developers[0].cells[0]").value(5));
    }
}
