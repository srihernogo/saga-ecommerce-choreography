package com.company.saga.shipping.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
