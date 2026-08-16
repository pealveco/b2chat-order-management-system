# SPEC.md — B2Chat Order Management System

> Fuente de verdad técnica para el desarrollo asistido por Claude Code bajo enfoque Spec-Driven Development (SDD).
> El backlog (`B2Chat_Backlog_Prueba_Tecnica.md`) define el QUÉ (historias + criterios de aceptación).
> Este documento define el CÓMO (contratos, modelo de datos, estructura exacta de módulos, decisiones de arquitectura).

**Repositorio:** `b2chat-order-management-system`
**Package base:** `com.b2chat.ordermanagement`
**Scaffold:** Bancolombia Clean Architecture — `--type=reactive --lombok=true --java-version=21`

---

## 1. Estructura de módulos (ya generada)

```
b2chat-order-management-system/
├── applications/app-service/          → Bootstrap, wiring, config (Spring Boot main)
├── domain/model/                      → Entidades de dominio puras, sin frameworks
├── domain/usecase/                    → Lógica de negocio, orquesta puertos del dominio
├── infrastructure/driven-adapters/    → Implementaciones de puertos salientes (DB, cache, notificaciones)
├── infrastructure/entry-points/       → Implementaciones de puertos entrantes (REST funcional WebFlux)
└── infrastructure/helpers/            → Utilidades transversales (si aplica)
```

**Regla de dependencia (Clean Architecture):** `domain/model` no depende de nada. `domain/usecase` depende solo de `domain/model` (y de interfaces/puertos que él mismo define). `infrastructure/*` depende de `domain/*`, nunca al revés. Validar con `./gradlew vs` (Validate Structure) antes de cada commit importante.

---

## 2. Módulos de infraestructura a generar

Usar las tareas del scaffold (`Generate Driven Adapter` / `Generate Entry Point`) para mantener consistencia de convenciones. Módulos requeridos:

| Módulo | Tipo | Responsabilidad |
|---|---|---|
| `infrastructure/driven-adapters/r2dbc-postgresql` | Driven adapter | Persistencia de `users`, `products`, `orders`, `order_items` |
| `infrastructure/driven-adapters/redis` | Driven adapter | Cache write-through + read-through del catálogo de productos |
| `infrastructure/driven-adapters/notification-adapter` | Driven adapter (custom, no viene por defecto en el scaffold — crear manualmente siguiendo la misma convención de carpeta) | Listener de eventos `OrderPlacedEvent` / `OrderCompletedEvent`, simula envío de notificación (log estructurado) |
| `infrastructure/entry-points/reactive-web` | Entry point | Handlers/Routers funcionales WebFlux + filtro de seguridad JWT |

**Nota:** confirmar contra `gradle generateDrivenAdapter` (listado de tipos disponibles) que `r2dbc` y `redis` estén soportados como tipos nativos del scaffold antes de generarlos — si el nombre exacto de la tarea difiere, ajustar aquí y no asumir.

---

## 3. Modelo de dominio (`domain/model`)

### 3.1 Entidades

```java
// User.java
public class User {
    private UUID id;
    private Email email;      // value object, valida formato en constructor
    private String name;
    private String address;
}

// Product.java
public class Product {
    private UUID id;
    private String name;
    private String description;
    private Money price;      // value object, valida > 0
    private Integer stock;    // valida >= 0
    // active se agregará cuando se implemente soft delete/listado de catálogo
}

// Order.java
public class Order {
    private UUID id;
    private UUID userId;
    private List<OrderItem> items;
    private OrderStatus status;
    private Instant createdAt;
}

// OrderItem.java
public class OrderItem {
    private UUID productId;
    private Integer quantity;
    private Money unitPriceAtOrderTime;  // snapshot del precio al momento del pedido
}

// OrderStatus.java (enum)
public enum OrderStatus {
    PENDING, PROCESSING, COMPLETED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        // PENDING -> PROCESSING, CANCELLED
        // PROCESSING -> COMPLETED, CANCELLED
        // COMPLETED -> (terminal, sin transiciones)
        // CANCELLED -> (terminal, sin transiciones)
    }
}
```

### 3.2 Value Objects

```java
// Email.java — valida formato regex en el constructor, lanza excepción de dominio si es inválido
// Money.java — encapsula BigDecimal, valida > 0 en el constructor donde aplique
```

Los value objects no tienen gateway/repository propio. Ejemplo: `Email` y `Money` viven dentro de agregados como `User` y `Product`; se persisten como columnas del agregado (`users.email`, `products.price`) mediante el repository del agregado.

### 3.3 Eventos de dominio (para el mecanismo async)

```java
// OrderPlacedEvent.java
public record OrderPlacedEvent(UUID orderId, UUID userId, Instant occurredAt) {}

// OrderCompletedEvent.java
public record OrderCompletedEvent(UUID orderId, UUID userId, Instant occurredAt) {}
```

---

## 4. Puertos de dominio (interfaces definidas en `domain/usecase`, implementadas en `infrastructure`)

```java
// Puertos de persistencia
public interface UserRepository {
    Mono<User> save(User user);
    Mono<User> findById(UUID id);
    Mono<Boolean> existsByEmail(Email email);
}

public interface ProductRepository {
    Mono<Product> save(Product product);
    // Los siguientes métodos se agregan cuando las HUs los necesiten:
    Mono<Product> findById(UUID id);
    Flux<Product> findAllActive();
    Mono<Void> deleteById(UUID id); // soft delete: update active=false
    Mono<Long> decrementStockIfAvailable(UUID productId, int quantity);
    // ^ UPDATE condicional atómico: WHERE id=? AND stock >= ? -> retorna filas afectadas
    Mono<Void> incrementStock(UUID productId, int quantity); // usado en cancelación
}

public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(UUID id);
    Flux<Order> findByUserId(UUID userId);
    Mono<Order> updateStatus(UUID orderId, OrderStatus newStatus);
}

// Puerto de cache
public interface ProductCachePort {
    Mono<Void> put(Product product);          // write-through
    // Los siguientes métodos se agregan cuando las HUs los necesiten:
    Mono<Product> get(UUID productId);
    Mono<Void> evict(UUID productId);          // usado en soft delete
    Flux<Product> getAll();
    Mono<Void> putAll(List<Product> products); // repoblado read-through
}

// Puerto de eventos/notificación
public interface OrderEventPublisher {
    void publishOrderPlaced(OrderPlacedEvent event);
    void publishOrderCompleted(OrderCompletedEvent event);
}
```

---

## 5. Casos de uso (`domain/usecase`)

| Clase | Depende de (puertos) | Resumen de lógica |
|---|---|---|
| `RegisterUserUseCase` | `UserRepository` | Valida email único → guarda → retorna `User` |
| `GetUserUseCase` | `UserRepository` | Busca por ID → error de dominio si no existe |
| `CreateProductUseCase` | `ProductRepository`, `ProductCachePort` | Guarda en Postgres → escribe en cache (write-through, secuencial, solo si Postgres confirmó) |
| `ListProductsUseCase` | `ProductRepository`, `ProductCachePort` | Intenta cache → si vacío o expirado, lee Postgres y repuebla cache (read-through) |
| `UpdateProductUseCase` | `ProductRepository`, `ProductCachePort` | Actualiza Postgres → actualiza cache (write-through) |
| `DeleteProductUseCase` | `ProductRepository`, `ProductCachePort` | Soft delete en Postgres (`active=false`) → evict de cache |
| `PlaceOrderUseCase` | `UserRepository`, `ProductRepository`, `OrderRepository`, `OrderEventPublisher` | Valida usuario existe → valida cada producto existe → `decrementStockIfAvailable` por cada ítem (si alguno falla, abortar toda la operación — ver nota de atomicidad abajo) → guarda `Order` en `PENDING` → publica `OrderPlacedEvent` |
| `GetOrderUseCase` | `OrderRepository` | Busca por ID → error de dominio si no existe |
| `UpdateOrderStatusUseCase` | `OrderRepository`, `ProductRepository`, `OrderEventPublisher` | Valida transición con `OrderStatus.canTransitionTo` → si es a `CANCELLED`, repone stock (`incrementStock` por cada ítem) → si es a `COMPLETED`, publica `OrderCompletedEvent` |
| `GetUserOrdersUseCase` | `UserRepository`, `OrderRepository` | Valida usuario existe → retorna `Flux<Order>` |

**Nota de atomicidad en `PlaceOrderUseCase`:** dado que R2DBC no maneja transacciones distribuidas triviales entre múltiples `decrementStockIfAvailable` reactivos, usar `@Transactional` reactivo (`TransactionalOperator` de Spring) envolviendo la secuencia de descuentos + guardado del pedido, de modo que si cualquier producto falla por stock insuficiente, toda la operación hace rollback — no debe quedar un pedido con descuentos parciales.

---

## 6. Contratos de API (`infrastructure/entry-points/reactive-web`)

### 6.0 Convenciones HTTP

El entry point REST usa WebFlux funcional siguiendo el estilo del scaffold Bancolombia, con una dupla por recurso:

```text
UserHandler + UserRouterRest
ProductHandler + ProductRouterRest
OrderHandler + OrderRouterRest
```

Los `Handler` / `RouterRest` de ejemplo generados por el scaffold no se exponen en producción; solo se conservan rutas reales por entidad.

Las respuestas exitosas usan envoltura estándar:

```json
{
  "data": {},
  "meta": {
    "path": "/resource",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
```

Las respuestas de error usan envoltura estándar:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "status": 400,
    "path": "/resource",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
```

Validación:

- DTO/request valida contrato HTTP con Jakarta Bean Validation (`@NotBlank`, `@Email`, etc.) mediante un `RequestValidator` reusable.
- Dominio valida invariantes internas con value objects y excepciones de dominio. Ejemplo: `Email` nunca puede existir inválido aunque el dato no venga desde HTTP.
- `DomainException` se mantiene como base común para errores de negocio con `code`.
- Excepciones propias del entry point viven bajo `api.exception`.
- Respuestas exitosas viven bajo `api.response`.
- Errores serializables y handler global viven bajo `api.error`.

### 6.1 Users

**`POST /users`**
```json
// Request
{ "email": "juan@example.com", "name": "Juan Perez", "address": "Cra 10 #20-30, Bogotá" }
// Response 201
{
  "data": {
    "id": "uuid",
    "email": "juan@example.com",
    "name": "Juan Perez",
    "address": "Cra 10 #20-30, Bogotá"
  },
  "meta": {
    "path": "/users",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
// Response 409 (email duplicado)
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "A user with email juan@example.com already exists",
    "status": 409,
    "path": "/users",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
```

Respuestas mínimas profesionales cubiertas para `POST /users`:

| Status | Caso |
|---|---|
| `201 Created` | Usuario registrado |
| `400 Bad Request` | Body inválido, body vacío, validación de DTO o invariant de dominio inválida |
| `409 Conflict` | Email duplicado |
| `415 Unsupported Media Type` | `Content-Type` diferente de `application/json` |
| `503 Service Unavailable` | Repositorio/persistencia temporalmente no disponible |
| `500 Internal Server Error` | Fallback no controlado |

**`GET /users/{id}`**

```json
// Response 200
{
  "data": {
    "id": "uuid",
    "email": "juan@example.com",
    "name": "Juan Perez",
    "address": "Cra 10 #20-30, Bogotá"
  },
  "meta": {
    "path": "/users/{id}",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
// Response 404
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User with id {id} was not found",
    "status": 404,
    "path": "/users/{id}",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
// Response 400
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Path variable id must be a valid UUID",
    "status": 400,
    "path": "/users/not-a-uuid",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
```

### 6.2 Products

**`POST /products`**
```json
// Request
{ "name": "Mouse inalámbrico", "description": "...", "price": 25000, "stock": 100 }
// Response 201
{
  "data": {
    "id": "uuid",
    "name": "Mouse inalámbrico",
    "description": "...",
    "price": 25000,
    "stock": 100
  },
  "meta": {
    "path": "/products",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
```

US-003 usa write-through secuencial dentro de `CreateProductUseCase`: primero guarda en Postgres mediante `ProductRepository`; si la persistencia confirma, escribe el mismo producto en Redis mediante `ProductCachePort` con la clave `product:{id}`. Si falla Postgres o Redis, la API responde `503 Service Unavailable` con el envelope estándar de error.

**`GET /products`** → `200`, array de productos (desde cache si está disponible).

**`PUT /products/{id}`** → mismo body que POST, `200` actualizado, `404` si no existe.

**`DELETE /products/{id}`** → `204`, `404` si no existe.

### 6.3 Orders

**`POST /orders`**
```json
// Request
{ "userId": "uuid", "items": [ { "productId": "uuid", "quantity": 2 } ] }
// Response 202 Accepted (async: notificación se procesa después)
{ "id": "uuid", "userId": "uuid", "status": "PENDING", "items": [...] }
// Response 409 (stock insuficiente)
{ "error": "INSUFFICIENT_STOCK", "message": "Product {id} has only N units available" }
```

**`GET /orders/{id}`** → `200` con detalle completo, `404` si no existe.

**`PUT /orders/{id}/status`**
```json
// Request
{ "status": "PROCESSING" }
// Response 200
{ "id": "uuid", "status": "PROCESSING", ... }
// Response 400 (transición inválida)
{ "error": "INVALID_STATUS_TRANSITION", "message": "Cannot transition from COMPLETED to PENDING" }
```

**`GET /users/{id}/orders`** (Bonus) → `200`, array de pedidos del usuario (vacío si no tiene).

### 6.4 Auth (Bonus — JWT)

**`POST /auth/token`**
```json
// Request
{ "userId": "uuid" }
// Response 200
{ "token": "eyJhbGciOi..." }
```

Endpoints protegidos (requieren `Authorization: Bearer {token}`): operaciones de escritura operativas (`POST`, `PUT`, `DELETE`) según alcance de cada HU. `POST /users` queda público para registro. `POST /products` queda temporalmente público durante US-003 porque aún no existe HU de autenticación/roles de administrador. Los `GET` quedan abiertos salvo que una HU indique lo contrario.

---

## 7. Modelo de datos (PostgreSQL — DDL de referencia)

Durante esta fase, el schema local y los datos mínimos esenciales para probar HUs se centralizan en:

```text
infrastructure/driven-adapters/r2dbc-postgresql/src/main/resources/r2dbc-schema.sql
```

Ese archivo se carga al arrancar la aplicación mediante `R2dbcSchemaInitializerConfig`, usando `ConnectionFactoryInitializer` y `ResourceDatabasePopulator`.

Convención del archivo:

```sql
-- Schema
-- TODO: Move schema evolution and seed data to Flyway or Liquibase when migrations are introduced.

-- DDL idempotente

-- Seed data

-- INSERT idempotentes con ON CONFLICT (...) DO NOTHING
```

Cuando el proyecto requiera migraciones formales, mover:

- evolución de esquema a Flyway o Liquibase
- seeds de desarrollo a scripts/versiones separadas según ambiente
- fixtures de pruebas a tests o contenedores de integración

### 7.1 Jerarquía para consultas R2DBC

Para mantener consistencia profesional en los adapters de persistencia, usar esta jerarquía:

1. **Query methods derivados**

   Primera opción para consultas simples, cortas y evidentes. El nombre del método debe seguir siendo legible.

   ```java
   Mono<Boolean> existsByEmail(String email);
   Flux<OrderData> findByUserId(UUID userId);
   Flux<ProductData> findAllByActiveTrue();
   ```

2. **`@Query`**

   Usar cuando el query method derivado se vuelve largo, cuando el SQL fijo es más claro, o cuando se necesita una operación específica como un update atómico.

   ```java
   @Query("""
       SELECT *
       FROM orders
       WHERE user_id = :userId
         AND status = :status
   """)
   Flux<OrderData> findUserOrdersByStatus(UUID userId, String status);
   ```

   ```java
   @Query("""
       UPDATE products
       SET stock = stock - :quantity
       WHERE id = :productId
         AND stock >= :quantity
   """)
   Mono<Integer> decrementStockIfAvailable(UUID productId, int quantity);
   ```

3. **`DatabaseClient` / `R2dbcEntityTemplate`**

   Usar para consultas dinámicas, filtros opcionales, joins con mapeo manual, flujos multi-step o casos donde se requiere control fino del SQL y del mapping.

   ```java
   databaseClient.sql("""
       SELECT *
       FROM products
       WHERE active = true
         AND (:name IS NULL OR name ILIKE :name)
   """)
   .bind("name", nameFilter)
   .map((row, metadata) -> toProductData(row))
   .all();
   ```

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_at_order_time NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

**Query crítica (concurrencia de stock):**
```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND stock >= :quantity;
-- Verificar rowsUpdated == 0 -> stock insuficiente -> abortar
```

---

## 8. Estructura de claves Redis

```
product:{productId}   -> JSON serializado del producto {id, name, description, price, stock}
products:all          -> Futuro índice de productId activos para listado read-through
```

US-003 solo escribe `product:{id}` con `ReactiveRedisTemplate`. `GET /products` combinará read-through: consultar Redis primero; si la entrada individual o el índice de catálogo no existe, leer Postgres, responder y repoblar Redis.

TTL recomendado para catálogo:

- Usar TTL en productos individuales para evitar datos indefinidamente obsoletos.
- Aplicar TTLs escalonados con jitter, por ejemplo 10-15 minutos por producto, para evitar expiración masiva simultánea.
- Cuando exista listado/read-through, evaluar un job programado que refresque claves cercanas a expirar desde Postgres. Ese job no reemplaza la fuente de verdad; solo reduce misses y latencia.
- En escrituras (`POST`, futuro `PUT`, futuro `DELETE`) mantener write-through/evict para que los cambios importantes actualicen o invaliden cache inmediatamente.

---

## 9. Mecanismo async (eventos en memoria)

Pendiente para US-007: la operación crítica de negocio debe ser síncrona y transaccional
validar usuario, validar productos, descontar stock y persistir la orden. Solo la notificación
de recepción del pedido será asíncrona.

```java
// OrderEventPublisherAdapter.java (infrastructure/driven-adapters/notification-adapter)
private final Sinks.Many<Object> eventSink = Sinks.many().multicast().onBackpressureBuffer();

public void publishOrderPlaced(OrderPlacedEvent event) {
    eventSink.tryEmitNext(event);
}
// listener suscrito en el arranque de la app (applications/app-service) consume el Flux
// y ejecuta el "envío" de notificación (log estructurado) sin bloquear el hilo de la request HTTP
```

Documentar en README: en producción, este `Sinks.Many` se reemplazaría por un publisher real hacia AWS SQS/SNS o EventBridge — el principio de desacople (responder rápido, notificar aparte) es el mismo.

Resumen de la decisión para retomar US-007:

- `OrderPlacedEvent` representa el hecho de dominio "pedido recibido/creado".
- `Sinks.Many<OrderPlacedEvent>` funcionará como bus de eventos en memoria para la prueba técnica.
- `OrderEventPublisher` publicará el evento después de persistir la orden.
- `OrderEventListener` escuchará el flujo y simulará la notificación con log estructurado.
- No se usará otro microservicio ni AWS real en esta prueba; en producción se reemplazaría por SQS/SNS/EventBridge con retries, durabilidad e idempotencia.

---

## 10. Seguridad (JWT)

- Librería: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`.
- `SecurityWebFilterChain` (Spring Security Reactive) protegiendo operaciones de escritura según alcance de cada HU.
- `POST /users` queda público para permitir registro de usuarios.
- `POST /products` queda público temporalmente hasta implementar autenticación/roles de administrador.
- Regla general futura: proteger `POST`, `PUT`, `DELETE` operativos; `GET` públicos salvo que una HU indique lo contrario.
- Secret de firma vía variable de entorno (`JWT_SECRET`), nunca hardcoded — coherente con buenas prácticas ya aplicadas en tu experiencia (Cognito/OAuth2 en AB InBev).

---

## 11. Testing

| Tipo | Alcance | Herramientas |
|---|---|---|
| Unitario | Todos los use cases de `domain/usecase`, con puertos mockeados | JUnit 5, Mockito, `StepVerifier` (Reactor Test) |
| Entry point | Contratos HTTP, status codes, response envelope, error envelope y validaciones de request | `WebTestClient` |
| Adapter | Mapeo de entidades, errores de persistencia y constraints | JUnit 5, Mockito, `StepVerifier` |
| Integración | `POST /users` (incl. email duplicado), `POST /orders` (incl. stock insuficiente → 409), `PUT /orders/{id}/status` | `@SpringBootTest`, `WebTestClient`, Testcontainers (Postgres + Redis reales) |

---

## 12. Docker

`docker-compose.yml` con servicios `app`, `postgres`, `redis`, healthchecks para orden de arranque. `Dockerfile` multi-stage ya generado por el scaffold en `deployment/Dockerfile` — ajustar según se requiera para exponer el puerto correcto y variables de entorno (`SPRING_R2DBC_URL`, `SPRING_REDIS_HOST`, `JWT_SECRET`).

---

## 13. Orden de implementación recomendado

1. `domain/model` — entidades, value objects, `OrderStatus` con transiciones
2. `domain/usecase` — puertos (interfaces) + casos de uso de Usuarios y Productos primero (más simples, validan que el scaffold responde)
3. `infrastructure/driven-adapters/r2dbc-postgresql` — implementación de repos + DDL
4. `infrastructure/entry-points/reactive-web` — handlers/routers funcionales de Usuarios y Productos
5. `infrastructure/driven-adapters/redis` — cache write-through/read-through
6. `domain/usecase` — `PlaceOrderUseCase` + `UpdateOrderStatusUseCase` (la pieza más compleja: concurrencia + async)
7. `infrastructure/driven-adapters/notification-adapter` — mecanismo de eventos
8. Seguridad JWT
9. Docker
10. Testing (idealmente en paralelo a cada paso anterior, no todo al final)
11. README final con assumptions + development approach

---

## 14. Registro de decisiones (sincronizar con Assumptions del backlog)

Este documento se actualiza junto con la sección "Assumptions consolidadas" de `BACKLOG.md` a medida que surjan nuevas decisiones durante la implementación.
