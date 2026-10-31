package br.com.medflow.messaging.consumer.persistence;

import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    default boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup) {
        return existsById(new ProcessedEventId(eventId, consumerGroup));
    }
}
