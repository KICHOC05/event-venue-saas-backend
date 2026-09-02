package com.example.demo.order.service;

import com.example.demo.order.dto.TimerNotificationEvent;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimerMonitoringService {

    private final OrderItemRepository orderItemRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TenantRepository tenantRepository;

    private final Set<String> notifiedFiveMin = ConcurrentHashMap.newKeySet();
    private final Set<String> notifiedOneMin = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> notifiedFinished = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 10000)
    public void monitorTimers() {
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            LocalDateTime now = LocalDateTime.now();

            for (Tenant tenant : tenants) {
                Long tenantId = tenant.getId();
                String tenantPrefix = tenantId + ":";

                List<OrderItem> timers = orderItemRepository.findActiveTimers(tenantId);

                for (OrderItem timer : timers) {
                    String publicId = timer.getPublicId();
                    String childName = timer.getChildName() != null ? timer.getChildName() : "Sin nombre";

                    if (timer.getSessionEnd() == null) {
                        continue;
                    }

                    long remainingSeconds = Duration.between(now, timer.getSessionEnd()).getSeconds();

                    String fiveMinKey = tenantPrefix + publicId + "-5";
                    String oneMinKey = tenantPrefix + publicId + "-1";
                    String finishedKey = tenantPrefix + publicId + "-F";

                    if (remainingSeconds <= 300 && remainingSeconds > 60) {
                        if (!notifiedFiveMin.contains(fiveMinKey)) {
                            notifiedFiveMin.add(fiveMinKey);
                            log.warn("[TIMER] FIVE_MIN tenant={} child={} timer={} remainingSeconds={}", tenantId, childName, publicId, remainingSeconds);

                            publishEvent(tenantId,
                                    "FIVE_MIN",
                                    publicId,
                                    childName,
                                    childName + " termina en menos de 5 minutos",
                                    remainingSeconds);

                            notifiedFinished.remove(finishedKey);
                        }
                    } else if (remainingSeconds <= 60 && remainingSeconds > 0) {
                        if (!notifiedOneMin.contains(oneMinKey)) {
                            notifiedOneMin.add(oneMinKey);
                            log.warn("[TIMER] ONE_MIN tenant={} child={} timer={} remainingSeconds={}", tenantId, childName, publicId, remainingSeconds);

                            publishEvent(tenantId,
                                    "ONE_MIN",
                                    publicId,
                                    childName,
                                    childName + " termina en menos de 1 minuto",
                                    remainingSeconds);

                            notifiedFinished.remove(finishedKey);
                        }
                    } else if (remainingSeconds <= 0) {
                        if (!notifiedFinished.containsKey(finishedKey)) {
                            notifiedFinished.put(finishedKey, LocalDateTime.now());
                            log.info("[TIMER] FINISHED tenant={} child={} timer={} remainingSeconds={}", tenantId, childName, publicId, remainingSeconds);

                            publishEvent(tenantId,
                                    "FINISHED",
                                    publicId,
                                    childName,
                                    childName + " ha finalizado",
                                    remainingSeconds);

                            notifiedFiveMin.remove(fiveMinKey);
                            notifiedOneMin.remove(oneMinKey);
                        }
                    }
                }
            }

            cleanupNotificationKeys(tenants);

        } catch (Exception e) {
            log.error("Error monitoring timers", e);
        }
    }

    private void cleanupNotificationKeys(List<Tenant> tenants) {
        Set<String> currentTimerKeys = ConcurrentHashMap.newKeySet();
        for (Tenant tenant : tenants) {
            Long tenantId = tenant.getId();
            String tenantPrefix = tenantId + ":";
            List<OrderItem> timers = orderItemRepository.findActiveTimers(tenantId);
            for (OrderItem timer : timers) {
                currentTimerKeys.add(tenantPrefix + timer.getPublicId() + "-5");
                currentTimerKeys.add(tenantPrefix + timer.getPublicId() + "-1");
            }
        }

        notifiedFiveMin.removeIf(key -> !currentTimerKeys.contains(key));
        notifiedOneMin.removeIf(key -> !currentTimerKeys.contains(key));

        int oldEntriesCount = notifiedFinished.size();
        notifiedFinished.entrySet().removeIf(
                entry -> entry.getValue().isBefore(LocalDateTime.now().minusDays(1)));
        int removedCount = oldEntriesCount - notifiedFinished.size();
        if (removedCount > 0) {
            log.debug("[TIMER] Cleaned old finished notifications - removed {} entries", removedCount);
        }
    }

    private void publishEvent(
            Long tenantId,
            String type,
            String timerId,
            String childName,
            String message,
            long remainingSeconds
    ) {
        TimerNotificationEvent event = TimerNotificationEvent.builder()
                .type(type)
                .timerId(timerId)
                .childName(childName)
                .message(message)
                .remainingSeconds(remainingSeconds)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/tenant/" + tenantId + "/timers", event);

        log.info("[WEBSOCKET] Event={} tenant={} child={} timer={}", type, tenantId, childName, timerId);
    }
}