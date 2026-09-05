package com.example.demo.order.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateRequest {

    private String customerName;

    private String clientPublicId;

    @Valid
    private OrderItemRequest initialItem;

}
