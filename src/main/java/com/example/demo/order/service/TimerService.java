package com.example.demo.order.service;

import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.order.dto.ActiveSessionResponse;
import com.example.demo.order.dto.TimerDashboardResponse;
import com.example.demo.order.dto.TimerHistoryResponse;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.security.TenantContext;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimerService {

    private final OrderItemRepository orderItemRepository;

    // =========================
    // ACTIVE SESSIONS
    // =========================

   public List<ActiveSessionResponse> getActiveSessions() {

    Long tenantId = TenantContext.getTenantId();

    LocalDateTime now = LocalDateTime.now();

    List<OrderItem> items =
            orderItemRepository
                    .findActiveTimers(tenantId);

    return items.stream()
            .map(item -> mapToResponse(item, now))
            .toList();
}

        // =========================
    // SESSION HISTORY
    // =========================

    public Page<TimerHistoryResponse> getSessionHistory(
            String search,
            String status,
            LocalDate date,
            Integer page,
            Integer size) {

        Long tenantId = TenantContext.getTenantId();

        // Validar y establecer valores por defecto
        int pageNum = (page != null && page >= 0) ? page : 0;
        int pageSize = (size != null && size > 0) ? size : 20;
        
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        // Convertir status a enum si está presente
        OrderItemStatus itemStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                itemStatus = OrderItemStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Si el status no es válido, ignorar el filtro
                itemStatus = null;
            }
        }

        // Convertir LocalDate a rango LocalDateTime
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        
        if (date != null) {
            startDate = date.atStartOfDay();
            endDate = date.plusDays(1).atStartOfDay();
        }

        // Buscar con los filtros aplicados
        Page<OrderItem> items = orderItemRepository.findTimerHistory(
                tenantId,
                itemStatus,
                search,
                startDate,
                endDate,
                pageable
        );

        // Mapear a TimerHistoryResponse
        return items.map(this::mapToHistoryResponse);
    }

    public TimerDashboardResponse getTimersDashboard() {

    Long tenantId = TenantContext.getTenantId();

    LocalDateTime now = LocalDateTime.now();

    List<OrderItem> activeItems =
            orderItemRepository.findActiveTimers(tenantId);

    long expiringSoon =
            activeItems.stream()
                    .filter(item ->
                            item.getSessionEnd() != null
                                    && Duration.between(
                                            now,
                                            item.getSessionEnd())
                                    .getSeconds() <= 300)
                    .count();

    TimerDashboardResponse response =
            new TimerDashboardResponse();

    response.setActiveSessions(
            (long) activeItems.size());

    response.setExpiringSoon(
            expiringSoon);

    response.setFinishedToday(0L);

    response.setExpired(0L);

    response.setTotalTodayMinutes(0L);

    return response;
}
    // =========================
    // PRIVATE HELPER METHODS
    // =========================

    private ActiveSessionResponse mapToResponse(
            OrderItem item,
            LocalDateTime now) {

        ActiveSessionResponse response =
                new ActiveSessionResponse();

        response.setItemPublicId(
                item.getPublicId());
        response.setOrderPublicId(
                item.getOrder().getPublicId());
        response.setCustomerName(
                item.getOrder().getCustomerName());

        String childName =
                item.getChildName();

        response.setChildName(
                childName != null
                        ? childName
                        : "Sin nombre");

        response.setProductName(
                item.getProduct().getName());

        response.setSessionStart(
                item.getSessionStart());

        response.setSessionEnd(
                item.getSessionEnd());

        response.setDurationMinutes(
                item.getDurationMinutes());

        response.setStatus(
                item.getStatus().name());

        if (item.getSessionEnd() != null) {

            Duration remaining =
                    Duration.between(
                            now,
                            item.getSessionEnd());

            long seconds =
                    Math.max(
                            remaining.getSeconds(),
                            0);

            response.setRemainingSeconds(
                    seconds);

            response.setRemainingMinutes(
                    seconds / 60);

            response.setExpiringSoon(
                    seconds <= 300);

            response.setExpired(
                    seconds <= 0);

            int duration =
                    item.getDurationMinutes() != null
                            ? item.getDurationMinutes()
                            : 60;

            long elapsed =
                    duration * 60L - seconds;

            int progress =
                    (int) Math.min(
                            100,
                            Math.max(
                                    0,
                                    (elapsed * 100)
                                            / (duration * 60L)
                            )
                    );

            response.setProgressPercent(
                    progress);

        } else {

            response.setRemainingSeconds(0L);
            response.setRemainingMinutes(0L);
            response.setExpiringSoon(false);
            response.setExpired(false);
            response.setProgressPercent(0);

        }

        return response;
    }

    private TimerHistoryResponse mapToHistoryResponse(OrderItem item) {
        TimerHistoryResponse response = new TimerHistoryResponse();

        response.setItemPublicId(item.getPublicId());
        response.setOrderPublicId(item.getOrder().getPublicId());
        response.setCustomerName(item.getOrder().getCustomerName());
        
        String childName = item.getChildName();
        response.setChildName(childName != null ? childName : "Sin nombre");
        
        response.setProductName(item.getProduct().getName());
        response.setSessionStart(item.getSessionStart());
        response.setSessionEnd(item.getSessionEnd());
        response.setDurationMinutes(item.getDurationMinutes());
        
        // Obtener el estado como string para el frontend
        response.setStatus(item.getStatus().name());

        return response;
    }
}