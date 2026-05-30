package com.company.saga.shared.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.company.saga.shared.dto.OrderItemDto;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class OrderCreatedEvent extends BaseEvent {

    private String customerId;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String shippingAddress;
    private List<OrderItemDto> items;

    public OrderCreatedEvent(String orderId, String customerId, BigDecimal totalAmount,
                             String paymentMethod, String shippingAddress, List<OrderItemDto> items) {
        super(orderId);
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.shippingAddress = shippingAddress;
        this.items = items;
    }
}
