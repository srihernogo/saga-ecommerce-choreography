package com.company.saga.payment.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
