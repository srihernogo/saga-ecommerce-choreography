package com.company.saga.payment.messaging;

import com.company.saga.payment.service.PaymentService;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.OrderCreatedEvent;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> {
            log.info("Received OrderCreatedEvent for order: {}", event.getAggregateId());
            paymentService.processPayment(event);
        };
    }

    @Bean
    public Consumer<OrderCancelledEvent> orderCancelled() {
        return event -> {
            log.info("Received OrderCancelledEvent for order: {}", event.getAggregateId());
            paymentService.processRefund(event);
        };
    }
}
