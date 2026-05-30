package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class OrderCompletedEvent extends BaseEvent {

    public OrderCompletedEvent(String orderId) {
        super(orderId);
    }
}
