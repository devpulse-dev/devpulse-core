package ru.x5.devpulse.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.x5.devpulse.application.port.out.KaitenCardWriter;
import ru.x5.devpulse.domain.model.kaiten.KaitenCardId;
import ru.x5.devpulse.domain.model.performance.AiAgentMarkResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarkDefectsAiAgentService (простановка AI-Agent в Kaiten)")
class MarkDefectsAiAgentServiceTest {

    @Mock private KaitenCardWriter writer;
    private MarkDefectsAiAgentService service;

    @BeforeEach
    void setUp() {
        service = new MarkDefectsAiAgentService(writer);
    }

    @Test
    @DisplayName("Все успешно → updated=N, failedIds пуст; writer вызван по каждой карточке")
    void allSucceed() {
        AiAgentMarkResult result = service.mark(List.of(new KaitenCardId(1), new KaitenCardId(2)));

        assertAll(
                () -> assertThat(result.requested()).isEqualTo(2),
                () -> assertThat(result.updated()).isEqualTo(2),
                () -> assertThat(result.failedIds()).isEmpty());
        verify(writer).markAiAgent(new KaitenCardId(1));
        verify(writer).markAiAgent(new KaitenCardId(2));
    }

    @Test
    @DisplayName("Частичный сбой: ошибка одной карточки → в failedIds, остальные обновлены")
    void partialFailure() {
        // lenient: строгий режим иначе бросил бы PotentialStubbingProblem (RuntimeException)
        // на вызовах с другими id — а сервис ловит любой RuntimeException, портя счёт.
        lenient().doThrow(new RuntimeException("kaiten 500"))
                .when(writer).markAiAgent(new KaitenCardId(2));

        AiAgentMarkResult result =
                service.mark(List.of(new KaitenCardId(1), new KaitenCardId(2), new KaitenCardId(3)));

        assertAll(
                () -> assertThat(result.requested()).isEqualTo(3),
                () -> assertThat(result.updated()).isEqualTo(2),
                () -> assertThat(result.failedIds()).containsExactly(new KaitenCardId(2)));
    }

    @Test
    @DisplayName("Пустой список → ничего не делаем")
    void emptyList() {
        AiAgentMarkResult result = service.mark(List.of());

        assertAll(
                () -> assertThat(result.requested()).isZero(),
                () -> assertThat(result.updated()).isZero(),
                () -> assertThat(result.failedIds()).isEmpty());
        verifyNoInteractions(writer);
    }
}
