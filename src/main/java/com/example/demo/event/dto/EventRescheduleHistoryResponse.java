package com.example.demo.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRescheduleHistoryResponse {

    private String publicId;
    private LocalDate oldEventDate;
    private LocalTime oldStartTime;
    private LocalTime oldEndTime;
    private LocalDate newEventDate;
    private LocalTime newStartTime;
    private LocalTime newEndTime;
    private String reason;
    private String changedByUserPublicId;
    private String changedByUserEmail;
    private LocalDateTime changedAt;
}
