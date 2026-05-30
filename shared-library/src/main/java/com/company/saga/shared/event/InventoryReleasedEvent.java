package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class InventoryReleasedEvent extends BaseEvent {

    private String reason;

    public InventoryReleasedEvent(String orderId, String reason) {
        super(orderId);
        this.reason = reason;
    }
}
