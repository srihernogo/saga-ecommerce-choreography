package com.company.saga.shared.event;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PaymentRefundedEvent extends BaseEvent {

    private String refundId;
    private BigDecimal amount;

    public PaymentRefundedEvent(String orderId, String refundId, BigDecimal amount) {
        super(orderId);
        this.refundId = refundId;
        this.amount = amount;
    }
}
