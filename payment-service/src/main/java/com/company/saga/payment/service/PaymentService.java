package com.company.saga.payment.service;

import com.company.saga.payment.domain.Payment;
import com.company.saga.payment.gateway.MidtransGateway;
import com.company.saga.payment.idempotency.ProcessedEventTracker;
import com.company.saga.payment.outbox.OutboxMessage;
import com.company.saga.payment.outbox.OutboxRepository;
import com.company.saga.payment.repository.PaymentRepository;
import com.company.saga.shared.constant.KafkaTopics;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.OrderCreatedEvent;
import com.company.saga.shared.event.PaymentCompletedEvent;
import com.company.saga.shared.event.PaymentFailedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventTracker eventTracker;
    private final MidtransGateway midtransGateway;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Processing payment for order {}", event.getAggregateId());

        Payment payment = Payment.create(
            event.getAggregateId(),
            event.getTotalAmount(),
            event.getPaymentMethod()
        );

        MidtransGateway.PaymentResult result = midtransGateway.charge(
            payment.getOrderId(),
            payment.getAmount(),
            payment.getPaymentMethod()
        );

        if (result.success()) {
            payment.markSuccess(result.referenceId());
            paymentRepository.save(payment);

            PaymentCompletedEvent completedEvent = new PaymentCompletedEvent(
                payment.getOrderId(),
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getExternalReference()
            );
            saveOutboxMessage(payment.getOrderId(), KafkaTopics.PAYMENT_COMPLETED, completedEvent);
        } else {
            payment.markFailed(result.errorMessage());
            paymentRepository.save(payment);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                payment.getOrderId(),
                result.errorMessage(),
                "CHARGE_FAILED"
            );
            saveOutboxMessage(payment.getOrderId(), KafkaTopics.PAYMENT_FAILED, failedEvent);
        }

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void processRefund(OrderCancelledEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Processing refund (compensation) for order {}", event.getAggregateId());

        paymentRepository.findByOrderId(event.getAggregateId())
            .ifPresent(payment -> {
                payment.markRefunded();
                paymentRepository.save(payment);
                log.info("Payment refunded for order {}", payment.getOrderId());
            });

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    private void saveOutboxMessage(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxMessage msg = OutboxMessage.create("Payment", aggregateId, event.getClass().getSimpleName(), topic, payload);
            outboxRepository.save(msg);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
