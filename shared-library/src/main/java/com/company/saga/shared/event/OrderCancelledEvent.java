package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class OrderCancelledEvent extends BaseEvent {

    private String reason;
    private String failedByService;

    public OrderCancelledEvent(String orderId, String reason, String failedByService) {
        super(orderId);
        this.reason = reason;
        this.failedByService = failedByService;
    }
}
