package com.example.demo.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO para eventos de notificación de timers vía WebSocket.
 * 
 * Este DTO será utilizado en el Sprint 2.3B.2 por TimerMonitoringService
 * para emitir eventos de timers (FIVE_MIN, ONE_MIN, FINISHED) al canal /topic/timers.
 * 
 * Tipos de evento:
 * - FIVE_MIN: Timer próximo a finalizar (5 minutos)
 * - ONE_MIN: Último minuto del timer
 * - FINISHED: Timer finalizado
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimerNotificationEvent {
    
    /**
     * Tipo de evento: FIVE_MIN, ONE_MIN, FINISHED
     */
    private String type;
    
    /**
     * Identificador único del timer (OrderItem.publicId)
     */
    private String timerId;
    
    /**
     * Nombre del niño asociado al timer
     */
    private String childName;
    
    /**
     * Mensaje descriptivo del evento
     */
    private String message;
    
    /**
     * Segundos restantes (puede ser negativo para eventos FINISHED)
     */
    private Long remainingSeconds;
    
    /**
     * Timestamp del momento en que se generó el evento
     */
    private LocalDateTime timestamp;
}