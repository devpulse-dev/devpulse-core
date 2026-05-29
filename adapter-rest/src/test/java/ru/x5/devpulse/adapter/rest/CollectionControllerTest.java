package ru.x5.devpulse.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;

@WebMvcTest(CollectionController.class)
@DisplayName("CollectionController (/api/v2/collection/runs)")
class CollectionControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CollectDailyStatsUseCase collectDailyStats;
    @MockitoBean GetCollectionRunUseCase getCollectionRun;

    @Test
    @DisplayName("POST без тела → запуск с since=null, возвращает финальный run (SUCCESS)")
    void startWithoutBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(collectDailyStats.run(any())).thenReturn(new CollectionRun(
                id, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59),
                CollectionStatus.SUCCESS, null));

        mvc.perform(post("/api/v2/collection/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("POST с since → значение прокидывается в use case")
    void startWithSince() throws Exception {
        UUID id = UUID.randomUUID();
        when(collectDailyStats.run(any())).thenReturn(new CollectionRun(
                id, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.of(2026, 5, 10, 0, 0),
                LocalDateTime.now(),
                CollectionStatus.SUCCESS, null));

        mvc.perform(post("/api/v2/collection/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"since\":\"2026-05-10T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET /{id} есть → 200; нет → 404")
    void getById() throws Exception {
        UUID id = UUID.randomUUID();
        when(getCollectionRun.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v2/collection/runs/" + id))
                .andExpect(status().isNotFound());
    }
}
