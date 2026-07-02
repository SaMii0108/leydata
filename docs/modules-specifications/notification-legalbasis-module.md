# Módulo: Notificaciones y Bases de Licitud (`notification/` + `legalbasis/`)

## Descripción

Dos módulos pequeños y sin relación funcional entre sí, agrupados en el mismo documento por tamaño:

| Módulo | Paquete | Qué hace |
|---|---|---|
| `notification/` | `com.leydata.backend.notification` | Notificaciones internas in-app (solicitud aprobada, documento publicado, consentimiento revocado, etc.) |
| `legalbasis/` | `com.leydata.backend.legalbasis` | Catálogo de solo lectura de las bases de licitud del Art. 12-13 de la Ley 21.719 |

`notification/` es generado por los servicios de negocio y consumido por el frontend del portal — cada usuario ve solo sus propias notificaciones. `legalbasis/` es sembrado por `CatalogSeeder` al iniciar el sistema y expone los fundamentos legales que cada `Purpose` debe declarar.

---

## Reglas de negocio

### `notification/`
1. Las notificaciones pertenecen al usuario autenticado (extraído del JWT) — un usuario no puede ver ni modificar notificaciones de otros.
2. `NotificationType` clasifica el evento que generó la notificación (ver tabla de tipos en Casos de uso).

### `legalbasis/`
3. El catálogo es sembrado por `CatalogSeeder` y **no es modificable** desde la API — no hay `POST`/`PUT`/`DELETE`.
4. Cada `Purpose` debe declarar su base de licitud. Solo las finalidades con base `CONSENTIMIENTO` generan un formulario de consentimiento al titular — las demás se tratan sin pedir autorización explícita.

---

## Estados

`notification/` tiene un único flag: `read` / `unread` (sin más transiciones — no hay estado intermedio ni reversión de "leída" a "no leída").

`legalbasis/` no tiene estados — es un catálogo inmutable de solo lectura.

---

## Casos de uso

### `notification/`
1. Listar notificaciones del usuario autenticado
2. Listar solo las no leídas
3. Marcar una notificación como leída
4. Eliminar una notificación

**Tipos de notificación (`NotificationType`):**

| Tipo | Cuándo se genera |
|---|---|
| `PURPOSE_REQUEST_APPROVED` | DPO aprueba una solicitud de propósito |
| `PURPOSE_REQUEST_REJECTED` | DPO rechaza una solicitud de propósito |
| `DOCUMENT_PUBLISHED` | DPO publica un documento de privacidad |
| `AGREEMENT_REVOKED` | Se revoca un consentimiento |
| `USER_BLOCKED` | Un ADMIN bloquea un usuario |

### `legalbasis/`
5. Listar todas las bases de licitud disponibles
6. Obtener una base de licitud por ID

**Bases de licitud disponibles:**

| Código | Nombre | Requiere consentimiento |
|---|---|---|
| `CONSENTIMIENTO` | Consentimiento explícito del titular | ✅ Sí |
| `CONTRATO` | Ejecución de contrato | ❌ No |
| `OBLIGACION_LEGAL` | Obligación legal | ❌ No |
| `INTERES_VITAL` | Interés vital del titular | ❌ No |
| `INTERES_PUBLICO` | Interés público | ❌ No |
| `INTERES_LEGITIMO` | Interés legítimo del responsable | ❌ No |

---

## Endpoints

### `notification/` — Endpoints base `/api/notifications/**` — Acceso: cualquier usuario autenticado

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `GET` | `/api/notifications` | Listar notificaciones del usuario autenticado |
| `GET` | `/api/notifications/unread` | Solo no leídas |
| `PATCH` | `/api/notifications/{id}/read` | Marcar como leída |
| `DELETE` | `/api/notifications/{id}` | Eliminar notificación |

### `legalbasis/` — Endpoints base `/api/legal-basis/**` — Acceso: DPO · ADMIN · JEFE_DOMINIO (solo lectura)

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `GET` | `/api/legal-basis` | Listar todas las bases de licitud |
| `GET` | `/api/legal-basis/{id}` | Obtener por ID |

---

## Estructura del módulo

```
notification/
  application/
    dto/
    service/
      NotificationService.java
  domain/
    enums/
      NotificationType.java
  infrastructure/
    persistence/
      NotificationRepository.java
  web/
    NotificationController.java

legalbasis/
  application/
    dto/
  infrastructure/
    persistence/
      LegalBasisRepository.java
  web/
    LegalBasisController.java

seeder/
  CatalogSeeder.java   — siembra el catálogo de bases de licitud al iniciar
```
