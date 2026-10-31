package br.com.medflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestContainerConfiguration {

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
    }

    @Bean
    KafkaContainer kafkaContainer() {
        return KAFKA;
    }
}
