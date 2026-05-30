package com.company.saga.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(length = 100)
    private String courier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Shipment create(String orderId) {
        Shipment s = new Shipment();
        s.id = UUID.randomUUID().toString();
        s.orderId = orderId;
        s.status = ShipmentStatus.PENDING;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void markShipped(String trackingNumber, String courier) {
        this.status = ShipmentStatus.SHIPPED;
        this.trackingNumber = trackingNumber;
        this.courier = courier;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = ShipmentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
