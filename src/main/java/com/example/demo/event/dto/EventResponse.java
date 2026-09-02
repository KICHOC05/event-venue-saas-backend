// com.example.demo.event.dto.EventResponse
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
public class EventResponse {

    private String publicId;
    private Long eventNumber;
    private String customerName;
    private String phone;
    private String childName;
    private Integer childAge;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer guestChildren;
    private Integer guestAdults;
    private String notes;
    private String packageProductPublicId;
    private String packageName;
    private BigDecimal eventPrice;
    private BigDecimal depositAmount;
    private BigDecimal remainingAmount;
    private EventStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
