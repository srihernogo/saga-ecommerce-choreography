package com.company.saga.order.saga;

public enum SagaStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    FAILED
}
