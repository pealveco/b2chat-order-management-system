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
├── infrastructure/entry-points/       → Implementaciones de puertos entrantes (REST controllers)
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
| `infrastructure/entry-points/reactive-web` | Entry point | Controllers REST + filtro de seguridad JWT |

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
    private boolean active;   // soft delete flag
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
    Mono<Product> get(UUID productId);
    Mono<Void> put(Product product);          // write-through
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
| `ListProductsUseCase` | `ProductRepository`, `ProductCachePort` | Intenta cache → si vacío, lee Postgres y repuebla cache (read-through) |
| `UpdateProductUseCase` | `ProductRepository`, `ProductCachePort` | Actualiza Postgres → actualiza cache (write-through) |
| `DeleteProductUseCase` | `ProductRepository`, `ProductCachePort` | Soft delete en Postgres (`active=false`) → evict de cache |
| `PlaceOrderUseCase` | `UserRepository`, `ProductRepository`, `OrderRepository`, `OrderEventPublisher` | Valida usuario existe → valida cada producto existe → `decrementStockIfAvailable` por cada ítem (si alguno falla, abortar toda la operación — ver nota de atomicidad abajo) → guarda `Order` en `PENDING` → publica `OrderPlacedEvent` |
| `GetOrderUseCase` | `OrderRepository` | Busca por ID → error de dominio si no existe |
| `UpdateOrderStatusUseCase` | `OrderRepository`, `ProductRepository`, `OrderEventPublisher` | Valida transición con `OrderStatus.canTransitionTo` → si es a `CANCELLED`, repone stock (`incrementStock` por cada ítem) → si es a `COMPLETED`, publica `OrderCompletedEvent` |
| `GetUserOrdersUseCase` | `UserRepository`, `OrderRepository` | Valida usuario existe → retorna `Flux<Order>` |

**Nota de atomicidad en `PlaceOrderUseCase`:** dado que R2DBC no maneja transacciones distribuidas triviales entre múltiples `decrementStockIfAvailable` reactivos, usar `@Transactional` reactivo (`TransactionalOperator` de Spring) envolviendo la secuencia de descuentos + guardado del pedido, de modo que si cualquier producto falla por stock insuficiente, toda la operación hace rollback — no debe quedar un pedido con descuentos parciales.

---

## 6. Contratos de API (`infrastructure/entry-points/reactive-web`)

### 6.1 Users

**`POST /users`**
```json
// Request
{ "email": "juan@example.com", "name": "Juan Perez", "address": "Cra 10 #20-30, Bogotá" }
// Response 201
{ "id": "uuid", "email": "juan@example.com", "name": "Juan Perez", "address": "Cra 10 #20-30, Bogotá" }
// Response 409 (email duplicado)
{ "error": "EMAIL_ALREADY_EXISTS", "message": "..." }
```

**`GET /users/{id}`** → `200` con el usuario, `404` si no existe.

### 6.2 Products

**`POST /products`**
```json
// Request
{ "name": "Mouse inalámbrico", "description": "...", "price": 25000, "stock": 100 }
// Response 201
{ "id": "uuid", "name": "...", "description": "...", "price": 25000, "stock": 100 }
```

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

Endpoints protegidos (requieren `Authorization: Bearer {token}`): todos los `POST`, `PUT`, `DELETE`. Los `GET` quedan abiertos (no especificado en el enunciado como requisito, decisión de alcance — documentar en assumptions).

---

## 7. Modelo de datos (PostgreSQL — DDL de referencia)

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
product:{productId}   -> Hash con campos {name, description, price, stock, active}
products:all          -> Set de todos los productId activos (evita KEYS * al listar)
```

Operaciones: `HSET`/`HGETALL` para individual, `SADD`/`SMEMBERS` + pipeline de `HGETALL` para el listado completo.

---

## 9. Mecanismo async (eventos en memoria)

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

---

## 10. Seguridad (JWT)

- Librería: `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson`.
- `SecurityWebFilterChain` (Spring Security Reactive) protegiendo `POST`, `PUT`, `DELETE`; `GET` públicos.
- Secret de firma vía variable de entorno (`JWT_SECRET`), nunca hardcoded — coherente con buenas prácticas ya aplicadas en tu experiencia (Cognito/OAuth2 en AB InBev).

---

## 11. Testing

| Tipo | Alcance | Herramientas |
|---|---|---|
| Unitario | Todos los use cases de `domain/usecase`, con puertos mockeados | JUnit 5, Mockito, `StepVerifier` (Reactor Test) |
| Integración | `POST /users` (incl. email duplicado), `POST /orders` (incl. stock insuficiente → 409), `PUT /orders/{id}/status` | `@SpringBootTest`, `WebTestClient`, Testcontainers (Postgres + Redis reales) |

---

## 12. Docker

`docker-compose.yml` con servicios `app`, `postgres`, `redis`, healthchecks para orden de arranque. `Dockerfile` multi-stage ya generado por el scaffold en `deployment/Dockerfile` — ajustar según se requiera para exponer el puerto correcto y variables de entorno (`SPRING_R2DBC_URL`, `SPRING_REDIS_HOST`, `JWT_SECRET`).

---

## 13. Orden de implementación recomendado

1. `domain/model` — entidades, value objects, `OrderStatus` con transiciones
2. `domain/usecase` — puertos (interfaces) + casos de uso de Usuarios y Productos primero (más simples, validan que el scaffold responde)
3. `infrastructure/driven-adapters/r2dbc-postgresql` — implementación de repos + DDL
4. `infrastructure/entry-points/reactive-web` — controllers de Usuarios y Productos
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
