package com.example.demo.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderItemsBulkRequest {

    private List<OrderItemRequest> items;

}