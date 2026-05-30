package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class InventoryReservedEvent extends BaseEvent {

    private String reservationId;

    public InventoryReservedEvent(String orderId, String reservationId) {
        super(orderId);
        this.reservationId = reservationId;
    }
}
