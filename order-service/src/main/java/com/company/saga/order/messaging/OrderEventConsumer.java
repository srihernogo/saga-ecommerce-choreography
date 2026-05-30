package com.company.saga.order.messaging;

import com.company.saga.order.service.OrderService;
import com.company.saga.shared.event.InventoryFailedEvent;
import com.company.saga.shared.event.InventoryReservedEvent;
import com.company.saga.shared.event.PaymentCompletedEvent;
import com.company.saga.shared.event.PaymentFailedEvent;
import com.company.saga.shared.event.ShippingCreatedEvent;
import com.company.saga.shared.event.ShippingFailedEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderService orderService;

    @Bean
    public Consumer<PaymentCompletedEvent> paymentCompleted() {
        return event -> {
            log.info("Received PaymentCompletedEvent for order: {}", event.getAggregateId());
            orderService.onPaymentCompleted(event);
        };
    }

    @Bean
    public Consumer<PaymentFailedEvent> paymentFailed() {
        return event -> {
            log.info("Received PaymentFailedEvent for order: {}", event.getAggregateId());
            orderService.onPaymentFailed(event);
        };
    }

    @Bean
    public Consumer<InventoryReservedEvent> inventoryReserved() {
        return event -> {
            log.info("Received InventoryReservedEvent for order: {}", event.getAggregateId());
            orderService.onInventoryReserved(event);
        };
    }

    @Bean
    public Consumer<InventoryFailedEvent> inventoryFailed() {
        return event -> {
            log.info("Received InventoryFailedEvent for order: {}", event.getAggregateId());
            orderService.onInventoryFailed(event);
        };
    }

    @Bean
    public Consumer<ShippingCreatedEvent> shippingCreated() {
        return event -> {
            log.info("Received ShippingCreatedEvent for order: {}", event.getAggregateId());
            orderService.onShippingCreated(event);
        };
    }

    @Bean
    public Consumer<ShippingFailedEvent> shippingFailed() {
        return event -> {
            log.info("Received ShippingFailedEvent for order: {}", event.getAggregateId());
            orderService.onShippingFailed(event);
        };
    }
}
