package com.company.saga.shared.constant;

public final class KafkaTopics {

    private KafkaTopics() {
        // Prevent instantiation
    }

    // ── Order Service Topics ───────────────────────────────────────────────
    public static final String ORDER_CREATED    = "order.created";
    public static final String ORDER_CANCELLED  = "order.cancelled";
    public static final String ORDER_COMPLETED  = "order.completed";

    // ── Payment Service Topics ─────────────────────────────────────────────
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED    = "payment.failed";
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund.requested";
    public static final String PAYMENT_REFUNDED  = "payment.refunded";

    // ── Inventory Service Topics ───────────────────────────────────────────
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_FAILED   = "inventory.failed";
    public static final String INVENTORY_RELEASED = "inventory.released";

    // ── Shipping Service Topics ────────────────────────────────────────────
    public static final String SHIPPING_CREATED = "shipping.created";
    public static final String SHIPPING_FAILED  = "shipping.failed";
}
