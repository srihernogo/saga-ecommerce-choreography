package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class ShippingCreatedEvent extends BaseEvent {

    private String trackingNumber;
    private String courier;

    public ShippingCreatedEvent(String orderId, String trackingNumber, String courier) {
        super(orderId);
        this.trackingNumber = trackingNumber;
        this.courier = courier;
    }
}
