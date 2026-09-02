// com.example.demo.event.dto.UpdateEventRequest
package com.example.demo.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEventRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String customerName;

    private String phone;

    @NotBlank(message = "El nombre del niño es obligatorio")
    private String childName;

    @NotNull(message = "La edad del niño es obligatoria")
    @PositiveOrZero(message = "La edad debe ser mayor o igual a 0")
    private Integer childAge;

    @NotNull(message = "La fecha del evento es obligatoria")
    private LocalDate eventDate;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime startTime;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime endTime;

    @PositiveOrZero(message = "Los niños invitados deben ser mayores o igual a 0")
    private Integer guestChildren;

    @PositiveOrZero(message = "Los adultos invitados deben ser mayores o igual a 0")
    private Integer guestAdults;

    private String notes;

    @NotBlank(message = "El paquete es obligatorio")
    private String packageProductPublicId;

    @NotNull(message = "El monto del anticipo es obligatorio")
    @PositiveOrZero(message = "El anticipo debe ser mayor o igual a 0")
    private BigDecimal depositAmount;
}