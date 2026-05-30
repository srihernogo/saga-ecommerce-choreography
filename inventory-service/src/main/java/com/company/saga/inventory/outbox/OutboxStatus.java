package com.company.saga.inventory.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
