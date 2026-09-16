package br.com.medflow.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class PatientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotBlank
    @CPF
    @Column(name = "cpf", nullable = false, length = 14)
    private String cpf;

    @NotNull
    @PastOrPresent
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PatientEntity() {
    }

    public PatientEntity(String fullName, String cpf, LocalDate birthDate) {
        validateData(fullName, cpf, birthDate);

        this.fullName = fullName.trim();
        this.cpf = cpf.trim();
        this.birthDate = birthDate;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String fullName, String cpf, LocalDate birthDate) {
        validateData(fullName, cpf, birthDate);

        this.fullName = fullName.trim();
        this.cpf = cpf.trim();
        this.birthDate = birthDate;
        this.updatedAt = Instant.now();
    }

    private static void validateData(String fullName, String cpf, LocalDate birthDate) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nome completo deve ser informado");
        }

        if (fullName.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Nome completo deve ter no máximo 150 caracteres");
        }

        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("CPF deve ser informado");
        }

        if (birthDate == null) {
            throw new IllegalArgumentException("Data de nascimento deve ser informada");
        }

        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Data de nascimento não pode estar no futuro");
        }
    }

    public UUID getId() {
        return id;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCpf() {
        return cpf;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
