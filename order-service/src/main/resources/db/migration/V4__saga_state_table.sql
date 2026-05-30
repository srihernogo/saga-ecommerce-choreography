CREATE TABLE saga_states (
    order_id VARCHAR(36) PRIMARY KEY,
    current_step VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_id VARCHAR(36),
    reservation_id VARCHAR(36),
    shipment_id VARCHAR(36),
    failure_reason TEXT,
    failed_by VARCHAR(50),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_saga_status ON saga_states(status);
CREATE INDEX idx_saga_last_updated ON saga_states(last_updated_at);
-- Partial index
CREATE INDEX idx_saga_in_progress ON saga_states(last_updated_at) WHERE status = 'IN_PROGRESS';
