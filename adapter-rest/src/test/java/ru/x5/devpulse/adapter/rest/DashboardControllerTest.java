package ru.x5.devpulse.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
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
import ru.x5.devpulse.application.port.in.GetDashboardUseCase;
import ru.x5.devpulse.domain.common.Page;
import ru.x5.devpulse.domain.common.PageRequest;
import ru.x5.devpulse.domain.common.Period;
import ru.x5.devpulse.domain.model.stats.AuthorSummary;
import ru.x5.devpulse.domain.model.stats.Dashboard;
import ru.x5.devpulse.domain.model.user.Email;

@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController (/api/v2/dashboard)")
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GetDashboardUseCase getDashboard;

    @Test
    @DisplayName("GET без from/to → бэк подставляет period = [today-30..today], дефолт page=0, size=20")
    void defaultsToLast30Days() throws Exception {
        AuthorSummary boris = new AuthorSummary(
                new Email("boris@x5.ru"), "Boris", "https://avatar/1",
                /*commits*/ 50, /*mergeCommits*/ 5,
                /*added*/ 200, /*deleted*/ 80, /*testAdded*/ 30,
                /*activity*/ null);
        when(getDashboard.get(any(), any())).thenReturn(new Dashboard(
                new Period(LocalDate.now().minusDays(30), LocalDate.now()),
                new Page<>(List.of(boris), 0, 20, 1)));

        mvc.perform(get("/api/v2/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].email").value("boris@x5.ru"))
                .andExpect(jsonPath("$.items[0].displayName").value("Boris"))
                .andExpect(jsonPath("$.items[0].avatarUrl").value("https://avatar/1"))
                .andExpect(jsonPath("$.items[0].commits").value(50))
                .andExpect(jsonPath("$.items[0].nonMergeCommits").value(45))
                .andExpect(jsonPath("$.items[0].mergeCommits").value(5));

        ArgumentCaptor<Period> periodCap = ArgumentCaptor.forClass(Period.class);
        ArgumentCaptor<PageRequest> pageCap = ArgumentCaptor.forClass(PageRequest.class);
        verify(getDashboard).get(periodCap.capture(), pageCap.capture());

        org.assertj.core.api.Assertions.assertThat(periodCap.getValue().from())
                .isEqualTo(LocalDate.now().minusDays(30));
        org.assertj.core.api.Assertions.assertThat(pageCap.getValue().page()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageCap.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET с from/to/page/size → значения прокинуты в use case 1-в-1")
    void explicitArgsArePropagated() throws Exception {
        Period period = new Period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        when(getDashboard.get(any(), any())).thenReturn(new Dashboard(
                period, new Page<>(List.of(), 2, 5, 0)));

        mvc.perform(get("/api/v2/dashboard")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(getDashboard).get(period, new PageRequest(2, 5));
    }
}
