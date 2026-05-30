package com.company.saga.order.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_CONFIRMED,
    INVENTORY_CONFIRMED,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
