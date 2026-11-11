package br.com.medflow.exam.order.persistence;

import br.com.medflow.exam.order.domain.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    Optional<ExamResult> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
