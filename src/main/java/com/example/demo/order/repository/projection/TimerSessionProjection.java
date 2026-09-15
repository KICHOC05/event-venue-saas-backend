package com.example.demo.order.repository.projection;

import java.time.LocalDateTime;

import com.example.demo.common.enums.OrderItemStatus;

public interface TimerSessionProjection {

    String getItemPublicId();

    String getOrderPublicId();

    String getCustomerName();

    String getChildName();

    String getProductName();

    LocalDateTime getSessionStart();

    LocalDateTime getSessionEnd();

    Integer getDurationMinutes();

    OrderItemStatus getStatus();
}
