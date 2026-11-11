package br.com.medflow;

import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.exam.order.persistence.ExamResultRepository;
import br.com.medflow.notification.persistence.NotificationRepository;
import br.com.medflow.patient.persistence.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
		+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
		+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
class MedflowApplicationTests {

	@MockitoBean
	private ExamOrderRepository examOrderRepository;

	@MockitoBean
	private ExamDefinitionRepository examDefinitionRepository;

	@MockitoBean
	private PatientRepository patientRepository;

	@MockitoBean
	private br.com.medflow.messaging.outbox.persistence.OutboxEventRepository outboxEventRepository;

	@MockitoBean
	private br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository processedEventRepository;

	@MockitoBean
	private ExamResultRepository examResultRepository;

	@MockitoBean
	private NotificationRepository notificationRepository;

	@Test
	void contextLoads() {
	}

}
