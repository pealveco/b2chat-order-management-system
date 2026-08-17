# B2Chat — Order Management System

Backend de un sistema simplificado de gestión de pedidos de e-commerce, desarrollado como prueba técnica para el proceso de selección de Backend Developer en B2Chat.

**Estado del proyecto:** 🚧 En desarrollo — este README se actualiza progresivamente a medida que avanza la implementación.

---

## Stack Tecnológico

- **Lenguaje:** Java 21
- **Framework:** Spring Boot (WebFlux — programación reactiva)
- **Persistencia:** PostgreSQL vía R2DBC
- **Cache:** Redis (write-through + read-through fallback para catálogo)
- **Seguridad:** JWT
- **Arquitectura:** Clean Architecture — [Scaffold Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/)
- **Contenedores:** Docker / Docker Compose
- **Testing:** JUnit 5, Mockito, Reactor Test, Testcontainers

---

## Cómo ejecutar el proyecto

La aplicación se levanta junto con PostgreSQL y Redis usando Docker Compose. El archivo `.env` contiene valores de desarrollo local para contenedores y no se versiona porque incluye credenciales. Para ejecución local desde IntelliJ (por ejemplo) se usa `oms.env`, con los mismos valores funcionales pero apuntando a `localhost`.

Primero crea el `.env` local desde el ejemplo versionado:

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Si tu instalación usa el binario legacy:

```bash
docker-compose up --build
```

Este comando crea y levanta:

- `app`: aplicación Spring Boot WebFlux, expuesta en `http://localhost:8080`.
- `postgres`: PostgreSQL 16, expuesto en `localhost:5432`, con volumen persistente `postgres_data`.
- `redis`: Redis 7, expuesto en `localhost:6379`.

`app` depende de los healthchecks de `postgres` y `redis`, por lo que no intenta arrancar hasta que ambos servicios estén disponibles. La app se conecta a esos servicios por variables de entorno:

| Variable | Uso en Docker Compose |
|---|---|
| `SPRING_R2DBC_URL` | URL R2DBC hacia Postgres dentro de la red Docker (`postgres:5432`) |
| `SPRING_R2DBC_USERNAME` / `SPRING_R2DBC_PASSWORD` | Credenciales de Postgres |
| `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` | Host y puerto de Redis dentro de la red Docker (`redis:6379`) |
| `JWT_SECRET` | Secreto local de firma JWT, mínimo 32 bytes |
| `JWT_EXPIRATION_MINUTES` | Tiempo de expiración de los JWT emitidos |

Para verificar que la app quedó arriba:

```bash
curl --location 'http://localhost:8080/actuator/health'
```

Para detener los contenedores:

```bash
docker compose down
```

Para detenerlos y borrar también el volumen de PostgreSQL local:

```bash
docker compose down -v
```

---

## Arquitectura

El proyecto sigue Clean Architecture con la siguiente estructura de módulos:

```
b2chat-order-management-system/
├── applications/app-service/          → Bootstrap, wiring, configuración (Spring Boot main)
├── domain/model/                      → Entidades de dominio puras, sin dependencias de framework
├── domain/usecase/                    → Lógica de negocio, orquesta puertos del dominio
├── infrastructure/driven-adapters/    → Implementaciones de puertos salientes (DB, cache, notificaciones)
├── infrastructure/entry-points/       → Implementaciones de puertos entrantes (REST controllers)
└── infrastructure/helpers/            → Utilidades transversales
```

**Regla de dependencia:** `domain/model` no depende de nada. `domain/usecase` depende solo de `domain/model`. `infrastructure/*` depende de `domain/*`, nunca al revés. Estructura validada con `./gradlew vs` (Validate Structure) del scaffold.

Detalle completo de contratos técnicos, modelo de datos y decisiones de diseño en [`SPEC.md`](./SPEC.md).
Backlog completo de historias de usuario y criterios de aceptación en [`docs/BACKLOG.md`](./docs/BACKLOG.md).

---

## API — Endpoints

### Users
| Método | Endpoint | Auth | Descripción |
|---|---|---|---|
| `POST` | `/users` | Público | Registrar un nuevo usuario *(implementado)* |
| `GET` | `/users/{id}` | Público | Obtener detalles de un usuario *(implementado)* |
| `GET` | `/users/{id}/orders` | Público | Listar pedidos de un usuario *(implementado / bonus)* |

### Products
| Método | Endpoint | Auth | Descripción |
|---|---|---|---|
| `POST` | `/products` | Bearer JWT | Registrar un nuevo producto *(implementado)* |
| `GET` | `/products` | Público | Listar catálogo de productos *(implementado)* |
| `PUT` | `/products/{id}` | Bearer JWT | Actualizar un producto *(implementado)* |
| `DELETE` | `/products/{id}` | Bearer JWT | Eliminar un producto (soft delete) *(implementado)* |

### Orders
| Método | Endpoint | Auth | Descripción |
|---|---|---|---|
| `POST` | `/orders` | Bearer JWT | Crear un pedido (persistencia transaccional + notificación asíncrona) *(implementado)* |
| `GET` | `/orders/{id}` | Público | Obtener detalle de un pedido *(implementado)* |
| `PUT` | `/orders/{id}/status` | Bearer JWT | Actualizar estado de un pedido *(implementado)* |

### Auth *(bonus)*
| Método | Endpoint | Auth | Descripción |
|---|---|---|---|
| `POST` | `/auth/token` | Público | Emitir token JWT *(implementado / bonus)* |

> Ejemplos completos de request/response en [`SPEC.md`](./SPEC.md#6-contratos-de-api).

---

## Decisiones de diseño

### Manejo asíncrono de pedidos
La creación de un pedido (`POST /orders`) valida usuario, valida productos activos, descuenta stock y persiste el pedido de forma síncrona y transaccional (no puede ser "eventual", ya que involucra descuento de inventario). Lo que se procesa de forma **asíncrona** es el envío de la notificación de recepción: se emite un `OrderPlacedEvent` en memoria (`Sinks.Many` de Project Reactor) inmediatamente después de persistir, y un listener independiente procesa la notificación sin bloquear la respuesta HTTP.

Cuando un pedido pasa a `COMPLETED`, se emite un `OrderCompletedEvent` usando el mismo patrón de notificación asíncrona en memoria. El listener simula el envío con un log estructurado.

En un entorno productivo, este mecanismo se reemplazaría por un publisher real hacia **AWS SQS/SNS o EventBridge** — el principio de desacople (responder rápido, notificar aparte) es el mismo que se aplicaría en producción.

### Concurrencia en descuento de stock
Para evitar sobreventa bajo pedidos concurrentes, el descuento de stock **no** usa el patrón leer-luego-escribir (vulnerable a condiciones de carrera incluso en R2DBC). En su lugar, se usa una actualización condicional atómica a nivel de base de datos:

```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND active = TRUE AND stock >= :quantity;
```

Si el número de filas afectadas es 0, se interpreta como stock insuficiente y la operación se aborta.

US-008 queda cubierta explícitamente con `ProductStockConcurrencyIntegrationTest`, que ejecuta descuentos concurrentes contra PostgreSQL vía Testcontainers y verifica que, cuando dos pedidos compiten por el último ítem, exactamente uno actualiza la fila y el otro falla sin dejar stock negativo.

### Estrategia de caché
Actualmente se implementa **write-through** para productos: toda creación o actualización de producto persiste primero en Postgres y, si la persistencia confirma, escribe el producto en Redis con la clave `product:{id}` y registra el id en el set `products:all`. La eliminación usa soft delete en Postgres (`active=false`) y luego elimina la clave individual de Redis y remueve el id del set `products:all`.

La consulta de catálogo (`GET /products`) implementa **read-through fallback**: primero intenta reconstruir la lista desde Redis usando `products:all`; ante un miss real — cache frío en el arranque, entrada incompleta, TTL expirado o evicción — lee desde Postgres, responde al cliente y repuebla Redis para futuras lecturas.

Cuando `POST /orders` descuenta stock, refresca en Redis las claves de los productos afectados después de confirmar la transacción en Postgres. Cuando `PUT /orders/{id}/status` cancela un pedido, repone stock y refresca esas mismas claves. Así `products:all` conserva el índice completo del catálogo y `product:{id}` queda con el stock actualizado.

Para un entorno de mayor tráfico, el diseño completo evolucionaría a incluir TTL con jitter (para evitar expiración simultánea de claves y *thundering herd*) y un job de *refresh-ahead* que renueve proactivamente las claves antes de vencer. Esto queda documentado como evolución natural del diseño — ver [Assumptions](#assumptions).

### Desarrollo asistido por IA (Spec-Driven Development)
Este proyecto se desarrolló usando **Claude Code** bajo un enfoque de Spec-Driven Development (SDD): definición de backlog (historias de usuario + criterios de aceptación) → especificación técnica (`SPEC.md`, contratos de API, modelo de datos, decisiones de arquitectura) → implementación guiada por esa especificación → validación de estructura con `./gradlew vs` del scaffold Bancolombia. Este proceso se mantuvo documentado y versionado a lo largo del desarrollo, no aplicado de forma ad-hoc.

---

## Assumptions

Decisiones de alcance no especificadas explícitamente en el enunciado de la prueba técnica:

1. **Caching:** write-through implementado para creación/actualización/eliminación de producto y read-through fallback implementado para catálogo. TTL con jitter y job de refresh-ahead quedan documentados como evolución productiva.
2. **Notificaciones:** simuladas en memoria con un patrón productor/consumidor reactivo, representando el mismo principio de desacople que se usaría en producción con AWS SQS/SNS o EventBridge.
3. **Eliminación de productos:** soft delete (`active=false`) en lugar de DELETE físico, para preservar integridad referencial con pedidos históricos.
4. **Cancelación de pedidos:** al cancelar un pedido, se repone automáticamente el stock descontado.
5. **Autenticación JWT:** endpoint simplificado de emisión de token basado en `userId`/`email` existente, sin flujo completo de credenciales/password, dado que el enunciado no lo especifica. Los endpoints de escritura (`POST`, `PUT`, `DELETE`) quedan protegidos con `Authorization: Bearer {token}`; `POST /users`, `POST /auth/token` y los `GET` quedan públicos por alcance de la prueba.
6. **Concurrencia en stock:** UPDATE condicional atómico a nivel de base de datos, no lectura-luego-escritura en código.
7. **Consistencia de cache en pedidos:** al crear o cancelar pedidos se refresca cache de productos afectados después de confirmar Postgres; no se intenta actualizar Redis dentro de la transacción de base de datos.
8. **Historial de pedidos:** `GET /users/{id}/orders` se implementa sin paginación por alcance de la prueba. En producción debería evolucionar a paginación por cursor o `page/size` con orden estable por `createdAt`.

---

## Testing

- **Unitario:** casos de uso del dominio con puertos mockeados (JUnit 5 + Mockito + Reactor Test).
- **Integración:** endpoints críticos y concurrencia de stock contra instancias reales de PostgreSQL/Redis vía Testcontainers o contenedores equivalentes.

```bash
./gradlew :model:test :usecase:test :r2dbc-postgresql:test :notification:test :reactive-web:test :app-service:classes
```

---

## Autor

Piter Velasquez — [LinkedIn](https://linkedin.com/in/piter-velasquez-cota) · [Portfolio](https://portfolio-piter.pealveco.workers.dev)
