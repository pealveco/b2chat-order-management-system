# Backlog — B2Chat Practical Test: E-Commerce Order Management System

**Stack:** Java + Spring Boot WebFlux (reactivo) + R2DBC + PostgreSQL + Redis (write-through + read-through) + JWT + Docker
**Arquitectura:** Clean Architecture — Scaffold Bancolombia
**Entrega:** Repositorio GitHub → fredy@b2chat.io | Deadline: 18/08/2026

---

## Épica 1 — Gestión de Usuarios

### US-001: Registrar usuario
**Como** consumidor de la API, **quiero** registrar un usuario **para** poder asociarle pedidos.

**Criterios de aceptación:**
- `POST /users` recibe `email`, `name`, `address`.
- `email` es único — si ya existe, responde `409 Conflict` con mensaje claro.
- Validación de formato de `email` — inválido → `400 Bad Request`.
- Campos obligatorios (`name`, `address`) no vacíos.
- Respuesta `201 Created` con el recurso creado, incluyendo `id` generado.

**Notas técnicas:**
- `domain/model`: `User` (id, email, name, address) + value object `Email` con validación en el constructor.
- `domain/usecase`: `RegisterUserUseCase`.
- `infrastructure/driven-adapters/r2dbc-postgresql`: tabla `users` (id UUID PK, email UNIQUE, name, address).
- Unicidad de email: constraint UNIQUE en BD + manejo de `DataIntegrityViolationException` mapeado a 409.

**Prioridad:** Alta | **Estimado:** 3 pts

---

### US-002: Consultar usuario por ID
**Como** consumidor de la API, **quiero** obtener los datos de un usuario por su ID **para** verificar su información.

**Criterios de aceptación:**
- `GET /users/{id}` retorna el usuario si existe.
- ID inexistente → `404 Not Found`.
- ID con formato inválido (no UUID) → `400 Bad Request`.

**Notas técnicas:**
- `domain/usecase`: `GetUserUseCase`.
- Reutiliza el mismo `UserRepository` (puerto de dominio) que US-001.

**Prioridad:** Alta | **Estimado:** 1 pt

---

---

---

## Épica 2 — Gestión de Productos

### US-003: Crear producto
**Como** administrador, **quiero** registrar un producto **para** que esté disponible en el catálogo.

**Criterios de aceptación:**
- `POST /products` recibe `name`, `description`, `price`, `stock`.
- `price` > 0, `stock` >= 0 — validación, si no `400 Bad Request`.
- Respuesta `201 Created`.
- **Write-through:** al crear, se persiste en Postgres y se escribe simultáneamente en Redis (clave `product:{id}`), dentro del mismo use case.

**Notas técnicas:**
- `domain/model`: `Product` (id, name, description, `Money` price, stock).
- `domain/usecase`: `CreateProductUseCase` — orquesta puerto `ProductRepository` (Postgres) + puerto `ProductCachePort` (Redis), ambos como dependencias inyectadas del use case, no acopladas entre sí.

**Prioridad:** Alta | **Estimado:** 3 pts

---

### US-004: Listar productos (catálogo)
**Como** consumidor de la API, **quiero** listar todos los productos **para** ver el catálogo disponible.

**Criterios de aceptación:**
- `GET /products` retorna la lista completa.
- **Cache write-through con fallback read-through:** intenta leer de Redis primero; si está vacío (cache frío, ej. arranque de la app, o evicción por TTL/memoria), lee de Postgres y **puebla** Redis para futuras lecturas — el write-through garantiza que toda escritura propia (US-003/005/006) deja el cache consistente de inmediato; el read-through cubre el caso en que el dato nunca estuvo o fue evictado.
- Respuesta `200 OK`, lista vacía si no hay productos (no error).

**Notas técnicas:**
- `domain/usecase`: `ListProductsUseCase`.
- `infrastructure/driven-adapters/redis`: estructura recomendada — Hash o String serializado por producto (`product:{id}`) + Set `products:all` con los IDs, para poder reconstruir la lista sin `KEYS *` (antipatrón en Redis productivo).
- TTL con jitter (ej. 1h ± unos minutos aleatorios por clave) solo como red de seguridad ante evicción por memoria, no como mecanismo principal de frescura — la frescura la garantiza el write-through.

**Estrategia de caché — análisis completo (para discutir en la sesión técnica):**

Write-through resuelve consistencia en escritura, pero no disponibilidad del dato en cache ante: (1) cache frío / miss real (arranque, Redis reiniciado, dato nunca escrito), y (2) evicción por TTL o política de memoria (ej. `allkeys-lru`). El diseño completo para un entorno productivo de alto tráfico combina 4 capas:

| Mecanismo | Rol |
|---|---|
| Write-through | Consistencia inmediata en toda escritura propia — fuente principal de frescura |
| TTL con jitter | Red de seguridad; el jitter (TTL escalonado por clave) evita que todas las claves expiren simultáneamente y generen thundering herd contra Postgres |
| Job de refresh-ahead | Scheduler que detecta claves con TTL restante bajo un umbral (ej. <20%) y las refresca proactivamente antes de que expiren, evitando misses reactivos |
| Read-through fallback | Última línea de defensa ante miss real — lee Postgres y repuebla sin fallar la request |

**Decisión de alcance para esta prueba:** se implementan write-through + read-through fallback (suficiente para demostrar consistencia y disponibilidad correctas). TTL con jitter + job de refresh-ahead **no se implementan** por alcance/tiempo del ejercicio, pero se documentan explícitamente en el README como la evolución natural del diseño para un entorno de mayor tráfico — ver Assumption #7.

**Prioridad:** Alta | **Estimado:** 5 pts

---

### US-005: Actualizar producto
**Como** administrador, **quiero** actualizar los detalles de un producto **para** mantener el catálogo correcto.

**Criterios de aceptación:**
- `PUT /products/{id}` actualiza `name`, `description`, `price`, `stock`.
- ID inexistente → `404 Not Found`.
- **Write-through:** actualiza Postgres y Redis en la misma operación (mismo use case, no evento asíncrono separado — así se garantiza consistencia inmediata).
- Respuesta `200 OK` con el recurso actualizado.

**Notas técnicas:**
- `domain/usecase`: `UpdateProductUseCase`.
- Importante: la escritura a Redis debe ocurrir **después** de confirmar la escritura en Postgres (orden secuencial dentro del flujo reactivo), no en paralelo — si Postgres falla, Redis no debe actualizarse con datos que no llegaron a persistir.

**Prioridad:** Media | **Estimado:** 2 pts

---

### US-006: Eliminar producto
**Como** administrador, **quiero** eliminar un producto **para** retirarlo del catálogo.

**Criterios de aceptación:**
- `DELETE /products/{id}` elimina el producto.
- ID inexistente → `404 Not Found`.
- **Write-through:** elimina de Postgres y de Redis (clave individual + removido del Set `products:all`) en la misma operación.
- Respuesta `204 No Content`.

**Notas técnicas:**
- `domain/usecase`: `DeleteProductUseCase`.
- Considerar regla de negocio (asumption a documentar): ¿se permite eliminar un producto con pedidos históricos asociados? → Recomendación: soft delete (flag `active=false`) en vez de DELETE físico, para no romper integridad referencial con `order_items`. **Documentar esta decisión como assumption.**

**Prioridad:** Media | **Estimado:** 2 pts

---

---

---

## Épica 3 — Gestión de Pedidos (Order Management)

### US-007: Crear pedido (async + notificación de recepción)
**Como** usuario, **quiero** realizar un pedido de productos **para** comprarlos, recibiendo confirmación inmediata sin esperar el procesamiento completo.

**Criterios de aceptación:**
- `POST /orders` recibe `userId`, lista de `{productId, quantity}`.
- Valida existencia de `userId` — inexistente → `404`.
- Valida existencia de cada `productId` — inexistente → `404`.
- Valida stock suficiente para cada producto — insuficiente → `409 Conflict` (no se crea el pedido, no se descuenta nada — operación atómica o ninguna).
- Si todo es válido: descuenta stock, crea el pedido en estado `Pending`, responde de inmediato (`202 Accepted`, no `201`, porque el procesamiento de notificación es async).
- **Manejo asíncrono:** la creación del pedido en sí (validación + descuento de stock + persistencia) es síncrona y transaccional (debe completarse antes de responder, porque el descuento de stock no puede ser "eventual"). Lo asíncrono es específicamente el **envío de la notificación** de recepción — se emite un evento `OrderPlacedEvent` a un `Sinks.Many` (Reactor) inmediatamente después de persistir, y un listener separado procesa el envío de la notificación sin bloquear la respuesta HTTP.
- Notificación de recepción: puede ser tan simple como un log estructurado o un stub de "email service" — lo importante es demostrar el patrón de desacople, no la integración real con un proveedor de email.

**Notas técnicas:**
- `domain/usecase`: `PlaceOrderUseCase` — orquesta: validar usuario → validar productos → validar y descontar stock (ver US-008 para el detalle de concurrencia) → persistir orden + order_items → emitir evento.
- `infrastructure/driven-adapters/notification` (adapter custom, no viene en el scaffold por defecto): `OrderEventListener` suscrito al `Sinks.Many<OrderPlacedEvent>`.
- **Assumption a documentar:** en producción este patrón se reemplazaría por AWS SQS/SNS o EventBridge — aquí se simula en memoria dado el alcance de la prueba, manteniendo el mismo principio de desacople entre "confirmar la operación" y "notificar el resultado".

**Prioridad:** Alta (crítica) | **Estimado:** 8 pts

---

### US-008: Descuento de stock sin condiciones de carrera
**Como** sistema, **quiero** descontar el stock de forma atómica **para** evitar sobreventa bajo pedidos concurrentes.

**Criterios de aceptación:**
- Bajo múltiples requests concurrentes para el mismo producto con stock limitado, nunca se debe permitir que el stock quede negativo.
- Si dos pedidos compiten por el último ítem en stock, exactamente uno debe tener éxito y el otro debe fallar con `409 Conflict`.

**Notas técnicas:**
- **No usar patrón leer-luego-escribir** (`SELECT stock` → validar en código → `UPDATE`), es vulnerable a race conditions incluso en R2DBC.
- Usar **UPDATE condicional atómico** a nivel de query: `UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty`, y verificar el número de filas afectadas (`rowsUpdated == 0` → stock insuficiente → abortar y responder 409).
- Este es un punto que vale la pena mencionar explícitamente en la sesión técnica con B2Chat, incluso si no preguntan — demuestra criterio senior en concurrencia.

**Prioridad:** Alta (crítica) | **Estimado:** 3 pts (puede integrarse dentro de US-007, listado aparte por su importancia técnica)

---

### US-009: Consultar pedido por ID
**Como** usuario, **quiero** consultar el detalle de un pedido **para** verificar su estado y contenido.

**Criterios de aceptación:**
- `GET /orders/{id}` retorna el pedido con su lista de productos/cantidades y estado actual.
- ID inexistente → `404 Not Found`.

**Notas técnicas:**
- `domain/usecase`: `GetOrderUseCase`.
- Requiere join reactivo entre `orders` y `order_items` (y opcionalmente `products` para enriquecer con nombre/precio al momento de la consulta).

**Prioridad:** Alta | **Estimado:** 2 pts

---

### US-010: Actualizar estado del pedido (+ notificación de completado — Bonus)
**Como** administrador, **quiero** actualizar el estado de un pedido **para** reflejar su progreso (Pending → Processing → Completed / Cancelled).

**Criterios de aceptación:**
- `PUT /orders/{id}/status` recibe el nuevo estado.
- Transiciones válidas únicamente (ej. no se puede pasar de `Completed` a `Pending`) — transición inválida → `400 Bad Request`.
- ID inexistente → `404 Not Found`.
- **Bonus:** si la transición es a `Completed`, emitir evento `OrderCompletedEvent` al mismo mecanismo de notificación async de US-007.
- Si la transición es a `Cancelled`, **assumption a documentar:** ¿se debe reponer el stock descontado? → Recomendación: sí, reponer stock automáticamente al cancelar, para mantener consistencia del inventario.

**Notas técnicas:**
- `domain/usecase`: `UpdateOrderStatusUseCase`.
- Modelar las transiciones válidas explícitamente en el dominio (ej. un método `OrderStatus.canTransitionTo(newStatus)`), no dejarlo como validación ad-hoc en el use case.

**Prioridad:** Alta | **Estimado:** 3 pts

---

### US-011: Listar pedidos de un usuario (Bonus)
**Como** usuario, **quiero** ver todos mis pedidos con su detalle **para** revisar mi historial de compras.

**Criterios de aceptación:**
- `GET /users/{id}/orders` retorna todos los pedidos del usuario, cada uno con su lista de productos, cantidades y estado.
- Usuario sin pedidos → lista vacía, no error.
- Usuario inexistente → `404 Not Found`.

**Notas técnicas:**
- `domain/usecase`: `GetUserOrdersUseCase`.
- Evaluar paginación si el alcance del ejercicio lo justifica (probablemente no es necesario para esta prueba, pero vale mencionarlo como posible mejora futura en el README).

**Prioridad:** Media (Bonus) | **Estimado:** 3 pts

---

---

---

## Épica 4 — Seguridad (Bonus)

### US-012: Autenticación JWT
**Como** consumidor de la API, **quiero** autenticarme con JWT **para** que los endpoints estén protegidos.

**Criterios de aceptación:**
- `POST /auth/token` recibe `userId` o `email`, retorna un JWT firmado.
- Endpoints de escritura (`POST`, `PUT`, `DELETE`) requieren `Authorization: Bearer {token}` válido — sin token o inválido → `401 Unauthorized`.
- **Assumption a documentar:** el enunciado no especifica un flujo de credenciales (password), por lo que se implementa un endpoint simplificado de emisión de token basado en `userId` existente, no un login completo con contraseña — se documenta explícitamente como decisión de alcance.

**Notas técnicas:**
- `infrastructure/entry-points/reactive-web`: `SecurityWebFilterChain` con `ReactiveJwtDecoder` o filtro custom si se prefiere no traer todo Spring Security OAuth2 Resource Server por simplicidad.
- Librería sugerida: `io.jsonwebtoken:jjwt` para firmar/validar si se opta por implementación manual ligera.

**Prioridad:** Media (Bonus) | **Estimado:** 5 pts

---

---

---

## Épica 5 — Infraestructura (Bonus)

### US-013: Dockerización
**Como** evaluador, **quiero** levantar la aplicación con un solo comando **para** probarla sin configurar el entorno manualmente.

**Criterios de aceptación:**
- `docker-compose up` levanta: `app` (Spring Boot), `postgres`, `redis`.
- La app se conecta correctamente a ambos servicios usando variables de entorno (no hardcoded).
- README incluye instrucciones exactas de arranque.

**Notas técnicas:**
- `Dockerfile` multi-stage: stage de build con Gradle (`./gradlew build`), stage final solo con el JAR sobre una imagen JRE liviana (ej. `eclipse-temurin:21-jre-alpine`).
- `docker-compose.yml` con healthchecks para Postgres/Redis antes de levantar `app` (evita fallos de arranque por orden de servicios).

**Prioridad:** Media (Bonus) | **Estimado:** 3 pts

---

---

---

## Épica 6 — Testing

### US-014: Pruebas unitarias de use cases
**Como** desarrollador, **quiero** pruebas unitarias de la lógica de dominio **para** garantizar corrección sin depender de infraestructura.

**Criterios de aceptación:**
- Cobertura de los use cases críticos: `RegisterUserUseCase`, `PlaceOrderUseCase` (incluyendo el caso de stock insuficiente), `UpdateOrderStatusUseCase` (transiciones válidas/inválidas), `UpdateProductUseCase`.
- Uso de mocks (Mockito) para los puertos (repositorios, cache, notificación) — el dominio se prueba aislado de infraestructura real.

**Notas técnicas:**
- JUnit 5 + Mockito + `StepVerifier` (Reactor Test) para validar flujos reactivos (`Mono`/`Flux`).

**Prioridad:** Alta | **Estimado:** 5 pts

---

### US-015: Pruebas de integración de APIs clave
**Como** desarrollador, **quiero** pruebas de integración **para** validar el comportamiento end-to-end de los endpoints críticos.

**Criterios de aceptación:**
- Cobertura mínima: creación de usuario (incluyendo caso de email duplicado), creación de pedido (incluyendo caso de stock insuficiente → 409), actualización de estado de pedido.
- Las pruebas corren contra instancias reales de Postgres y Redis (no mocks) para validar la integración real.

**Notas técnicas:**
- `Testcontainers` con contenedores de PostgreSQL y Redis — evita depender de servicios locales instalados manualmente y es reproducible en cualquier máquina/CI.
- `WebTestClient` (WebFlux) para probar los entry-points reactivos.

**Prioridad:** Alta | **Estimado:** 5 pts

---

---

---

## Épica 7 — Documentación

### US-016: README completo
**Como** evaluador, **quiero** un README claro **para** entender cómo levantar el proyecto y consumir la API sin ayuda adicional.

**Criterios de aceptación:**
- Instrucciones de setup y ejecución (local y con Docker).
- Listado de endpoints con ejemplos de request/response (curl o similar).
- Sección de **Assumptions** — documentando cada decisión de alcance no especificada explícitamente en el enunciado (ver lista consolidada abajo).
- Sección breve de **Development approach**, uso de Claude Code y Codex bajo un enfoque spec-driven (spec → arquitectura → implementación → validación de estructura con `./gradlew vs` del scaffold Bancolombia) — alineado con el requisito diferenciador.
- Sección de **Production considerations** (o dentro de Assumptions): explicar el diseño completo de caché (write-through + read-through implementados; TTL con jitter + job de refresh-ahead como evolución no implementada por alcance) — ver Assumption #7. No dejarlo implícito en el código; el evaluador debe ver que la decisión fue consciente, no una omisión.

**Prioridad:** Alta | **Estimado:** 2 pts

---

## Assumptions consolidadas (a mantener actualizadas durante el desarrollo)

> Esta sección se irá completando a medida que avancemos — cada decisión de diseño no especificada explícitamente en el enunciado debe quedar registrada aquí y trasladarse al README final.

1. **Caching:** se usa estrategia *write-through* (no cache-aside) para el catálogo de productos, garantizando que Redis nunca sirva datos desactualizados — toda escritura de producto actualiza Postgres y Redis en la misma operación.
2. **Notificaciones:** se simulan en memoria con un patrón productor/consumidor reactivo (`Sinks.Many`), representando el mismo principio de desacople que se usaría en producción con AWS SQS/SNS o EventBridge.
3. **Eliminación de productos:** se implementa soft delete (`active=false`) en lugar de DELETE físico, para preservar integridad referencial con pedidos históricos. *(Pendiente de confirmar si se implementa así o se documenta como alternativa considerada.)*
4. **Cancelación de pedidos:** al cancelar un pedido, se repone automáticamente el stock descontado.
5. **Autenticación JWT:** se implementa un endpoint simplificado de emisión de token basado en `userId`/`email` existente, sin flujo completo de credenciales/password, dado que el enunciado no lo especifica.
6. **Concurrencia en stock:** se usa UPDATE condicional atómico a nivel de base de datos (no lectura-luego-escritura en código) para evitar sobreventa bajo pedidos concurrentes.
7. **Estrategia de caché:** se implementa write-through (consistencia inmediata en escritura) + read-through fallback (disponibilidad ante miss real). No se implementan TTL con jitter ni job de refresh-ahead — se documentan como la evolución natural del diseño para un entorno de mayor tráfico, fuera del alcance de esta prueba por tiempo, pero conscientemente considerados y no omitidos por desconocimiento.
8. **Blocking calls detectados por las pruebas de integración (US-015):** al levantar el contexto completo de Spring Boot contra Postgres/Redis reales con BlockHound activo (por primera vez, ya que ningún test previo combinaba esas tres condiciones), aparecieron dos llamadas bloqueantes reales en hilos reactivos: lectura perezosa del schema SQL desde el classpath (`R2dbcSchemaInitializerConfig`) y apertura perezosa/bloqueante de la conexión reactiva compartida de Redis. Ambas se corrigieron (lectura eager a memoria; warm-up de la conexión reactiva en el arranque vía `RedisConnectionWarmupConfig`) sin cambiar comportamiento observable de la API.

---

## Resumen de estimación

| Épica | Story Points |
|---|---|
| Gestión de Usuarios | 4 |
| Gestión de Productos | 12 |
| Gestión de Pedidos | 16 |
| Seguridad (Bonus) | 5 |
| Infraestructura (Bonus) | 3 |
| Testing | 10 |
| Documentación | 2 |
| **Total** | **52 pts** |

**Orden de implementación sugerido:** Usuarios → Productos (con cache write-through) → Pedidos (incluye la pieza más compleja: async + concurrencia de stock) → JWT → Docker → Testing (en paralelo a cada épica funcional, no al final) → README.