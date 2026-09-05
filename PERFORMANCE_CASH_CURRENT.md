# Fase 3A: rendimiento de `GET /api/cash/current`

Fecha de implementación: 2026-09-02.

## Estado anterior

Después de localizar la caja abierta, `CashService.calculateCashSummary()` hacía
ocho agregaciones independientes:

| # | Propósito | Método anterior | Tabla y filtros |
|---|---|---|---|
| 1 | POS CASH | `PaymentRepository.sumCashPayments` | `payments`, branch y ventana de la caja |
| 2 | POS CARD | `PaymentRepository.sumCardPayments` | `payments`, branch y ventana de la caja |
| 3 | POS TRANSFER | `PaymentRepository.sumTransferPayments` | `payments`, branch y ventana de la caja |
| 4 | Evento CASH | `EventPaymentRepository.sumByCashRegisterAndPaymentMethod` | `event_payments`, caja y método |
| 5 | Evento CARD | mismo método | `event_payments`, caja y método |
| 6 | Evento TRANSFER | mismo método | `event_payments`, caja y método |
| 7 | Depósitos | `CashMovementRepository.sumByCashRegisterAndType` | `cash_movements`, caja, tipo y `voided=false` |
| 8 | Retiros | mismo método | `cash_movements`, caja, tipo y `voided=false` |

La ruta representaba nueve consultas de servicio: una para la caja y ocho para
los totales. Sumando las dos consultas observadas en la validación JWT, el
request completo tenía un mínimo aproximado de once consultas.

Las consultas anteriores no filtraban por estado de orden o evento. Un pago ya
persistido continúa contando aunque posteriormente se cancele su orden o evento.
No existen estado ni marca de anulación en las entidades `Payment` y
`EventPayment`; esta regla se conserva.

## Estado optimizado

La fuente única de la fórmula continúa siendo
`CashService.calculateCashSummary()`; solo cambió cómo se obtienen sus entradas.

1. `CashRegisterRepository.sumPaymentTotals` recorre `payments` y
   `event_payments` una vez cada una mediante `UNION ALL` y agregación
   condicional. Devuelve los seis totales POS/eventos en una proyección tipada.
2. `CashMovementRepository.sumMovementTotals` obtiene depósitos y retiros en
   una sola agregación condicional, excluyendo `voided=true`.
3. Las dos consultas reciben explícitamente `tenantId`, `branchId` y
   `cashRegisterId` donde corresponde.
4. La búsqueda de la caja actual también filtra explícitamente tenant, branch y
   estado `OPEN`.

El servicio queda en tres consultas: caja abierta, totales de pagos y totales de
movimientos. Con las dos consultas JWT observadas en la línea base, el request
completo baja estructuralmente de aproximadamente once a cinco consultas. No se
creó ningún N+1 y una prueba con estadísticas Hibernate verifica tres sentencias
preparadas dentro de `CashService.currentCash()`.

## Fórmulas preservadas

```text
cashSales     = posCashSales + eventCashPayments
cardSales     = posCardSales + eventCardPayments
transferSales = posTransferSales + eventTransferPayments
salesTotal    = cashSales + cardSales + transferSales
expectedCash  = openingAmount + cashSales + depositTotal - withdrawalTotal
expectedAmount (contrato existente) = openingAmount + salesTotal
```

No cambió ningún campo de `CashRegisterResponse` ni el contrato de
`GET /api/cash/current`.

## Índices a comprobar en staging

Hibernate está configurado con `ddl-auto=validate` y el proyecto no usa
Flyway/Liquibase. Por eso esta fase no modifica el esquema ni depende de
anotaciones `@Index` para crear índices.

Ejecutar primero:

```sql
SHOW INDEX FROM cash_registers;
SHOW INDEX FROM payments;
SHOW INDEX FROM event_payments;
SHOW INDEX FROM cash_movements;
```

Los índices declarados en entidades que más se aproximan a las nuevas consultas
son:

- `payments(tenant_id, branch_id, created_at)`;
- `event_payments(cash_register_id, payment_method)`;
- `cash_movements(cash_register_id, created_at)`;
- índices de `cash_registers` con prefijos tenant/branch o tenant/status.

Dos candidatos deben evaluarse con datos reales, no crearse por intuición:

- `cash_registers(tenant_id, branch_id, status)` para localizar la caja abierta;
- `cash_movements(cash_register_id, voided, type)` para la agregación de caja.

## EXPLAIN para staging

Usar identificadores y fechas de una caja de prueba, nunca datos sensibles:

```sql
SET @tenant_id = 1;
SET @branch_id = 1;
SET @cash_register_id = 1;
SET @opened_at = '2026-01-01 09:00:00';
SET @summary_end = '2026-01-01 18:00:00';

EXPLAIN
SELECT
    COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
        AND totals.payment_method = 'CASH' THEN totals.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
        AND totals.payment_method = 'CARD' THEN totals.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN totals.payment_source = 'POS'
        AND totals.payment_method = 'TRANSFER' THEN totals.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
        AND totals.payment_method = 'CASH' THEN totals.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
        AND totals.payment_method = 'CARD' THEN totals.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN totals.payment_source = 'EVENT'
        AND totals.payment_method = 'TRANSFER' THEN totals.amount ELSE 0 END), 0)
FROM (
    SELECT 'POS' AS payment_source, p.payment_method, p.amount
    FROM payments p
    WHERE p.tenant_id = @tenant_id
      AND p.branch_id = @branch_id
      AND p.created_at BETWEEN @opened_at AND @summary_end
    UNION ALL
    SELECT 'EVENT', ep.payment_method, ep.amount
    FROM event_payments ep
    WHERE ep.tenant_id = @tenant_id
      AND ep.branch_id = @branch_id
      AND ep.cash_register_id = @cash_register_id
) totals;

EXPLAIN
SELECT
    COALESCE(SUM(CASE WHEN cm.type = 'DEPOSIT' THEN cm.amount ELSE 0 END), 0),
    COALESCE(SUM(CASE WHEN cm.type = 'WITHDRAWAL' THEN cm.amount ELSE 0 END), 0)
FROM cash_movements cm
WHERE cm.tenant_id = @tenant_id
  AND cm.branch_id = @branch_id
  AND cm.cash_register_id = @cash_register_id
  AND cm.voided = false;

EXPLAIN
SELECT *
FROM cash_registers cr
WHERE cr.tenant_id = @tenant_id
  AND cr.branch_id = @branch_id
  AND cr.status = 'OPEN';
```

Revisar especialmente `key`, `rows`, `filtered` y `Extra`. Comparar los planes
antes de proponer una migración manual para los índices candidatos.

## Medición runtime pendiente

No se registran tiempos inventados. En staging, después de al menos 30 requests
warm del flujo POS, consultar:

```text
/actuator/metrics/http.server.requests?tag=uri:/api/cash/current&tag=method:GET
/actuator/metrics/spacekids.service.requests?tag=service:cash&tag=operation:current
/actuator/metrics/hikaricp.connections.pending
```

Comparar `count`, `max`, p50, p95 y p99 con la muestra anterior, y correlacionar
los requests con el slow query log de staging. No habilitar el general query log
ni SQL detallado en producción.
