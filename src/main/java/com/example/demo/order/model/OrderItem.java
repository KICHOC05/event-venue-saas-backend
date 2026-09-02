package com.example.demo.order.model;

import com.example.demo.common.enums.OrderItemStatus;
import com.example.demo.product.model.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(length = 255)
    private String warning;

    @Column(name = "child_name", length = 150)
private String childName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus status = OrderItemStatus.ACTIVE;

    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;

    // =========================
    // HOURLY (TIMERS)
    // =========================

   @Column(name = "session_start")
    private LocalDateTime sessionStart;

    @Column(name = "session_end")
    private LocalDateTime sessionEnd;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "active")
    private Boolean active;

    @Column(nullable = false)
    private Boolean isEvent = false;

    @Column(name = "reward_item", nullable = false)
    private Boolean rewardItem = false;
}
