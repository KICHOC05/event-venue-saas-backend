package com.example.demo.cash.repository.projection;

import java.math.BigDecimal;

public interface CashPaymentTotalsProjection {

    BigDecimal getPosCashSales();

    BigDecimal getPosCardSales();

    BigDecimal getPosTransferSales();

    BigDecimal getEventCashPayments();

    BigDecimal getEventCardPayments();

    BigDecimal getEventTransferPayments();
}
