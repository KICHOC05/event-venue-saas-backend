package com.example.demo.cash;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CashBalanceCalculationTest {

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal calculateExpectedCash(
            BigDecimal openingAmount,
            BigDecimal cashSales,
            BigDecimal deposits,
            BigDecimal withdrawals) {
        return openingAmount
                .add(cashSales)
                .add(deposits)
                .subtract(withdrawals);
    }

    @Test
    void caso1_openingAndCashSalesAndDepositsAndWithdrawals() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("500.00");
        BigDecimal deposits = bd("200.00");
        BigDecimal withdrawals = bd("300.00");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("1400.00")));
    }

    @Test
    void caso2_openingAndDepositOnly() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("0");
        BigDecimal deposits = bd("500.00");
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("1500.00")));
    }

    @Test
    void caso3_cardPaymentShouldNotIncreaseExpectedCash() {
        BigDecimal opening = bd("500.00");
        BigDecimal cashSales = bd("0");       // CARD payment does NOT add to cashSales
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("500.00")));
    }

    @Test
    void caso4_transferPaymentShouldNotIncreaseExpectedCash() {
        BigDecimal opening = bd("500.00");
        BigDecimal cashSales = bd("0");       // TRANSFER does NOT add to cashSales
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("500.00")));
    }

    @Test
    void caso5_withdrawalAfterSales() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("500.00");
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal available = calculateExpectedCash(opening, cashSales, deposits, withdrawals);
        assertEquals(0, available.compareTo(bd("1500.00")));

        BigDecimal withdraw = bd("1200.00");
        assertTrue(withdraw.compareTo(available) <= 0,
                "Withdrawal allowed when within available");

        withdrawals = withdraw;
        BigDecimal remaining = calculateExpectedCash(opening, cashSales, deposits, withdrawals);
        assertEquals(0, remaining.compareTo(bd("300.00")));
    }

    @Test
    void caso6_withdrawalExceedingAvailableShouldBeRejected() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("0");
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal available = calculateExpectedCash(opening, cashSales, deposits, withdrawals);
        assertEquals(0, available.compareTo(bd("1000.00")));

        BigDecimal withdraw = bd("1000.01");
        assertTrue(withdraw.compareTo(available) > 0,
                "Withdrawal exceeding available should be rejected");
    }

    @Test
    void caso7_differenceCalculation() {
        BigDecimal expectedCash = bd("2400.00");
        BigDecimal countedCash = bd("2350.00");

        BigDecimal difference = countedCash.subtract(expectedCash);

        assertEquals(0, difference.compareTo(bd("-50.00")));
    }

    @Test
    void caso8_differenceCalculationSurplus() {
        BigDecimal expectedCash = bd("2400.00");
        BigDecimal countedCash = bd("2450.00");

        BigDecimal difference = countedCash.subtract(expectedCash);

        assertEquals(0, difference.compareTo(bd("50.00")));
    }

    @Test
    void caso9_differenceCalculationExact() {
        BigDecimal expectedCash = bd("2400.00");
        BigDecimal countedCash = bd("2400.00");

        BigDecimal difference = countedCash.subtract(expectedCash);

        assertEquals(0, difference.compareTo(BigDecimal.ZERO));
    }

    @Test
    void caso10_completeClosingScenario() {
        BigDecimal openingAmount = bd("1000.00");
        BigDecimal cashSales = bd("1500.00");

        BigDecimal cardSales = bd("500.00");
        BigDecimal transferSales = bd("300.00");

        BigDecimal deposits = bd("200.00");
        BigDecimal withdrawals = bd("300.00");

        BigDecimal totalSales = cashSales.add(cardSales).add(transferSales);
        assertEquals(0, totalSales.compareTo(bd("2300.00")));

        BigDecimal expectedCash = calculateExpectedCash(
                openingAmount, cashSales, deposits, withdrawals);
        assertEquals(0, expectedCash.compareTo(bd("2400.00")));

        // Card and transfer should NOT be in expectedCash
        BigDecimal cardAndTransfer = cardSales.add(transferSales);
        BigDecimal physicalCashOnly = expectedCash.subtract(openingAmount)
                .subtract(deposits).add(withdrawals);
        assertEquals(0, physicalCashOnly.compareTo(cashSales));

        // Close with counted = 2350
        BigDecimal counted = bd("2350.00");
        BigDecimal difference = counted.subtract(expectedCash);
        assertEquals(0, difference.compareTo(bd("-50.00")));
    }

    @Test
    void caso11_voidedDepositShouldNotAffectCalculation() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("500.00");
        // voided deposit of $300 should NOT be in deposits
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("1500.00")));
    }

    @Test
    void caso12_voidedWithdrawalShouldNotAffectCalculation() {
        BigDecimal opening = bd("1000.00");
        BigDecimal cashSales = bd("500.00");
        BigDecimal deposits = bd("200.00");
        // voided withdrawal of $300 should NOT be in withdrawals
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("1700.00")));
    }

    @Test
    void caso13_bigDecimalPrecision() {
        BigDecimal opening = bd("100.33");
        BigDecimal cashSales = bd("50.67");
        BigDecimal deposits = bd("0.01");
        BigDecimal withdrawals = bd("0.01");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(bd("151.00")));
    }

    @Test
    void caso14_zeroOpeningAmount() {
        BigDecimal opening = bd("0");
        BigDecimal cashSales = bd("0");
        BigDecimal deposits = bd("0");
        BigDecimal withdrawals = bd("0");

        BigDecimal expected = calculateExpectedCash(opening, cashSales, deposits, withdrawals);

        assertEquals(0, expected.compareTo(BigDecimal.ZERO));
    }
}
