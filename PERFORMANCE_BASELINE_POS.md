# PERFORMANCE BASELINE POS

Fecha de auditoría: 2026-09-01.

La medición percibida reportada antes de esta fase fue: 14 s para abrir el POS,
20 s para agregar el primer producto y 12 s para cobrar. El backend no estaba
escuchando localmente en el puerto 8080 durante la auditoría, por lo que esos
valores no se atribuyen artificialmente a una etapa concreta. Las cantidades de
queries siguientes son mínimos/aproximaciones obtenidas de llamadas a
repositorios y accesos LAZY; deben confirmarse contra MariaDB staging.

## A. Requests de carga inicial

`frontend/app/routes/dashboard/pos.tsx`, `loadData()`:

1. `GET /api/products` mediante `fetchProducts()`.
2. `GET /api/cash/settings` mediante `getCashSettings()`.
3. `GET /api/cash/current` mediante `getCurrentCash()`.

Los tres requests se esperan secuencialmente en tres bloques `try` independientes.
El `useEffect([])` solo llama `loadData()` al montar el componente; los rerenders
del POS no vuelven a ejecutarlo. Un desmontaje/montaje de la ruta sí repite toda
la carga. No existe un `GET /api/orders` durante la entrada al POS y
`OrderController` tampoco declara un listado general en esa ruta.

Cuando se monta por primera vez `DashLayout`, `TimerNotificationWatcher` y
`EventNotificationWatcher` también abren dos conexiones STOMP/WebSocket hacia
`/ws`. No son requests REST del POS. `NotificationContext` recrea
`addNotification` en cada render, por lo que ambos efectos pueden reconectar
cuando cambia el provider; debe verificarse como churn WebSocket separado.

Queries aproximadas en estado normal:

| Request | JWT | Servicio/repositorio | Total mínimo aproximado |
| --- | ---: | ---: | ---: |
| `GET /api/products` | 2 | 1 listado de productos | 3 |
| `GET /api/cash/settings` | 2 | 1 lectura | 3 |
| `GET /api/cash/current` | 2 | 1 caja + 8 agregaciones | 11 |
| Carga completa | 6 | 11 | 17 |

Si aún no existe `CashSettings`, su creación añade lecturas de tenant y branch
más un insert, elevando la carga inicial aproximadamente a 20 queries.

## B. Requests al crear una venta

La orden no se crea al abrir el POS. Se crea de forma perezosa al seleccionar el
primer producto o confirmar el primer servicio:

1. `POST /api/orders`.
2. Tras recibir la orden, `POST /api/orders/{orderPublicId}/items`.

`POST /api/orders` realiza aproximadamente 8 queries/operaciones SQL sin cliente
y 9 con `clientPublicId`: 2 de JWT, tenant, branch, user, insert de orden, listado
vacío de items y listado vacío de pagos. Algunas lecturas repetidas pueden salir
del persistence context y no llegar físicamente a MariaDB.

## C. Requests por addItem

- Producto nuevo en la orden: `POST /api/orders/{orderPublicId}/items`.
- Producto ya presente: `PUT /api/orders/{orderPublicId}/items/{itemPublicId}`.
- Eliminar cantidad completa: `POST /api/orders/{orderPublicId}/items/{itemPublicId}/void`.
- Servicio con recompensa: `POST /api/loyalty/redeem/{orderPublicId}` en lugar
  del addItem normal.

`OrderService.addItem()` vuelve a buscar orden y producto, lee configuración de
inventario, guarda item/stock, recalcula leyendo tax e items, guarda la orden y
después llama `getOrder()`. `mapToResponse()` vuelve a leer todos los items y
pagos. La operación representa aproximadamente 12-14 queries más posibles
selects LAZY de producto (`N`) y las 2 queries JWT.

`OrderItemRepository.findAllByOrder_Id()` no trae `product` con `JOIN FETCH`,
por lo que el mapper puede generar N+1 según qué productos ya estén en el
persistence context. Además, `recalculateOrder()` y `mapToResponse()` leen la
misma colección por separado.

## D. Requests durante checkout

Para pago total el frontend ejecuta secuencialmente:

1. `POST /api/orders/{orderPublicId}/payments`.
2. Si `remainingAmount <= 0`, `POST /api/orders/{orderPublicId}/close`.

El registro del pago representa aproximadamente 7-9 queries SQL incluyendo JWT,
suma de pagos, referencias tenant/branch/user, insert y actualización de orden.
El cierre representa aproximadamente 10-12 queries más posibles selects LAZY.
`LoyaltyService.registerVisits()` consulta programa e items incluso cuando
después decide que la orden no tiene cliente frecuente. Con cliente frecuente
puede añadir una comprobación `exists` e insert por item calificable, además de
leer/actualizar el progreso.

Los pagos parciales no llaman `close`. Imprimir ticket añade
`GET /api/orders/{orderPublicId}/ticket`. Iniciar una venta nueva después del
cobro vuelve a solicitar `GET /api/products` mediante `refreshProducts()`.

## E. Queries aproximadas por operación

| Operación percibida | Requests HTTP | Queries/operaciones mínimas aproximadas |
| --- | ---: | ---: |
| Abrir POS, caja/config existentes | 3 secuenciales | 17 |
| Crear orden vacía | 1 | 8-9 |
| Primer producto (crear + agregar) | 2 secuenciales | 20-23 + N |
| Artículo posterior nuevo | 1 | 14-16 + N |
| Cambiar cantidad | 1 | 13-15 + N |
| Pago parcial | 1 | 7-9 |
| Pago total + cierre sin lealtad | 2 secuenciales | 17-21 + N |

`N` representa productos cargados de forma LAZY al construir `OrderResponse`.
Los números deben validarse con slow query log o estadísticas del driver porque
el primer nivel de caché de Hibernate puede evitar algunas lecturas repetidas.

## F. Métricas Actuator disponibles

- `http.server.requests`: count, total, max, p50, p95, p99 e histograma para
  todos los endpoints indicados. Spring MVC aplica el tag `uri` con la plantilla
  resuelta, por ejemplo `/api/orders/{orderPublicId}/items`; no usa UUID reales.
- `spacekids.service.requests`: p50/p95/p99 de operaciones POS mediante tags
  fijos `service` y `operation`.
- `spacekids.security.jwt.authentication`: p50/p95/p99 del tramo JWT anterior
  al controller, con outcomes de conjunto cerrado.
- `spring.data.repository.invocations`: instrumentación automática de Spring
  Data por repositorio/método/estado. No hay `@Timed` en repositories.
- `X-Correlation-ID`: se acepta o genera, se devuelve en response, entra al MDC
  y nunca se usa como tag.

Actuator solo expone `health`, `info`, `metrics` y `prometheus`. `health` es
público sin detalles; `info`, `metrics` y `prometheus` requieren rol `ADMIN`; el
resto de `/actuator/**` está denegado.

## G. Estado HikariCP

La vinculación automática está verificada por tests para:

- `hikaricp.connections.active`
- `hikaricp.connections.idle`
- `hikaricp.connections.pending`
- `hikaricp.connections.timeout`

El pool está configurado por defecto con máximo 5, mínimo idle 1 y timeout de
conexión 30 s. Sin un backend conectado al MariaDB real no existe una muestra
válida de active/idle/pending para este reporte. `pending > 0` sostenido o el
incremento de `timeout` identificarán saturación del pool.

## H. Cuellos de botella encontrados

1. Tres requests iniciales independientes ejecutados secuencialmente.
2. `currentCash()` ejecuta ocho agregaciones separadas además de buscar la caja.
3. El primer artículo requiere crear orden y agregar item en dos ciclos HTTP.
4. Relectura duplicada de items durante recálculo y mapping de la orden.
5. Potencial N+1 de `OrderItem.product` en `mapToResponse()`.
6. Checkout completo dividido en pago y cierre, ambos con validación JWT.
7. JWT consulta tenant y usuario en cada request autenticado.
8. Lealtad consulta programa/items antes de descartar órdenes no elegibles.
9. `spring.jpa.open-in-view` queda activo por defecto; permite SQL tardío durante
   mapping/serialización y dificulta delimitar el costo del service.
10. `API_BASE` y las URLs WebSocket están fijadas a `localhost:8080`, riesgo
    funcional para frontend y backend en contenedores distintos.
11. Las anotaciones `@Index` no garantizan que los índices existan en MariaDB
    porque producción usa `ddl-auto=validate` y no hay migraciones versionadas.

## I. Cambios recomendados ordenados por impacto

1. Paralelizar productos, configuración y caja actual manteniendo manejo de
   errores independiente.
2. Consolidar las ocho agregaciones de caja en consultas condicionales por tabla
   y revisar sus índices con `EXPLAIN`.
3. Eliminar la doble lectura de items y usar un fetch plan explícito para
   `OrderItem.product` al construir la respuesta.
4. Crear una operación aditiva/compatible para crear orden con primer item en
   una sola transacción, manteniendo los endpoints actuales.
5. Diseñar checkout transaccional compatible que registre pago y cierre cuando
   corresponda, manteniendo idempotencia e integridad financiera.
6. Aplicar caché corta y explícitamente invalidable a la validación de
   usuario/tenant JWT; en múltiples instancias usar un mecanismo compartido o
   TTL conservador, sin confiar únicamente en claims para estados revocables.
7. Mover descartes baratos de lealtad antes de sus queries y agrupar operaciones
   por item cuando sea posible.
8. Verificar índices reales de MariaDB con `SHOW INDEX` y planes con `EXPLAIN`;
   crear migraciones antes de modificar índices.
9. Configurar `VITE_API_BASE_URL`/WebSocket por entorno antes del despliegue en
   contenedores separados.
10. Estabilizar callbacks de `NotificationContext` para evitar reconexiones WS.

## Verificación manual de la línea base

1. Reiniciar backend y generar al menos 30 recorridos warm del mismo flujo.
2. En DevTools Network usar Disable cache y Preserve log. Registrar Duration,
   Waiting/TTFB, descarga y `X-Correlation-ID` de cada request.
3. Consultar con JWT `ADMIN`:

   ```text
   /actuator/metrics/http.server.requests?tag=uri:/api/products&tag=method:GET
   /actuator/metrics/http.server.requests?tag=uri:/api/cash/current&tag=method:GET
   /actuator/metrics/http.server.requests?tag=uri:/api/orders&tag=method:POST
   /actuator/metrics/http.server.requests?tag=uri:/api/orders/{orderPublicId}/items&tag=method:POST
   /actuator/metrics/spacekids.service.requests?tag=service:order&tag=operation:add-item
   /actuator/metrics/spacekids.security.jwt.authentication?tag=outcome:authenticated
   /actuator/metrics/hikaricp.connections.pending
   ```

4. Correlacionar el header con logs y comparar: tiempo HTTP total menos timer de
   service/JWT. La diferencia incluye filtros restantes, controller, red de
   salida y serialización; DevTools separa network/TTFB.
5. En staging habilitar el slow query log documentado y revisar las queries por
   encima del umbral. No habilitar `show-sql` ni general query log en producción.
