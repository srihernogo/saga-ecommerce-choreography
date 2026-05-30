package com.company.saga.order.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
