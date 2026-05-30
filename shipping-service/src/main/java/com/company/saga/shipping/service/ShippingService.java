package com.company.saga.shipping.service;

import com.company.saga.shared.constant.KafkaTopics;
import com.company.saga.shared.event.InventoryReservedEvent;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.ShippingCreatedEvent;
import com.company.saga.shipping.domain.Shipment;
import com.company.saga.shipping.idempotency.ProcessedEventTracker;
import com.company.saga.shipping.outbox.OutboxMessage;
import com.company.saga.shipping.outbox.OutboxRepository;
import com.company.saga.shipping.repository.ShipmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventTracker eventTracker;
    private final ObjectMapper objectMapper;

    @Transactional
    public void arrangeShipping(InventoryReservedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Arranging shipping for order {}", event.getAggregateId());

        Shipment shipment = Shipment.create(event.getAggregateId());
        
        // Simulate courier integration
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        shipment.markShipped(trackingNumber, "FedEx");
        
        shipmentRepository.save(shipment);

        ShippingCreatedEvent createdEvent = new ShippingCreatedEvent(
            shipment.getOrderId(),
            shipment.getTrackingNumber(),
            shipment.getCourier()
        );
        saveOutboxMessage(shipment.getOrderId(), KafkaTopics.SHIPPING_CREATED, createdEvent);

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void cancelShipping(OrderCancelledEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Cancelling shipping for order {}", event.getAggregateId());

        shipmentRepository.findByOrderId(event.getAggregateId())
            .ifPresent(shipment -> {
                shipment.cancel();
                shipmentRepository.save(shipment);
            });

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    private void saveOutboxMessage(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxMessage msg = OutboxMessage.create("Shipping", aggregateId, event.getClass().getSimpleName(), topic, payload);
            outboxRepository.save(msg);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
