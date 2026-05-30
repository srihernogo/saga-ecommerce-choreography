package com.company.saga.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MidtransGateway {

    public PaymentResult charge(String orderId, BigDecimal amount, String paymentMethod) {
        log.info("Calling Midtrans to charge order {} for amount {} via {}", orderId, amount, paymentMethod);
        
        try {
            // Simulate network delay
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate logic
        if (amount.compareTo(BigDecimal.valueOf(100000000)) > 0) {
            return new PaymentResult(false, null, "Amount exceeds limit");
        }
        
        return new PaymentResult(true, "MIDTRANS-" + UUID.randomUUID().toString(), null);
    }

    public record PaymentResult(boolean success, String referenceId, String errorMessage) {}
}
