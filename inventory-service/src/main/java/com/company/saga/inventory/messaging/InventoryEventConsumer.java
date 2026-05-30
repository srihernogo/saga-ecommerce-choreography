package com.company.saga.inventory.messaging;

import com.company.saga.inventory.service.InventoryService;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.PaymentCompletedEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @Bean
    public Consumer<PaymentCompletedEvent> paymentCompleted() {
        return event -> {
            log.info("Received PaymentCompletedEvent for order: {}", event.getAggregateId());
            inventoryService.reserveInventory(event);
        };
    }

    @Bean
    public Consumer<OrderCancelledEvent> orderCancelled() {
        return event -> {
            log.info("Received OrderCancelledEvent for order: {}", event.getAggregateId());
            inventoryService.releaseInventory(event);
        };
    }
}
