# B2Chat — Order Management System

Backend de un sistema simplificado de gestión de pedidos de e-commerce, desarrollado como prueba técnica para el proceso de selección de Backend Developer en B2Chat.

**Estado del proyecto:** 🚧 En desarrollo — este README se actualiza progresivamente a medida que avanza la implementación.

---

## Stack Tecnológico

- **Lenguaje:** Java 21
- **Framework:** Spring Boot (WebFlux — programación reactiva)
- **Persistencia:** PostgreSQL vía R2DBC
- **Cache:** Redis (estrategia write-through + read-through)
- **Seguridad:** JWT
- **Arquitectura:** Clean Architecture — [Scaffold Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/)
- **Contenedores:** Docker / Docker Compose
- **Testing:** JUnit 5, Mockito, Reactor Test, Testcontainers

---

## Cómo ejecutar el proyecto

La aplicación se levanta junto con PostgreSQL y Redis usando Docker Compose. El archivo `.env` contiene valores de desarrollo local y no se versiona porque incluye credenciales.

```bash
docker compose up --build
```

Este comando crea y levanta:

- `app`: aplicación Spring Boot WebFlux, expuesta en `http://localhost:8080`.
- `postgres`: PostgreSQL 16, expuesto en `localhost:5432`, con volumen persistente `postgres_data`.
- `redis`: Redis 7, expuesto en `localhost:6379`.

`app` depende de los healthchecks de `postgres` y `redis`, por lo que no intenta arrancar hasta que ambos servicios estén disponibles.

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
| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/users` | Registrar un nuevo usuario |
| `GET` | `/users/{id}` | Obtener detalles de un usuario |
| `GET` | `/users/{id}/orders` | Listar pedidos de un usuario *(bonus)* |

### Products
| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/products` | Registrar un nuevo producto |
| `GET` | `/products` | Listar catálogo de productos |
| `PUT` | `/products/{id}` | Actualizar un producto |
| `DELETE` | `/products/{id}` | Eliminar un producto (soft delete) |

### Orders
| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/orders` | Crear un pedido (procesamiento asíncrono + notificación) |
| `GET` | `/orders/{id}` | Obtener detalle de un pedido |
| `PUT` | `/orders/{id}/status` | Actualizar estado de un pedido |

### Auth *(bonus)*
| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/auth/token` | Emitir token JWT |

> Ejemplos completos de request/response en [`SPEC.md`](./SPEC.md#6-contratos-de-api).

---

## Decisiones de diseño

### Manejo asíncrono de pedidos
La creación de un pedido (`POST /orders`) valida stock y persiste el pedido de forma síncrona y transaccional (no puede ser "eventual", ya que involucra descuento de inventario). Lo que se procesa de forma **asíncrona** es el envío de la notificación de recepción: se emite un evento en memoria (`Sinks.Many` de Project Reactor) inmediatamente después de persistir, y un listener independiente procesa la notificación sin bloquear la respuesta HTTP. El mismo mecanismo dispara la notificación de pedido completado (bonus).

En un entorno productivo, este mecanismo se reemplazaría por un publisher real hacia **AWS SQS/SNS o EventBridge** — el principio de desacople (responder rápido, notificar aparte) es el mismo que se aplicaría en producción.

### Concurrencia en descuento de stock
Para evitar sobreventa bajo pedidos concurrentes, el descuento de stock **no** usa el patrón leer-luego-escribir (vulnerable a condiciones de carrera incluso en R2DBC). En su lugar, se usa una actualización condicional atómica a nivel de base de datos:

```sql
UPDATE products SET stock = stock - :quantity
WHERE id = :productId AND stock >= :quantity;
```

Si el número de filas afectadas es 0, se interpreta como stock insuficiente y la operación se aborta.

### Estrategia de caché
Se implementa **write-through** (toda escritura de producto actualiza Postgres y Redis en la misma operación, garantizando consistencia inmediata) combinado con **read-through fallback** (si ocurre un miss real — cache frío en el arranque, o evicción — se lee de Postgres y se repuebla Redis).

Para un entorno de mayor tráfico, el diseño completo evolucionaría a incluir TTL con jitter (para evitar expiración simultánea de claves y *thundering herd*) y un job de *refresh-ahead* que renueve proactivamente las claves antes de vencer. Esto no se implementó en el alcance de esta prueba por tiempo, pero se documenta como la evolución natural del diseño — ver [Assumptions](#assumptions).

### Desarrollo asistido por IA (Spec-Driven Development)
Este proyecto se desarrolló usando **Claude Code** bajo un enfoque de Spec-Driven Development (SDD): definición de backlog (historias de usuario + criterios de aceptación) → especificación técnica (`SPEC.md`, contratos de API, modelo de datos, decisiones de arquitectura) → implementación guiada por esa especificación → validación de estructura con `./gradlew vs` del scaffold Bancolombia. Este proceso se mantuvo documentado y versionado a lo largo del desarrollo, no aplicado de forma ad-hoc.

---

## Assumptions

Decisiones de alcance no especificadas explícitamente en el enunciado de la prueba técnica:

1. **Caching:** estrategia write-through + read-through fallback (no cache-aside), para evitar servir datos desactualizados. TTL con jitter y job de refresh-ahead quedan documentados como evolución productiva no implementada por alcance/tiempo.
2. **Notificaciones:** simuladas en memoria con un patrón productor/consumidor reactivo, representando el mismo principio de desacople que se usaría en producción con AWS SQS/SNS o EventBridge.
3. **Eliminación de productos:** soft delete (`active=false`) en lugar de DELETE físico, para preservar integridad referencial con pedidos históricos.
4. **Cancelación de pedidos:** al cancelar un pedido, se repone automáticamente el stock descontado.
5. **Autenticación JWT:** endpoint simplificado de emisión de token basado en `userId`/`email` existente, sin flujo completo de credenciales/password, dado que el enunciado no lo especifica. Solo los endpoints de escritura (`POST`/`PUT`/`DELETE`) están protegidos; los `GET` son públicos.
6. **Concurrencia en stock:** UPDATE condicional atómico a nivel de base de datos, no lectura-luego-escritura en código.

---

## Testing

- **Unitario:** casos de uso del dominio con puertos mockeados (JUnit 5 + Mockito + Reactor Test).
- **Integración:** endpoints críticos (registro de usuario, creación de pedido, actualización de estado) contra instancias reales de PostgreSQL y Redis vía Testcontainers.

```bash
# (placeholder — comando exacto se confirma al finalizar el setup de testing)
./gradlew test
```

---

## Autor

Piter Velasquez — [LinkedIn](https://linkedin.com/in/piter-velasquez-cota) · [Portfolio](https://portfolio-piter.pealveco.workers.dev)
