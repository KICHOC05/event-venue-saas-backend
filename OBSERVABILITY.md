# Observabilidad del backend

La configuración usa Spring Boot Actuator, Micrometer y el registro de
Prometheus incluidos en Spring Boot 4.0.4. No cambia contratos HTTP ni lógica de
negocio.

## Endpoints y seguridad

Solo se publican estos endpoints:

| Endpoint | Acceso |
| --- | --- |
| `GET /actuator/health` | Público, solo estado general; sin componentes ni detalles |
| `GET /actuator/info` | Rol `ADMIN` |
| `GET /actuator/metrics` y `/actuator/metrics/{name}` | Rol `ADMIN` |
| `GET /actuator/prometheus` | Rol `ADMIN` |

El resto de `/actuator/**` está denegado aunque alguien lo habilite por error.
No se exponen `env`, `configprops`, `beans`, `heapdump`, `loggers` ni datos de
request. Los logs solo añaden el identificador de correlación; nunca imprimen
Authorization, cookies, tokens o cuerpos.

Para consultar una métrica con un JWT administrativo:

```bash
curl -H "Authorization: Bearer <admin-jwt>" \
  http://localhost:8080/actuator/metrics/http.server.requests
```

## Métricas disponibles

- `http.server.requests`: duración HTTP completa, incluyendo la conversión y
  escritura de la respuesta por Spring MVC. Publica p50, p95, p99, máximo e
  histograma. `uri` procede de la plantilla resuelta (`/api/events/{publicId}`),
  no de valores concretos. Un filtro limita el número de valores `uri` a 100.
- `hikaricp.connections.active`, `hikaricp.connections.idle`,
  `hikaricp.connections.pending` y `hikaricp.connections.timeout`: estado y
  timeouts del pool `spacekids-primary`. Actuator también publica métricas JDBC
  genéricas cuando corresponda.
- `spring.data.repository.invocations`: instrumentación automática de Spring
  Data. No se anotaron repositorios manualmente para no medirlos dos veces.
- `spacekids.service.requests`: tiempos de operaciones críticas de login,
  clientes frecuentes, disponibilidad, eventos, pagos, POS, temporizadores y
  dashboard. Sus tags `service` y `operation` pertenecen a un conjunto fijo.
- `spacekids.security.jwt.authentication`: tiempo dedicado exclusivamente a
  validar y construir la autenticación JWT, antes de ejecutar controller y
  service. El tag `outcome` usa un conjunto cerrado y nunca contiene usuarios,
  tenants, tokens ni identificadores de request.

`X-Correlation-ID` nunca se añade como tag de métricas. Si el cliente manda un
valor seguro de hasta 128 caracteres se conserva; de lo contrario se genera un
UUID. El valor se devuelve en el header y se elimina del MDC al terminar, incluso
si el request falla.

No existe una segunda métrica exclusiva de serialización. Medirla de forma
separada requeriría envolver la escritura de los `HttpMessageConverter` y podría
alterar streaming o respuestas; la temporización oficial de Spring MVC ya cubre
el ciclo HTTP completo sin leer el body dos veces.

## Prometheus en VPS

El scraper debe llegar al backend por una red privada y autenticarse con un JWT
de rol `ADMIN`. No publique `/actuator/prometheus` directamente en Internet. Si
el JWT expira, configure el componente de despliegue para renovarlo y actualizar
el `bearer_token_file`, o proteja el acceso en un proxy interno con una identidad
de máquina sin relajar las reglas de la aplicación.

Variables no secretas disponibles:

- `HTTP_SERVER_REQUESTS_MAX_EXPECTED` (por defecto `10s`).
- `MANAGEMENT_MAX_URI_TAGS` (por defecto `100`).
- `DB_POOL_NAME` (por defecto `spacekids-primary`).

## Slow query log de MariaDB en staging

El perfil `staging` mantiene apagados `spring.jpa.show-sql`, `org.hibernate.SQL`
y los valores de bind. El slow query log se activa en el servidor MariaDB, no en
la aplicación.

1. Copie `ops/mariadb/staging-slow-query.cnf.example` al directorio de
   configuración del MariaDB de staging.
2. Ajuste `long_query_time` con `MARIADB_SLOW_QUERY_TIME_SECONDS` y
   `min_examined_row_limit` con `MARIADB_SLOW_QUERY_MIN_EXAMINED_ROWS` durante el
   despliegue. Los archivos `.cnf` no expanden variables de entorno por sí solos.
3. Monte un volumen persistente/escribible para `/var/log/mysql` y configure la
   rotación del archivo `mariadb-slow.log`.
4. Reinicie únicamente MariaDB de staging y compruebe con:

   ```sql
   SHOW GLOBAL VARIABLES WHERE Variable_name IN
     ('slow_query_log', 'slow_query_log_file', 'long_query_time',
      'min_examined_row_limit', 'general_log');
   ```

El archivo fuerza `general_log=OFF`. No monte esta configuración en producción
sin una decisión operativa explícita; nunca habilite el general query log para
esta necesidad.
