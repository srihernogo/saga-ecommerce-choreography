package com.company.saga.order.service;

import com.company.saga.order.api.request.CreateOrderRequest;
import com.company.saga.order.domain.Order;
import com.company.saga.order.idempotency.IdempotencyRecord;
import com.company.saga.order.idempotency.IdempotencyRepository;
import com.company.saga.order.idempotency.ProcessedEventTracker;
import com.company.saga.order.metrics.SagaMetrics;
import com.company.saga.order.outbox.OutboxMessage;
import com.company.saga.order.outbox.OutboxRepository;
import com.company.saga.order.repository.OrderRepository;
import com.company.saga.order.repository.SagaStateRepository;
import com.company.saga.order.saga.SagaState;
import com.company.saga.order.saga.SagaStep;
import com.company.saga.shared.constant.KafkaTopics;
import com.company.saga.shared.dto.OrderItemDto;
import com.company.saga.shared.event.InventoryFailedEvent;
import com.company.saga.shared.event.InventoryReservedEvent;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.OrderCompletedEvent;
import com.company.saga.shared.event.OrderCreatedEvent;
import com.company.saga.shared.event.PaymentCompletedEvent;
import com.company.saga.shared.event.PaymentFailedEvent;
import com.company.saga.shared.event.ShippingCreatedEvent;
import com.company.saga.shared.event.ShippingFailedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final SagaStateRepository sagaStateRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ProcessedEventTracker eventTracker;
    private final ObjectMapper objectMapper;
    private final SagaMetrics sagaMetrics;

    @Transactional
    public Order createOrder(CreateOrderRequest request, String idempotencyKey) {
        Optional<IdempotencyRecord> record = idempotencyRepository.findById(idempotencyKey);
        if (record.isPresent()) {
            log.info("Idempotent request detected. Returning existing order for key: {}", idempotencyKey);
            return orderRepository.findById(record.get().getResourceId())
                .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key"));
        }

        List<Order.OrderItemData> itemData = request.getItems().stream()
            .map(i -> new Order.OrderItemData(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
            .toList();

        Order order = Order.create(
            request.getCustomerId(),
            itemData,
            request.getShippingAddress(),
            request.getPaymentMethod()
        );

        order = orderRepository.save(order);

        List<OrderItemDto> itemDtos = order.getItems().stream()
            .map(i -> new OrderItemDto(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
            .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotalAmount(),
            order.getPaymentMethod(),
            order.getShippingAddress(),
            itemDtos
        );

        saveOutboxMessage(order.getId(), KafkaTopics.ORDER_CREATED, event);

        SagaState sagaState = SagaState.start(order.getId());
        sagaStateRepository.save(sagaState);

        sagaMetrics.recordStarted();

        IdempotencyRecord idemp = new IdempotencyRecord(
            idempotencyKey,
            order.getId(),
            "ORDER",
            Instant.now(),
            Instant.now().plus(24, ChronoUnit.HOURS)
        );
        idempotencyRepository.save(idemp);

        return order;
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) {
            log.info("PaymentCompletedEvent {} already processed", event.getEventId());
            return;
        }

        Order order = orderRepository.findById(event.getAggregateId())
            .orElseThrow(() -> new IllegalStateException("Order not found"));

        SagaState sagaState = sagaStateRepository.findById(order.getId())
            .orElseThrow(() -> new IllegalStateException("Saga state not found"));

        order.confirmPayment(event.getPaymentId());
        orderRepository.save(order);

        sagaState.transitionTo(SagaStep.PAYMENT_COMPLETED);
        sagaState.setPaymentId(event.getPaymentId());
        sagaStateRepository.save(sagaState);

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) {
            return;
        }

        Order order = orderRepository.findById(event.getAggregateId()).orElseThrow();
        SagaState sagaState = sagaStateRepository.findById(order.getId()).orElseThrow();

        order.cancel("Payment failed: " + event.getReason(), "PAYMENT_SERVICE");
        orderRepository.save(order);

        sagaState.startCompensating(SagaStep.PAYMENT_FAILED, event.getReason(), "PAYMENT_SERVICE");
        sagaStateRepository.save(sagaState);
        
        sagaMetrics.recordCompensating("PAYMENT_FAILED");

        publishOrderCancelledEvent(order.getId(), event.getReason(), "PAYMENT_SERVICE");

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getAggregateId()).orElseThrow();
        SagaState sagaState = sagaStateRepository.findById(order.getId()).orElseThrow();

        order.confirmInventory(event.getReservationId());
        orderRepository.save(order);

        sagaState.transitionTo(SagaStep.INVENTORY_RESERVED);
        sagaState.setReservationId(event.getReservationId());
        sagaStateRepository.save(sagaState);

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getAggregateId()).orElseThrow();
        SagaState sagaState = sagaStateRepository.findById(order.getId()).orElseThrow();

        order.cancel("Inventory failed: " + event.getReason(), "INVENTORY_SERVICE");
        orderRepository.save(order);

        sagaState.startCompensating(SagaStep.INVENTORY_FAILED, event.getReason(), "INVENTORY_SERVICE");
        sagaStateRepository.save(sagaState);

        sagaMetrics.recordCompensating("INVENTORY_FAILED");
        
        publishOrderCancelledEvent(order.getId(), event.getReason(), "INVENTORY_SERVICE");

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void onShippingCreated(ShippingCreatedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getAggregateId()).orElseThrow();
        SagaState sagaState = sagaStateRepository.findById(order.getId()).orElseThrow();

        order.markShipped(event.getTrackingNumber());
        order.complete();
        orderRepository.save(order);

        sagaState.complete(SagaStep.COMPLETED);
        sagaState.setShipmentId(event.getTrackingNumber()); // Using tracking as shipment ID here
        sagaStateRepository.save(sagaState);

        sagaMetrics.recordCompleted(sagaState.getDurationMs());

        publishOrderCompletedEvent(order.getId());

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void onShippingFailed(ShippingFailedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getAggregateId()).orElseThrow();
        SagaState sagaState = sagaStateRepository.findById(order.getId()).orElseThrow();

        order.cancel("Shipping failed: " + event.getReason(), "SHIPPING_SERVICE");
        orderRepository.save(order);

        sagaState.startCompensating(SagaStep.SHIPPING_FAILED, event.getReason(), "SHIPPING_SERVICE");
        sagaStateRepository.save(sagaState);

        sagaMetrics.recordCompensating("SHIPPING_FAILED");
        
        publishOrderCancelledEvent(order.getId(), event.getReason(), "SHIPPING_SERVICE");

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    private void publishOrderCancelledEvent(String orderId, String reason, String failedBy) {
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(orderId, reason, failedBy);
        saveOutboxMessage(orderId, KafkaTopics.ORDER_CANCELLED, cancelledEvent);
    }
    
    private void publishOrderCompletedEvent(String orderId) {
        OrderCompletedEvent completedEvent = new OrderCompletedEvent(orderId);
        saveOutboxMessage(orderId, KafkaTopics.ORDER_COMPLETED, completedEvent);
    }

    private void saveOutboxMessage(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxMessage msg = OutboxMessage.create("Order", aggregateId, event.getClass().getSimpleName(), topic, payload);
            outboxRepository.save(msg);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
