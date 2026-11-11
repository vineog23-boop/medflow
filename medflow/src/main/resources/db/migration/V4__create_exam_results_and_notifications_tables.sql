CREATE TABLE exam_results (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    patient_id UUID NOT NULL,
    exam_code VARCHAR(50) NOT NULL,
    result_text TEXT NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_exam_results_order
        FOREIGN KEY (order_id) REFERENCES exam_orders (id),
    CONSTRAINT fk_exam_results_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id)
);

CREATE INDEX idx_exam_results_patient_id ON exam_results (patient_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    order_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    exam_code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notifications_order
        FOREIGN KEY (order_id) REFERENCES exam_orders (id),
    CONSTRAINT fk_notifications_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id)
);

CREATE INDEX idx_notifications_patient_created_at ON notifications (patient_id, created_at DESC);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
