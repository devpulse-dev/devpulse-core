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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.x5.devpulse.application.port.in.CancelCollectionUseCase;
import ru.x5.devpulse.application.port.in.CollectDailyStatsUseCase;
import ru.x5.devpulse.application.port.in.GetCollectionRunUseCase;
import ru.x5.devpulse.application.port.out.CollectionAlreadyRunningException;
import ru.x5.devpulse.application.port.out.CollectionRunNotCancellableException;
import ru.x5.devpulse.domain.model.collection.CollectionRun;
import ru.x5.devpulse.domain.model.collection.CollectionStatus;

@WebMvcTest(CollectionController.class)
@Import(RestMappersTestConfig.class)
@DisplayName("CollectionController (/api/v2/collection/runs)")
class CollectionControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CollectDailyStatsUseCase collectDailyStats;
    @MockitoBean CancelCollectionUseCase cancelCollection;
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

    @Test
    @DisplayName("GET /runs/latest → 200 + run (и роутится сюда, а не в /{id})")
    void latestReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(getCollectionRun.findLatest()).thenReturn(Optional.of(new CollectionRun(
                id, LocalDateTime.now(), null,
                LocalDateTime.of(2026, 5, 1, 0, 0), LocalDateTime.now(),
                CollectionStatus.RUNNING, null)));

        mvc.perform(get("/api/v2/collection/runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /runs/latest когда прогонов не было → 404")
    void latestReturns404() throws Exception {
        when(getCollectionRun.findLatest()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v2/collection/runs/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST когда сбор уже идёт → 409 Conflict + RFC 7807 problem")
    void startWhenAlreadyRunning() throws Exception {
        when(collectDailyStats.run(any())).thenThrow(new CollectionAlreadyRunningException());

        mvc.perform(post("/api/v2/collection/runs"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:conflict"))
                .andExpect(jsonPath("$.title").value("Collection already running"));
    }

    @Test
    @DisplayName("POST /{id}/cancel для RUNNING → 202 + run")
    void cancelRunningReturns202() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancelCollection.cancel(id)).thenReturn(Optional.of(new CollectionRun(
                id, LocalDateTime.now(), null,
                LocalDateTime.of(2026, 5, 1, 0, 0), LocalDateTime.now(),
                CollectionStatus.RUNNING, null)));

        mvc.perform(post("/api/v2/collection/runs/" + id + "/cancel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("POST /{id}/cancel для несуществующего → 404")
    void cancelMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancelCollection.cancel(id)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v2/collection/runs/" + id + "/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/cancel для терминального → 409 + RFC 7807 problem")
    void cancelTerminalReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(cancelCollection.cancel(id))
                .thenThrow(new CollectionRunNotCancellableException(id, CollectionStatus.SUCCESS));

        mvc.perform(post("/api/v2/collection/runs/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:devpulse:problem:conflict"))
                .andExpect(jsonPath("$.title").value("Collection run not cancellable"));
    }
}
