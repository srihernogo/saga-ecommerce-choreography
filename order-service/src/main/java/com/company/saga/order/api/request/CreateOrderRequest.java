package com.company.saga.order.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank
    private String customerId;
    @NotBlank
    private String shippingAddress;
    @NotBlank
    private String paymentMethod;
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
