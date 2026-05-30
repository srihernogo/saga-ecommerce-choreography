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
public class PaymentCompletedEvent extends BaseEvent {

    private String paymentId;
    private BigDecimal amount;
    private String paymentMethod;
    private String externalReference;

    public PaymentCompletedEvent(String orderId, String paymentId, BigDecimal amount,
                                 String paymentMethod, String externalReference) {
        super(orderId);
        this.paymentId = paymentId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.externalReference = externalReference;
    }
}
