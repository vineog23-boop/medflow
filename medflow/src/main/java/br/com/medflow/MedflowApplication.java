package br.com.medflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedflowApplication.class, args);
	}

}
