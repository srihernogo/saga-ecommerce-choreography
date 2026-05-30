package com.company.saga.shared.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class BaseEvent {

    /**
     * Unique identifier for this specific event instance.
     * Digunakan oleh consumer untuk Idempotency Check.
     */
    private String eventId;

    /**
     * Waktu event dibuat.
     */
    private Instant timestamp;

    /**
     * Identifier dari entity utama (misal: orderId).
     * Biasa digunakan sebagai Kafka partition key.
     */
    private String aggregateId;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    protected BaseEvent(String aggregateId) {
        this();
        this.aggregateId = aggregateId;
    }
}
