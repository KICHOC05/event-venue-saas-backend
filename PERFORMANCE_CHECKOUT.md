# Checkout transaccional del POS

## Contrato

`POST /api/orders/{orderPublicId}/checkout`

```json
{
  "requestId": "UUID-generado-por-el-cliente",
  "payment": {
    "amount": 500.00,
    "paymentMethod": "CASH",
    "reference": null
  }
}
```

La respuesta contiene `order`, `payment` y `closed`. Los endpoints existentes
`POST /payments` y `POST /close` se conservan.

## Garantías

- Pago y cierre comparten una sola transacción.
- Una orden se bloquea durante checkout para serializar cobros concurrentes.
- `requestId` es único por tenant y orden. Repetir la misma solicitud devuelve
  el resultado existente y no registra otro pago.
- Reutilizar el mismo `requestId` con datos de pago distintos se rechaza.
- Caja sigue calculándose desde `payments`; checkout no crea movimientos duplicados.
- Un fallo de lealtad durante cierre revierte pago y cierre.

## Migración MariaDB obligatoria

El proyecto usa `spring.jpa.hibernate.ddl-auto=validate` y no tiene Flyway ni
Liquibase. Por ello, antes de iniciar el nuevo backend en staging o producción,
ejecutar con un usuario de migración:

```text
ops/mariadb/checkout-idempotency.sql
```

Después verificar:

```sql
SHOW COLUMNS FROM payments LIKE 'checkout_request_id';
SHOW INDEX FROM payments WHERE Key_name = 'uk_payments_tenant_order_checkout_request';
```

No conceder permisos `ALTER` al usuario habitual de la aplicación.

## Observabilidad

La operación publica la métrica existente `spacekids.service.requests` con tags
acotados `service=order` y `operation=checkout`. La ruta HTTP se observa como
plantilla en `http.server.requests`; ni `requestId` ni el UUID de la orden se usan
como tags.

## Verificación en staging

1. Abrir caja y crear una orden de prueba.
2. Probar pago parcial, exacto y efectivo con cambio.
3. Repetir exactamente el mismo `requestId` y comprobar un solo registro en
   `payments`.
4. Ejecutar dos checkouts simultáneos sobre la misma orden.
5. Comparar p50/p95/p99 y máximo de `/api/orders/{orderPublicId}/checkout` en
   Actuator/Prometheus contra el flujo anterior de dos requests.
