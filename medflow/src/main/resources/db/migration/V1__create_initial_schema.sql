CREATE TABLE patients (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    cpf VARCHAR(14) NOT NULL,
    birth_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE exam_definitions (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    sample_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE exam_orders (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    exam_code VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_exam_orders_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_exam_orders_definition
        FOREIGN KEY (exam_code) REFERENCES exam_definitions (code)
);

CREATE INDEX idx_exam_orders_patient_id ON exam_orders (patient_id);
CREATE INDEX idx_exam_orders_exam_code ON exam_orders (exam_code);
