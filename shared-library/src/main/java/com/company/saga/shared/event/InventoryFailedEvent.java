package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class InventoryFailedEvent extends BaseEvent {

    private String reason;
    private String failedProductId;

    public InventoryFailedEvent(String orderId, String reason, String failedProductId) {
        super(orderId);
        this.reason = reason;
        this.failedProductId = failedProductId;
    }
}
