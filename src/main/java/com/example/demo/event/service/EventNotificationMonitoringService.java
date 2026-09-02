package com.example.demo.event.service;

import com.example.demo.common.enums.EventStatus;
import com.example.demo.event.dto.EventNotificationMessage;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventNotificationMonitoringService {

    private final EventBookingRepository eventBookingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantRepository tenantRepository;

    private static final List<EventStatus> UPCOMING_STATUSES = List.of(
            EventStatus.PENDING_DEPOSIT,
            EventStatus.CONFIRMED
    );

    private static final List<EventStatus> PAYMENT_STATUSES = List.of(
            EventStatus.PENDING_DEPOSIT,
            EventStatus.CONFIRMED,
            EventStatus.IN_PROGRESS
    );

    private final Set<String> notifiedKeys = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 300000)
    public void monitorEvents() {
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            LocalDate today = LocalDate.now();
            LocalDate fiveDaysFromNow = today.plusDays(5);
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = today.toString();

            for (Tenant tenant : tenants) {
                Long tenantId = tenant.getId();
                String tenantPrefix = tenantId + ":";

                checkFiveDaysAway(tenantId, tenantPrefix, today, fiveDaysFromNow, todayKey);
                checkOneDayAway(tenantId, tenantPrefix, today, tomorrow, todayKey);
                checkPaymentPending(tenantId, tenantPrefix, today, fiveDaysFromNow, todayKey);
            }
        } catch (Exception e) {
            log.error("Error monitoring event notifications", e);
        }
    }

    private void checkFiveDaysAway(Long tenantId, String tenantPrefix, LocalDate today, LocalDate targetDate, String todayKey) {
        List<EventBooking> events = eventBookingRepository.findByTenantAndDateAndStatuses(
                tenantId, targetDate, UPCOMING_STATUSES);

        for (EventBooking event : events) {
            String key = tenantPrefix + event.getPublicId() + ":EVENT_5_DAYS_AWAY:" + todayKey;
            if (notifiedKeys.add(key)) {
                String message = String.format(
                        "El evento de %s está a 5 días.",
                        event.getCustomerName()
                );

                publishNotification(tenantId, event, "EVENT_5_DAYS_AWAY", message);
                log.info("[EVENT] EVENT_5_DAYS_AWAY tenant={} event={} customer={}",
                        tenantId, event.getPublicId(), event.getCustomerName());
            }
        }
    }

    private void checkOneDayAway(Long tenantId, String tenantPrefix, LocalDate today, LocalDate targetDate, String todayKey) {
        List<EventBooking> events = eventBookingRepository.findByTenantAndDateAndStatuses(
                tenantId, targetDate, UPCOMING_STATUSES);

        for (EventBooking event : events) {
            String key = tenantPrefix + event.getPublicId() + ":EVENT_1_DAY_AWAY:" + todayKey;
            if (notifiedKeys.add(key)) {
                String message = String.format(
                        "El evento de %s es mañana.",
                        event.getCustomerName()
                );

                publishNotification(tenantId, event, "EVENT_1_DAY_AWAY", message);
                log.info("[EVENT] EVENT_1_DAY_AWAY tenant={} event={} customer={}",
                        tenantId, event.getPublicId(), event.getCustomerName());
            }
        }
    }

    private void checkPaymentPending(Long tenantId, String tenantPrefix, LocalDate today, LocalDate fiveDaysFromNow, String todayKey) {
        List<EventBooking> events = eventBookingRepository.findUpcomingWithPendingBalance(
                tenantId, today, fiveDaysFromNow, PAYMENT_STATUSES);

        for (EventBooking event : events) {
            String key = tenantPrefix + event.getPublicId() + ":EVENT_PAYMENT_PENDING:" + todayKey;
            if (notifiedKeys.add(key)) {
                String message = String.format(
                        "El evento de %s aún tiene saldo pendiente de $%.0f.",
                        event.getCustomerName(),
                        event.getRemainingAmount()
                );

                publishNotification(tenantId, event, "EVENT_PAYMENT_PENDING", message);
                log.info("[EVENT] EVENT_PAYMENT_PENDING tenant={} event={} customer={} remaining={}",
                        tenantId, event.getPublicId(), event.getCustomerName(), event.getRemainingAmount());
            }
        }
    }

    private void publishNotification(Long tenantId, EventBooking event, String type, String message) {
        EventNotificationMessage notification = EventNotificationMessage.builder()
                .type(type)
                .eventPublicId(event.getPublicId())
                .customerName(event.getCustomerName())
                .childName(event.getChildName())
                .eventDate(event.getEventDate())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .eventPrice(event.getEventPrice())
                .depositAmount(event.getDepositAmount())
                .remainingAmount(event.getRemainingAmount())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/tenant/" + tenantId + "/events", notification);

        log.info("[WEBSOCKET] Event notification type={} tenant={} event={}",
                type, tenantId, event.getPublicId());
    }
}
