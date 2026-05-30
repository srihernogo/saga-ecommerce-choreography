package com.company.saga.order.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saga_states")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class SagaState {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Column(name = "shipment_id", length = 36)
    private String shipmentId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failed_by", length = 50)
    private String failedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    public static SagaState start(String orderId) {
        return SagaState.builder()
            .orderId(orderId)
            .currentStep(SagaStep.ORDER_CREATED)
            .status(SagaStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .lastUpdatedAt(Instant.now())
            .build();
    }

    public void transitionTo(SagaStep step) {
        this.currentStep = step;
        this.lastUpdatedAt = Instant.now();
    }

    public void complete(SagaStep finalStep) {
        this.currentStep = finalStep;
        this.status = SagaStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    public void fail(SagaStep failStep, String reason, String failedBy) {
        this.currentStep = failStep;
        this.status = SagaStatus.FAILED;
        this.failureReason = reason;
        this.failedBy = failedBy;
        this.completedAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
    }

    public void startCompensating(SagaStep step, String reason, String failedBy) {
        this.currentStep = step;
        this.status = SagaStatus.COMPENSATING;
        this.failureReason = reason;
        this.failedBy = failedBy;
        this.lastUpdatedAt = Instant.now();
    }

    public long getDurationMs() {
        if (completedAt == null) return -1L;
        return Duration.between(startedAt, completedAt).toMillis();
    }

    public long getAgeMs() {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
