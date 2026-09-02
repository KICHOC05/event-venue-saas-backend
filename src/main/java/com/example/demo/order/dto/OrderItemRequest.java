package com.example.demo.order.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemRequest {

    private String productPublicId;

    private Integer quantity;

    private String childName;

    private LocalDate eventDate;

    private LocalTime startTime;

    private LocalTime endTime;

}