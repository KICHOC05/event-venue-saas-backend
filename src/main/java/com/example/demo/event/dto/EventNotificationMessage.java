package com.example.demo.event.dto;

import com.example.demo.common.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventNotificationMessage {

    private String type;
    private String eventPublicId;
    private String customerName;
    private String childName;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private EventStatus status;
    private BigDecimal eventPrice;
    private BigDecimal depositAmount;
    private BigDecimal remainingAmount;
    private String message;
    private LocalDateTime createdAt;
}
