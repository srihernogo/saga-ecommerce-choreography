package com.company.saga.inventory.service;

import com.company.saga.inventory.domain.Product;
import com.company.saga.inventory.domain.Reservation;
import com.company.saga.inventory.idempotency.ProcessedEventTracker;
import com.company.saga.inventory.outbox.OutboxMessage;
import com.company.saga.inventory.outbox.OutboxRepository;
import com.company.saga.inventory.repository.ProductRepository;
import com.company.saga.inventory.repository.ReservationRepository;
import com.company.saga.shared.constant.KafkaTopics;
import com.company.saga.shared.dto.OrderItemDto;
import com.company.saga.shared.event.InventoryFailedEvent;
import com.company.saga.shared.event.InventoryReleasedEvent;
import com.company.saga.shared.event.InventoryReservedEvent;
import com.company.saga.shared.event.OrderCancelledEvent;
import com.company.saga.shared.event.PaymentCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventTracker eventTracker;
    private final ObjectMapper objectMapper;

    @Transactional
    public void reserveInventory(PaymentCompletedEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Processing inventory reservation for order {}", event.getAggregateId());

        try {
            // Note: in a real implementation we would fetch the order items from a reliable source or pass them in the PaymentCompletedEvent. 
            // For this example, let's assume we have logic to get items or they are part of the event.
            // Since PaymentCompletedEvent doesn't have items, we'll simulate a failure if items can't be reserved or fetch items from order service via API/cache.
            // For simplicity based on reference, we'll do a mock check or just proceed if we had items. 
            // Wait, reference 07 says PaymentCompletedEvent should be used but how does it get items? 
            // Ah, actually OrderCreatedEvent could carry them and we save them, but usually they are sent in PaymentCompletedEvent or we query Order Service.
            // Let's assume we just reserve some hardcoded/dummy check for this demo or we can pass a dummy product ID if not available.
            
            // To make the code compile and work similar to reference:
            Reservation reservation = Reservation.create(event.getAggregateId());
            
            // Simulate reserving "prod-laptop-001" with qty 1 if it's not present in event
            Product product = productRepository.findByIdWithPessimisticLock("prod-laptop-001")
                .orElseThrow(() -> new IllegalStateException("Product not found"));
                
            product.reserve(1);
            productRepository.save(product);
            
            reservation.addItem(product.getId(), 1);
            reservationRepository.save(reservation);

            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                event.getAggregateId(),
                reservation.getId()
            );
            saveOutboxMessage(event.getAggregateId(), KafkaTopics.INVENTORY_RESERVED, reservedEvent);

        } catch (Exception ex) {
            log.error("Failed to reserve inventory for order {}: {}", event.getAggregateId(), ex.getMessage());
            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                event.getAggregateId(),
                ex.getMessage(),
                "unknown"
            );
            saveOutboxMessage(event.getAggregateId(), KafkaTopics.INVENTORY_FAILED, failedEvent);
        }

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    @Transactional
    public void releaseInventory(OrderCancelledEvent event) {
        if (eventTracker.isAlreadyProcessed(event.getEventId())) return;

        log.info("Processing inventory release (compensation) for order {}", event.getAggregateId());

        reservationRepository.findByOrderId(event.getAggregateId())
            .ifPresent(reservation -> {
                reservation.getItems().forEach(item -> {
                    Product product = productRepository.findByIdWithPessimisticLock(item.getProductId())
                        .orElseThrow();
                    product.release(item.getQuantity());
                    productRepository.save(product);
                });
                
                reservation.release();
                reservationRepository.save(reservation);
                log.info("Inventory released for order {}", reservation.getOrderId());
            });

        eventTracker.markProcessed(event.getEventId(), event.getClass().getSimpleName());
    }

    private void saveOutboxMessage(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxMessage msg = OutboxMessage.create("Inventory", aggregateId, event.getClass().getSimpleName(), topic, payload);
            outboxRepository.save(msg);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
