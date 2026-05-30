package com.company.saga.shared.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PaymentFailedEvent extends BaseEvent {

    private String reason;
    private String errorCode;

    public PaymentFailedEvent(String orderId, String reason, String errorCode) {
        super(orderId);
        this.reason = reason;
        this.errorCode = errorCode;
    }
}
