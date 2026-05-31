package com.company.saga.order.service;

import com.company.saga.order.domain.Order;
import com.company.saga.order.domain.OrderStatus;
import com.company.saga.order.api.request.CreateOrderRequest;
import com.company.saga.order.idempotency.IdempotencyRecord;
import com.company.saga.order.idempotency.IdempotencyRepository;
import com.company.saga.order.metrics.SagaMetrics;
import com.company.saga.order.idempotency.ProcessedEventTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.saga.order.outbox.OutboxRepository;
import com.company.saga.order.repository.OrderRepository;
import com.company.saga.order.repository.SagaStateRepository;
import com.company.saga.order.saga.SagaState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 * These tests focus on the happy‑path of order creation and the interaction
 * with the outbox and idempotency layers.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ProcessedEventTracker processedEventTracker;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SagaMetrics sagaMetrics;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_successful() throws Exception {
        // Arrange – build a minimal Order entity using the domain factory method
        Order order = Order.create(
                "cust-123",
                java.util.Collections.emptyList(),
                "123 Main St",
                "CREDIT_CARD");

        // Simulate repository save returning the same order
        when(orderRepository.save(any())).thenReturn(order);
        // Idempotency record does not exist yet
        when(idempotencyRepository.findById(anyString()))
                .thenReturn(Optional.empty());
        // Saga state repository save returns a saga state
        SagaState sagaState = SagaState.start(order.getId());
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(sagaState);
        // Stub out the metrics and event tracker to avoid NPEs
        doNothing().when(sagaMetrics).recordStarted();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxRepository.save(any())).thenReturn(null);

        // Act – invoke the method under test using a CreateOrderRequest
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-123");
        request.setShippingAddress("123 Main St");
        request.setPaymentMethod("CREDIT_CARD");
        request.setItems(Collections.emptyList());

        Order result = orderService.createOrder(request, "idem-key-1");

        // Assert – order is persisted and returned
        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getStatus());

        // Verify that an idempotency record was stored
        ArgumentCaptor<IdempotencyRecord> idemCaptor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRepository, times(1)).save(idemCaptor.capture());
        assertEquals("idem-key-1", idemCaptor.getValue().getIdempotencyKey());

        // Verify that an outbox message was saved via the repository
        verify(outboxRepository, times(1)).save(any());
    }
}
