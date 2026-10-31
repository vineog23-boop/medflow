package br.com.medflow.persistence;

import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.domain.enums.SampleType;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.ExamOrderService;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.patient.domain.PatientEntity;
import br.com.medflow.patient.persistence.PatientRepository;
import br.com.medflow.support.PostgreSqlTestContainerConfiguration;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgreSqlTestContainerConfiguration.class)
@Transactional
class MedflowPersistenceIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExamDefinitionRepository examDefinitionRepository;

    @Autowired
    private ExamOrderRepository examOrderRepository;

    @Autowired
    private ExamOrderService examOrderService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deveAplicarMigrationInicialEValidarAsTabelas() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(tables).contains("patients", "exam_definitions", "exam_orders", "outbox_events", "processed_events");
    }

    @Test
    void deveCriarERecuperarOrdemComComponentesReais() {
        PatientEntity patient = patientRepository.saveAndFlush(new PatientEntity(
                "Maria da Silva",
                "52998224725",
                LocalDate.of(1990, 5, 20)
        ));
        examDefinitionRepository.saveAndFlush(new ExamDefinition(
                "HEMOGRAMA",
                "Hemograma completo",
                SampleType.BLOOD
        ));

        ExamOrderResponseDto response = examOrderService.create(new CreateExamRequestDto(
                patient.getId(),
                " hemograma ",
                ExamPriority.URGENT
        ));
        entityManager.flush();
        entityManager.clear();

        ExamOrder persisted = examOrderRepository.findById(response.id()).orElseThrow();

        assertThat(persisted.getPatientId()).isEqualTo(patient.getId());
        assertThat(persisted.getExamCode()).isEqualTo("HEMOGRAMA");
        assertThat(persisted.getPriority()).isEqualTo(ExamPriority.URGENT);
        assertThat(persisted.getStatus()).isEqualTo(ExamStatus.RECEIVED);
    }

    @Test
    void deveRejeitarOrdemComPacienteInexistentePelaChaveEstrangeira() {
        examDefinitionRepository.saveAndFlush(new ExamDefinition(
                "HEMOGRAMA",
                "Hemograma completo",
                SampleType.BLOOD
        ));
        ExamOrder order = new ExamOrder(
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );

        assertThatThrownBy(() -> examOrderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarOrdemComDefinicaoInexistentePelaChaveEstrangeira() {
        PatientEntity patient = patientRepository.saveAndFlush(new PatientEntity(
                "João da Silva",
                "11144477735",
                LocalDate.of(1985, 8, 10)
        ));
        ExamOrder order = new ExamOrder(
                patient.getId(),
                "INEXISTENTE",
                ExamPriority.NORMAL
        );

        assertThatThrownBy(() -> examOrderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
