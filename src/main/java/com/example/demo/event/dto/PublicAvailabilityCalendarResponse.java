package com.example.demo.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PublicAvailabilityCalendarResponse {

    private LocalDate from;
    private LocalDate to;
    private List<LocalDate> occupiedDates;
}
