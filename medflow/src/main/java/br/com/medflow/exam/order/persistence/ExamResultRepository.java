package br.com.medflow.exam.order.persistence;

import br.com.medflow.exam.order.domain.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    Optional<ExamResult> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
