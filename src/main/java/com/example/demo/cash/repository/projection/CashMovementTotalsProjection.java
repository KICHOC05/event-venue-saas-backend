package com.example.demo.cash.repository.projection;

import java.math.BigDecimal;

public interface CashMovementTotalsProjection {

    BigDecimal getDepositTotal();

    BigDecimal getWithdrawalTotal();
}
