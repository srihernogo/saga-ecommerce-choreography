package com.company.saga.shipping.messaging;

import com.company.saga.shared.event.InventoryReservedEvent;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shipping.service.ShippingService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ShippingEventConsumer {

    private final ShippingService shippingService;

    @Bean
    public Consumer<InventoryReservedEvent> inventoryReserved() {
        return event -> {
            log.info("Received InventoryReservedEvent for order: {}", event.getAggregateId());
            shippingService.arrangeShipping(event);
        };
    }

    @Bean
    public Consumer<OrderCancelledEvent> orderCancelled() {
        return event -> {
            log.info("Received OrderCancelledEvent for order: {}", event.getAggregateId());
            shippingService.cancelShipping(event);
        };
    }
}
