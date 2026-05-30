package com.company.saga.order.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    @NotBlank
    private String productId;
    @NotBlank
    private String productName;
    @Min(1)
    private int quantity;
    @NotNull
    @Min(0)
    private BigDecimal unitPrice;
}
