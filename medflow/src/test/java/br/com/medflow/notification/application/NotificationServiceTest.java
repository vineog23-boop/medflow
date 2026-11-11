package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.notification.application.dto.response.NotificationResponseDto;
import br.com.medflow.notification.domain.Notification;
import br.com.medflow.notification.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository);

    @Test
    void deveListarTodasNotificacoesQuandoPatientIdForNulo() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID patientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Notification notification = new Notification(
                UUID.randomUUID(),
                orderId,
                patientId,
                "HEMOGRAMA",
                ExamStatus.COLLECTED,
                "Amostra coletada."
        );
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(notification), pageable, 1));

        Page<NotificationResponseDto> result = service.findAll(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().orderId()).isEqualTo(orderId);
        assertThat(result.getContent().getFirst().status()).isEqualTo(ExamStatus.COLLECTED);
        verify(repository).findAll(pageable);
        verify(repository, never()).findByPatientId(patientId, pageable);
    }

    @Test
    void deveFiltrarNotificacoesPorPatientIdQuandoInformado() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID patientId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Notification notification = new Notification(
                UUID.randomUUID(),
                orderId,
                patientId,
                "HEMOGRAMA",
                ExamStatus.RELEASED,
                "Resultado liberado."
        );
        when(repository.findByPatientId(patientId, pageable))
                .thenReturn(new PageImpl<>(List.of(notification), pageable, 1));

        Page<NotificationResponseDto> result = service.findAll(patientId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().patientId()).isEqualTo(patientId);
        assertThat(result.getContent().getFirst().status()).isEqualTo(ExamStatus.RELEASED);
        verify(repository).findByPatientId(patientId, pageable);
        verify(repository, never()).findAll(pageable);
    }
}
