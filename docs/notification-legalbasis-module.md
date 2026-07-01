# Módulos: Notificaciones y Bases de Licitud

---

## Módulo `notification/` — Notificaciones In-App

**Endpoints base:** `/api/notifications/**`  
**Acceso:** Cualquier usuario autenticado (solo ve sus propias notificaciones)

### Responsabilidad

Sistema de notificaciones internas para avisar a los usuarios de eventos relevantes (solicitud aprobada, documento publicado, consentimiento revocado, etc.). Las notificaciones son generadas por los servicios de negocio y consumidas por el frontend del portal.

### `NotificationType` — Tipos de notificación

| Tipo | Cuándo se genera |
|---|---|
| `PURPOSE_REQUEST_APPROVED` | DPO aprueba una solicitud de propósito |
| `PURPOSE_REQUEST_REJECTED` | DPO rechaza una solicitud de propósito |
| `DOCUMENT_PUBLISHED` | DPO publica un documento de privacidad |
| `AGREEMENT_REVOKED` | Se revoca un consentimiento |
| `USER_BLOCKED` | Un ADMIN bloquea un usuario |

### Endpoints

```
GET    /api/notifications              — Listar notificaciones del usuario autenticado
GET    /api/notifications/unread       — Solo no leídas
PATCH  /api/notifications/{id}/read    — Marcar como leída
DELETE /api/notifications/{id}         — Eliminar notificación
```

Las notificaciones son del usuario autenticado (extraído del JWT). Un usuario no puede ver ni modificar notificaciones de otros usuarios.

### Archivos clave

| Archivo | Rol |
|---|---|
| `notification/web/NotificationController.java` | Endpoints REST |
| `notification/application/service/NotificationService.java` | Consulta y marcado de lectura |
| `notification/domain/enums/NotificationType.java` | Tipos de notificación |
| `notification/infrastructure/persistence/NotificationRepository.java` | JPA sobre `notifications` |

---

## Módulo `legalbasis/` — Catálogo de Bases de Licitud

**Endpoints base:** `/api/legal-basis/**`  
**Acceso:** DPO · ADMIN · JEFE_DOMINIO (solo lectura — el catálogo es inmutable)

### Responsabilidad

Expone el catálogo de bases de licitud del Art. 12-13 de la Ley 21.719. Son los fundamentos legales que justifican el tratamiento de datos personales. El catálogo es sembrado por `CatalogSeeder` al iniciar el sistema y **no es modificable** desde la API.

### Bases de licitud disponibles

| Código | Nombre | Requiere consentimiento |
|---|---|---|
| `CONSENTIMIENTO` | Consentimiento explícito del titular | ✅ Sí |
| `CONTRATO` | Ejecución de contrato | ❌ No |
| `OBLIGACION_LEGAL` | Obligación legal | ❌ No |
| `INTERES_VITAL` | Interés vital del titular | ❌ No |
| `INTERES_PUBLICO` | Interés público | ❌ No |
| `INTERES_LEGITIMO` | Interés legítimo del responsable | ❌ No |

Cada **Finalidad** (`purposes/`) debe declarar su base de licitud. Solo las finalidades con base `CONSENTIMIENTO` generan un formulario de consentimiento al titular — las demás se tratan sin pedir autorización explícita.

### Endpoints

```
GET  /api/legal-basis        — Listar todas las bases de licitud
GET  /api/legal-basis/{id}   — Obtener por ID
```

No hay POST/PUT/DELETE — el catálogo es de solo lectura.

### Archivos clave

| Archivo | Rol |
|---|---|
| `legalbasis/web/LegalBasisController.java` | Endpoints de lectura |
| `legalbasis/infrastructure/persistence/LegalBasisRepository.java` | JPA sobre `legal_basis_catalog` |
| `seeder/CatalogSeeder.java` | Siembra el catálogo al iniciar |
