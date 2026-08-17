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
| `infrastructure/driven-adapters/notification` | Driven adapter genérico del scaffold | Publisher/listener de eventos `OrderPlacedEvent`, simula envío de notificación de recepción (log estructurado) |
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
    private Boolean active;   // soft delete: false lo retira del catálogo
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
        // Mismo estado -> permitido como operación idempotente
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

## 4. Puertos de dominio (interfaces definidas en `domain/model`, implementadas en `infrastructure`)

### 4.0 Convención de nombres para puertos

Aunque todos estos contratos son puertos en términos de Clean Architecture, se usa una convención semántica para evitar nombres artificiales:

- Usar sufijo `Repository` cuando el puerto representa persistencia principal de un agregado en la fuente de verdad. Ejemplos: `UserRepository`, `ProductRepository`, `OrderRepository`.
- Usar sufijo `Port` cuando el puerto representa una capacidad técnica o secundaria que no es el repositorio principal del agregado. Ejemplos: `ProductCachePort`, `TransactionPort`.
- Usar nombres por rol cuando expresan mejor la intención del puerto que un sufijo genérico. Ejemplo: `OrderEventPublisher`, porque su responsabilidad es publicar eventos, no persistir entidades.
- Mantener esta regla para nuevas HUs: no forzar todo a `Repository` si no representa persistencia de agregados, y no renombrar repositories generados por el scaffold si ya expresan correctamente el rol de persistencia.

```java
// Puertos de persistencia
public interface UserRepository {
    Mono<User> save(User user);
    Mono<User> findById(UUID id);
    Mono<Boolean> existsByEmail(Email email);
}

public interface ProductRepository {
    Mono<Product> save(Product product);
    Mono<Product> findById(UUID id); // solo productos activos para operaciones de catálogo
    Flux<Product> findAll();         // solo productos activos
    Mono<Boolean> decrementStockIfAvailable(UUID productId, int quantity);
    // ^ UPDATE condicional atómico: WHERE id=? AND active=true AND stock >= ? -> retorna filas afectadas
    Mono<Boolean> incrementStock(UUID productId, int quantity);
}

public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(UUID id);
    Flux<Order> findByUserId(UUID userId);
    Mono<Order> updateStatus(Order order);
}

// Puerto de cache
public interface ProductCachePort {
    Mono<Void> put(Product product);          // write-through
    Mono<Void> evict(UUID productId);         // borra product:{id} y remueve products:all
    Flux<Product> getAll();
    Mono<Void> putAll(List<Product> products); // repoblado read-through
}

// Puerto de eventos/notificación
public interface OrderEventPublisher {
    void publishOrderPlaced(OrderPlacedEvent event);
    void publishOrderCompleted(OrderCompletedEvent event);
}

// Puerto transaccional
public interface TransactionPort {
    <T> Mono<T> transactional(Mono<T> publisher);
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
| `PlaceOrderUseCase` | `UserRepository`, `ProductRepository`, `ProductCachePort`, `OrderRepository`, `OrderEventPublisher`, `TransactionPort` | Valida usuario existe → valida cada producto existe → `decrementStockIfAvailable` por cada ítem dentro de transacción → guarda `Order` en `PENDING` → confirma Postgres → refresca cache de productos afectados → publica `OrderPlacedEvent` |
| `GetOrderUseCase` | `OrderRepository` | Busca por ID → `OrderNotFoundException` si no existe |
| `UpdateOrderStatusUseCase` | `OrderRepository`, `ProductRepository`, `ProductCachePort`, `OrderEventPublisher`, `TransactionPort` | Busca pedido → valida transición con `OrderStatus.canTransitionTo` → si es a `CANCELLED`, repone stock (`incrementStock` por cada ítem) dentro de transacción y refresca cache → si es a `COMPLETED`, publica `OrderCompletedEvent` después de confirmar |
| `GetUserOrdersUseCase` *(bonus)* | `UserRepository`, `OrderRepository` | Valida usuario existe → retorna `Flux<Order>` con detalle de items; si no tiene pedidos retorna vacío |

**Nota de atomicidad en `PlaceOrderUseCase`:** usar un puerto transaccional implementado con `TransactionalOperator` de Spring/R2DBC para envolver la secuencia de descuentos + guardado del pedido. Si cualquier producto falla por inexistente o stock insuficiente, toda la operación de Postgres hace rollback — no debe quedar un pedido con descuentos parciales. Redis y la notificación quedan fuera de la transacción de base de datos: cache se refresca después de confirmar Postgres y la notificación se emite como evento desacoplado.

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

**`GET /products`**
```json
// Response 200
{
  "data": [
    {
      "id": "uuid",
      "name": "Mouse inalámbrico",
      "description": "...",
      "price": 25000,
      "stock": 100
    }
  ],
  "meta": {
    "path": "/products",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
```

US-004 usa read-through fallback dentro de `ListProductsUseCase`: intenta leer Redis por `products:all` + `product:{id}`; si Redis está vacío, incompleto o no disponible para lectura, lee Postgres. Cuando lee Postgres, intenta repoblar Redis sin fallar la request si la repoblación de cache falla. Si Postgres no tiene productos, responde `200 OK` con `data: []`.

**`PUT /products/{id}`** → mismo body que POST, `200` actualizado, `404` si no existe.

US-005 usa write-through secuencial dentro de `UpdateProductUseCase`: valida que el producto exista en Postgres, guarda los nuevos datos mediante `ProductRepository` y solo después actualiza Redis mediante `ProductCachePort`. Si Postgres falla, Redis no se actualiza. Si Redis falla después de confirmar Postgres, la API responde `503 Service Unavailable` con el envelope estándar.

**`DELETE /products/{id}`** → `204`, `404` si no existe.

US-006 usa soft delete por regla de negocio: no elimina físicamente el registro para preservar integridad referencial con pedidos históricos futuros (`order_items`). `DeleteProductUseCase` busca el producto activo, guarda `active=false` en Postgres y solo después ejecuta `ProductCachePort.evict(id)`, que borra `product:{id}` y remueve el id de `products:all`. Si Postgres falla, Redis no se toca. Si Redis falla después de confirmar Postgres, la API responde `503 Service Unavailable` con el envelope estándar.

### 6.3 Orders

**`POST /orders`**
```json
// Request
{
  "userId": "uuid",
  "items": [
    { "productId": "uuid", "quantity": 2 }
  ]
}
// Response 202 Accepted (async: notificación se procesa después)
{
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "status": "PENDING",
    "createdAt": "2026-08-16T00:00:00Z",
    "items": [
      {
        "productId": "uuid",
        "quantity": 2,
        "unitPriceAtOrderTime": 25000
      }
    ]
  },
  "meta": {
    "path": "/orders",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
// Response 409 (stock insuficiente)
{
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "Product {id} does not have enough stock for quantity {quantity}",
    "status": 409,
    "path": "/orders",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
```

Respuestas mínimas profesionales cubiertas para `POST /orders`:

| Status | Caso |
|---|---|
| `202 Accepted` | Pedido creado en estado `PENDING`; notificación emitida de forma asíncrona |
| `400 Bad Request` | Body inválido, body vacío, `userId`/`productId` no UUID, lista vacía o `quantity <= 0` |
| `404 Not Found` | Usuario inexistente o producto inexistente/inactivo |
| `409 Conflict` | Stock insuficiente |
| `415 Unsupported Media Type` | `Content-Type` diferente de `application/json` |
| `503 Service Unavailable` | Persistencia temporalmente no disponible |
| `500 Internal Server Error` | Fallback no controlado |

**`GET /orders/{id}`**
```json
// Response 200
{
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "status": "PENDING",
    "createdAt": "2026-08-16T00:00:00Z",
    "items": [
      {
        "productId": "uuid",
        "quantity": 2,
        "unitPriceAtOrderTime": 25000
      }
    ]
  },
  "meta": {
    "path": "/orders/{id}",
    "timestamp": "2026-08-16T00:00:00Z"
  }
}
// Response 404
{
  "error": {
    "code": "ORDER_NOT_FOUND",
    "message": "Order with id {id} was not found",
    "status": 404,
    "path": "/orders/{id}",
    "timestamp": "2026-08-16T00:00:00Z",
    "details": []
  }
}
```

Respuestas mínimas profesionales cubiertas para `GET /orders/{id}`:

| Status | Caso |
|---|---|
| `200 OK` | Pedido encontrado con `items` |
| `400 Bad Request` | `id` con formato inválido, no UUID |
| `404 Not Found` | Pedido inexistente |
| `503 Service Unavailable` | Persistencia temporalmente no disponible |
| `500 Internal Server Error` | Fallback no controlado |

**`PUT /orders/{id}/status`**
```json
// Request
{ "status": "PROCESSING" }
// Response 200
{
  "data": {
    "id": "uuid",
    "userId": "uuid",
    "status": "PROCESSING",
    "createdAt": "2026-08-17T12:00:00Z",
    "items": [
      { "productId": "uuid", "quantity": 2, "unitPriceAtOrderTime": 25000.00 }
    ]
  },
  "meta": { "path": "/orders/{id}/status", "timestamp": "2026-08-17T12:00:00Z" }
}
// Response 400 (transición inválida)
{
  "error": {
    "code": "INVALID_ORDER_STATUS_TRANSITION",
    "message": "Order status cannot transition from COMPLETED to PENDING",
    "status": 400
  }
}
```

Respuestas mínimas profesionales cubiertas para `PUT /orders/{id}/status`:

| Status | Caso |
|---|---|
| `200 OK` | Estado actualizado o mismo estado idempotente |
| `400 Bad Request` | `id` inválido, body inválido, status no soportado o transición inválida |
| `404 Not Found` | Pedido inexistente |
| `503 Service Unavailable` | Persistencia temporalmente no disponible |
| `500 Internal Server Error` | Fallback no controlado |

**`GET /users/{id}/orders`** (Bonus)
```json
// Response 200
{
  "data": [
    {
      "id": "uuid",
      "userId": "uuid",
      "status": "PENDING",
      "createdAt": "2026-08-17T12:00:00Z",
      "items": [
        { "productId": "uuid", "quantity": 2, "unitPriceAtOrderTime": 25000.00 }
      ]
    }
  ],
  "meta": { "path": "/users/{id}/orders", "timestamp": "2026-08-17T12:00:00Z" }
}
```

Respuestas mínimas profesionales cubiertas para `GET /users/{id}/orders`:

| Status | Caso |
|---|---|
| `200 OK` | Usuario existente; retorna lista de pedidos o lista vacía |
| `400 Bad Request` | `id` inválido, no UUID |
| `404 Not Found` | Usuario inexistente |
| `503 Service Unavailable` | Persistencia temporalmente no disponible |
| `500 Internal Server Error` | Fallback no controlado |

No se implementa paginación por alcance de la prueba. Evolución recomendada: paginación por cursor o `page/size` con orden estable por `createdAt`.

### 6.4 Auth (Bonus — JWT)

**`POST /auth/token`**
```json
// Request
{ "userId": "uuid" }
// Response 200
{
  "data": {
    "token": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresInSeconds": 3600
  },
  "meta": { "path": "/auth/token", "timestamp": "2026-08-17T12:00:00Z" }
}
```

También se acepta request por email:

```json
{ "email": "demo.user@example.com" }
```

Reglas:

- El request debe incluir exactamente un identificador: `userId` o `email`.
- `userId` inexistente o `email` inexistente retorna `404 Not Found`.
- `email` con formato inválido retorna `400 Bad Request`.
- Token emitido con firma local `HS256`, `sub=userId`, claims `userId`, `email`, `azp` y `roles`.

Endpoints protegidos (requieren `Authorization: Bearer {token}`): operaciones de escritura operativas (`POST`, `PUT`, `DELETE`). `POST /users` queda público para registro. `POST /auth/token` queda público para emisión del token simplificado. Los `GET` quedan abiertos salvo que una HU indique lo contrario.

Matriz de autenticación actual:

| Endpoint | Auth |
|---|---|
| `POST /users` | Público |
| `GET /users/{id}` | Público |
| `GET /users/{id}/orders` | Público |
| `POST /products` | Bearer JWT |
| `GET /products` | Público |
| `PUT /products/{id}` | Bearer JWT |
| `DELETE /products/{id}` | Bearer JWT |
| `POST /orders` | Bearer JWT |
| `GET /orders/{id}` | Público |
| `PUT /orders/{id}/status` | Bearer JWT |
| `POST /auth/token` | Público |

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
         AND active = TRUE
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
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_at_order_time NUMERIC(12, 2) NOT NULL CHECK (unit_price_at_order_time > 0)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id);
```

**Query crítica (concurrencia de stock):**
```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND active = TRUE AND stock >= :quantity;
-- Verificar rowsUpdated == 0 -> stock insuficiente -> abortar
```

### 7.2 US-008 — Concurrencia de stock

US-008 se implementa con el `UPDATE` condicional atómico anterior, usado desde `ProductReactiveRepository.decrementStockIfAvailable`. No se usa el patrón vulnerable `SELECT stock` → validar en código → `UPDATE`.

La verificación explícita vive en `ProductStockConcurrencyIntegrationTest` y usa PostgreSQL real con Testcontainers:

- stock inicial `1`, dos descuentos concurrentes de `1` unidad → exactamente un intento retorna éxito y el otro falla.
- stock inicial `3`, diez descuentos concurrentes de `1` unidad → exactamente tres intentos retornan éxito, siete fallan y el stock final queda en `0`.
- en ningún caso el stock puede quedar negativo por el `CHECK (stock >= 0)` y, principalmente, porque el `WHERE stock >= :quantity` hace que la actualización sea atómica a nivel de fila.

---

## 8. Estructura de claves Redis

```
product:{productId}   -> JSON serializado del producto {id, name, description, price, stock}
products:all          -> Set de productId usados para reconstruir el listado sin KEYS *
```

US-003 escribe `product:{id}` y registra el id en `products:all`. US-004 combina read-through: consultar Redis primero; si la entrada individual o el índice de catálogo no existe o está incompleto, leer Postgres, responder y repoblar Redis. US-005 actualiza la clave individual después de confirmar Postgres. US-006 invalida cache por soft delete. US-007 y US-010 refrescan las claves afectadas después de confirmar Postgres para que el catálogo conserve `products:all` completo y `product:{id}` actualizado.

TTL recomendado para catálogo:

- Usar TTL en productos individuales para evitar datos indefinidamente obsoletos.
- Aplicar TTLs escalonados con jitter, por ejemplo 10-15 minutos por producto, para evitar expiración masiva simultánea.
- Evaluar un job programado que refresque claves cercanas a expirar desde Postgres. Ese job no reemplaza la fuente de verdad; solo reduce misses y latencia.
- En escrituras (`POST`, `PUT`, `DELETE` y cambios de stock por pedidos) mantener write-through/refresh/evict según el caso: creación/actualización refrescan, soft delete elimina del índice, pedidos refrescan stock.

---

## 9. Mecanismo async (eventos en memoria)

Implementado en US-007 y extendido en US-010: la operación crítica de negocio es síncrona y transaccional
validar usuario, validar productos, descontar stock y persistir la orden. Solo la notificación
de recepción/completado del pedido es asíncrona.

```java
// OrderEventPublisherAdapter.java (infrastructure/driven-adapters/notification)
private final Sinks.Many<OrderPlacedEvent> orderPlacedSink;
private final Sinks.Many<OrderCompletedEvent> orderCompletedSink;

public void publishOrderPlaced(OrderPlacedEvent event) {
    orderPlacedSink.tryEmitNext(event);
}

public void publishOrderCompleted(OrderCompletedEvent event) {
    orderCompletedSink.tryEmitNext(event);
}
// listener suscrito en el arranque de la app (applications/app-service) consume el Flux
// y ejecuta el "envío" de notificación (log estructurado) sin bloquear el hilo de la request HTTP
```

En producción, este `Sinks.Many` se reemplazaría por un publisher real hacia AWS SQS/SNS o EventBridge — el principio de desacople (responder rápido, notificar aparte) es el mismo.

Si queda tiempo dentro de la prueba técnica, se puede evolucionar la simulación en memoria hacia una cola local con LocalStack. La opción preferida sería SQS cuando se quiera demostrar cola durable, mensajes pendientes, retries y consumo explícito; SNS aplica mejor si se quiere demostrar fan-out/publicación a varios suscriptores. Para US-007 actual, SQS local sería la alternativa más cercana a "pedido recibido → mensaje encolado → listener lo consume".

Resumen de la decisión US-007:

- `OrderPlacedEvent` representa el hecho de dominio "pedido recibido/creado".
- `Sinks.Many<OrderPlacedEvent>` funcionará como bus de eventos en memoria para la prueba técnica.
- `OrderEventPublisher` publicará el evento después de persistir la orden.
- `OrderEventListener` escuchará el flujo y simulará la notificación con log estructurado.
- No se usará otro microservicio ni AWS real en el alcance base de esta prueba; si se extiende el alcance, LocalStack SQS sería el siguiente paso recomendado para visualizar mensajes encolados localmente.

---

## 10. Seguridad (JWT)

- Implementación: Spring Security Reactive OAuth2 Resource Server + Nimbus (`JwtEncoder` / `NimbusReactiveJwtDecoder`) con firma simétrica `HS256`.
- `SecurityWebFilterChain` protegiendo operaciones de escritura (`POST`, `PUT`, `DELETE`) según alcance de US-012.
- `POST /users` queda público para permitir registro de usuarios.
- `POST /auth/token` queda público para emitir el JWT simplificado.
- Los `GET` quedan públicos salvo que una HU indique lo contrario.
- Escrituras sin token o con token inválido retornan `401 Unauthorized` con el envelope estándar de error.
- Secret de firma vía variable de entorno (`JWT_SECRET`), nunca hardcoded — coherente con buenas prácticas ya aplicadas en tu experiencia (Cognito/OAuth2 en AB InBev).
- `JWT_SECRET` debe tener mínimo 32 bytes para cumplir con `HS256`.
- El enunciado no define password ni credenciales; por alcance, se valida que el usuario exista por `userId` o `email` y se emite token. En producción esto debería reemplazarse por un flujo de autenticación real o IdP externo.

---

## 11. Testing

| Tipo | Alcance | Herramientas |
|---|---|---|
| Unitario | Todos los use cases de `domain/usecase`, con puertos mockeados | JUnit 5, Mockito, `StepVerifier` (Reactor Test) |
| Entry point | Contratos HTTP, status codes, response envelope, error envelope y validaciones de request | `WebTestClient` |
| Adapter | Mapeo de entidades, errores de persistencia y constraints | JUnit 5, Mockito, `StepVerifier` |
| Integración | `POST /users` (incl. email duplicado), `POST /orders` (incl. stock insuficiente → 409), US-008 concurrencia de stock, `PUT /orders/{id}/status` | `@SpringBootTest`, `WebTestClient`, Testcontainers (Postgres + Redis reales) |

---

## 12. Docker

US-013 queda cubierta con `docker-compose.yml` y `deployment/Dockerfile`.

Servicios:

| Servicio | Imagen / build | Puerto local | Responsabilidad |
|---|---|---|---|
| `app` | build local con `deployment/Dockerfile` | `${SERVER_PORT:-8080}:8080` | Spring Boot WebFlux |
| `postgres` | `postgres:16-alpine` | `${POSTGRES_PORT:-5432}:5432` | Base de datos principal |
| `redis` | `redis:7-alpine` | `${REDIS_PORT:-6379}:6379` | Cache de catálogo |

Arranque:

```bash
cp .env.example .env
docker compose up --build
```

Compatibilidad con Docker Compose legacy:

```bash
cp .env.example .env
docker-compose up --build
```

Healthchecks:

- `postgres`: `pg_isready` contra `${POSTGRES_DB}` y `${POSTGRES_USER}`.
- `redis`: `redis-cli ping`.
- `app`: depende de `postgres` y `redis` con `condition: service_healthy`, evitando arrancar antes de que las dependencias estén disponibles; además expone healthcheck HTTP sobre `/actuator/health`.

Tolerancia de arranque:

- `app` usa `restart: on-failure:3` para recuperarse de fallos transitorios de inicialización, especialmente en el primer arranque después de `docker compose down -v`, cuando Postgres crea el volumen y reinicia internamente su servidor.

Variables relevantes:

- `.env.example`: plantilla versionada para que el evaluador pueda crear el ambiente local.
- `.env`: valores para Docker Compose; usa hosts internos `postgres` y `redis`; no se versiona.
- `oms.env`: valores para IntelliJ/local; usa `localhost`.
- `SPRING_R2DBC_URL`, `SPRING_R2DBC_USERNAME`, `SPRING_R2DBC_PASSWORD`: conexión R2DBC a Postgres.
- `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`: conexión a Redis.
- `JWT_SECRET`: secreto local para firma `HS256`, mínimo 32 bytes.
- `JWT_EXPIRATION_MINUTES`: expiración de tokens emitidos.

El `Dockerfile` es multi-stage:

- Stage `builder`: `eclipse-temurin:21-jdk-alpine`, copia primero los scripts de Gradle y `build.gradle` de los módulos para aprovechar cache de capas en la resolución de dependencias; luego copia el código y genera el `bootJar`.
- Stage final: `eclipse-temurin:21-jre-alpine`, copia solo el JAR ejecutable, usa usuario no root y expone puerto `8080`.

Nota: el build de la imagen genera el artefacto ejecutable. La ejecución completa de tests y PIT se valida fuera del Docker build con Gradle, porque parte de los tests de integración usan Testcontainers/Docker y no deben depender del entorno interno del build de imagen.

---

## 13. Orden de implementación recomendado

1. `domain/model` — entidades, value objects, `OrderStatus` con transiciones
2. `domain/usecase` — puertos (interfaces) + casos de uso de Usuarios y Productos primero (más simples, validan que el scaffold responde)
3. `infrastructure/driven-adapters/r2dbc-postgresql` — implementación de repos + DDL
4. `infrastructure/entry-points/reactive-web` — handlers/routers funcionales de Usuarios y Productos
5. `infrastructure/driven-adapters/redis` — cache write-through/read-through
6. `domain/usecase` — `PlaceOrderUseCase` + `UpdateOrderStatusUseCase` (la pieza más compleja: concurrencia + async)
7. `infrastructure/driven-adapters/notification` — mecanismo de eventos
8. Seguridad JWT
9. Docker
10. Testing (idealmente en paralelo a cada paso anterior, no todo al final)
11. README final con assumptions + development approach

---

## 14. Registro de decisiones (sincronizar con Assumptions del backlog)

Este documento se actualiza junto con la sección "Assumptions consolidadas" de `BACKLOG.md` a medida que surjan nuevas decisiones durante la implementación.
