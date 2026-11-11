package br.com.medflow.exam.order.domain.event;

import br.com.medflow.exam.order.domain.enums.ExamPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExamOrderCreatedEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void deveSerializarEDesserializarEventoMantendoOsDados() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                orderId,
                patientId,
                "HEMOGRAMA",
                ExamPriority.URGENT
        );

        String json = objectMapper.writeValueAsString(event);
        ExamOrderCreatedEvent deserialized = objectMapper.readValue(json, ExamOrderCreatedEvent.class);

        assertThat(deserialized.eventId()).isEqualTo(event.eventId());
        assertThat(deserialized.orderId()).isEqualTo(orderId);
        assertThat(deserialized.patientId()).isEqualTo(patientId);
        assertThat(deserialized.examCode()).isEqualTo("HEMOGRAMA");
        assertThat(deserialized.priority()).isEqualTo(ExamPriority.URGENT);
        assertThat(deserialized.createdAt()).isNotNull();
    }
}
