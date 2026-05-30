package com.company.saga.order.api.response;

import com.company.saga.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {
    private String orderId;
    private String customerId;
    private String status;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private String cancellationReason;
    private Instant createdAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .status(order.getStatus().name())
            .paymentMethod(order.getPaymentMethod())
            .totalAmount(order.getTotalAmount())
            .cancellationReason(order.getCancellationReason())
            .createdAt(order.getCreatedAt())
            .build();
    }
}
