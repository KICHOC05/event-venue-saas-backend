package com.example.demo.event.repository.projection;

import java.time.LocalDate;

import com.example.demo.common.enums.EventStatus;

public interface DashboardUpcomingEventProjection {

    LocalDate getEventDate();

    String getCustomerName();

    String getPackageName();

    Integer getGuestChildren();

    EventStatus getStatus();
}
