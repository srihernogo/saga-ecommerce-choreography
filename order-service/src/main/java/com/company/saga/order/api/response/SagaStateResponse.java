package com.company.saga.order.api.response;

import com.company.saga.order.saga.SagaState;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SagaStateResponse {
    private String orderId;
    private String currentStep;
    private String status;
    private String paymentId;
    private String reservationId;
    private String shipmentId;
    private String failureReason;
    private String failedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastUpdatedAt;
    private long ageMs;
    private long durationMs;

    public static SagaStateResponse from(SagaState s) {
        return SagaStateResponse.builder()
            .orderId(s.getOrderId())
            .currentStep(s.getCurrentStep().name())
            .status(s.getStatus().name())
            .paymentId(s.getPaymentId())
            .reservationId(s.getReservationId())
            .shipmentId(s.getShipmentId())
            .failureReason(s.getFailureReason())
            .failedBy(s.getFailedBy())
            .startedAt(s.getStartedAt())
            .completedAt(s.getCompletedAt())
            .lastUpdatedAt(s.getLastUpdatedAt())
            .ageMs(s.getAgeMs())
            .durationMs(s.getDurationMs())
            .build();
    }
}
