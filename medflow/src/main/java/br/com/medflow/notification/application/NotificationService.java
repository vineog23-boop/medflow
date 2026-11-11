package br.com.medflow.notification.application;

import br.com.medflow.notification.application.dto.response.NotificationResponseDto;
import br.com.medflow.notification.persistence.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Page<NotificationResponseDto> findAll(UUID patientId, Pageable pageable) {
        if (patientId != null) {
            return notificationRepository.findByPatientId(patientId, pageable)
                    .map(NotificationResponseDto::new);
        }

        return notificationRepository.findAll(pageable)
                .map(NotificationResponseDto::new);
    }
}
