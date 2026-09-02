package com.example.demo.order.service;

import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderSessionScheduler {

    private final OrderItemRepository orderItemRepository;
    private final TenantRepository tenantRepository;

    @Scheduled(fixedRate = 30000)
    public void closeFinishedSessions() {

        LocalDateTime now = LocalDateTime.now();

        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {

            List<OrderItem> items = orderItemRepository
                    .findByActiveTrueAndSessionEndBeforeAndOrder_Tenant_Id(
                            now,
                            tenant.getId()
                    );

            for (OrderItem item : items) {

                item.setActive(false);
                item.setStatus(OrderItemStatus.FINISHED);

                orderItemRepository.save(item);

                System.out.println("⏱️ [" + tenant.getId() + "] sesión finalizada: " + item.getPublicId());
            }
        }
    }
}