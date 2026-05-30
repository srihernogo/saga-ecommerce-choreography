package com.company.saga.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    private String id;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Column(name = "shipment_tracking_number", length = 100)
    private String shipmentTrackingNumber;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // ── Factory Method ───────────────────────────────────────────────────

    public static Order create(String customerId, List<OrderItemData> itemData,
                               String shippingAddress, String paymentMethod) {
        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.shippingAddress = shippingAddress;
        order.paymentMethod = paymentMethod;
        order.createdAt = Instant.now();
        order.updatedAt = Instant.now();
        
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemData data : itemData) {
            OrderItem item = new OrderItem(order, data.productId(), data.productName(),
                                           data.quantity(), data.unitPrice());
            order.items.add(item);
            total = total.add(data.unitPrice().multiply(BigDecimal.valueOf(data.quantity())));
        }
        order.totalAmount = total;
        return order;
    }

    // ── Business Logic / State Transitions ───────────────────────────────

    public void confirmPayment(String paymentId) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not PENDING, current status: " + this.status);
        }
        this.status = OrderStatus.PAYMENT_CONFIRMED;
        this.paymentId = paymentId;
        this.updatedAt = Instant.now();
    }

    public void confirmInventory(String reservationId) {
        if (this.status != OrderStatus.PAYMENT_CONFIRMED) {
            throw new IllegalStateException("Order is not PAYMENT_CONFIRMED");
        }
        this.status = OrderStatus.INVENTORY_CONFIRMED;
        this.reservationId = reservationId;
        this.updatedAt = Instant.now();
    }

    public void markShipped(String trackingNumber) {
        if (this.status != OrderStatus.INVENTORY_CONFIRMED) {
            throw new IllegalStateException("Order is not INVENTORY_CONFIRMED");
        }
        this.status = OrderStatus.SHIPPED;
        this.shipmentTrackingNumber = trackingNumber;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Order is not SHIPPED");
        }
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason, String failedBy) {
        if (this.status == OrderStatus.CANCELLED) return; // Idempotent
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel an order that is already shipped or completed");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();
    }

    public record OrderItemData(String productId, String productName, int quantity, BigDecimal unitPrice) {}
}
