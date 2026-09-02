// com.example.demo.event.dto.EventCalendarResponse
package com.example.demo.event.dto;

import com.example.demo.common.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCalendarResponse {
    private String publicId;
    private Long eventNumber;
    private String customerName;
    private String childName;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private EventStatus status;
}
