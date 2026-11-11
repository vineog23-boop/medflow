package br.com.medflow.notification.web;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.notification.application.NotificationService;
import br.com.medflow.notification.application.dto.response.NotificationResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final MockMvc mockMvc = standaloneSetup(new NotificationController(notificationService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();

    @Test
    void deveListarTodasNotificacoesPaginadasQuandoSemFiltro() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        NotificationResponseDto dto = new NotificationResponseDto(
                UUID.randomUUID(),
                orderId,
                patientId,
                "HEMOGRAMA",
                ExamStatus.COLLECTED,
                "Sua amostra para o exame HEMOGRAMA foi coletada pelo laboratório.",
                Instant.parse("2026-10-13T10:00:00Z")
        );

        when(notificationService.findAll(isNull(), any())).thenReturn(new PageImpl<>(
                List.of(dto),
                PageRequest.of(0, 10),
                1
        ));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("COLLECTED"));

        verify(notificationService).findAll(isNull(), any());
    }

    @Test
    void deveFiltrarNotificacoesPorPacienteId() throws Exception {
        UUID patientId = UUID.fromString("76cb605b-71dc-47e8-83b5-83f2ef99a7cf");
        when(notificationService.findAll(eq(patientId), any())).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)
        );

        mockMvc.perform(get("/notifications").param("patientId", patientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(notificationService).findAll(eq(patientId), any());
    }
}
