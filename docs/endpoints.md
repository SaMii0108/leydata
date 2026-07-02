# Referencia de endpoints — LeyData

Índice de todos los endpoints REST expuestos por el **backend** (`localhost:8080`) y el **Orquestador** (`localhost:8081`), agrupados por módulo. Generado leyendo directamente los `@RestController` del código fuente (no la colección Bruno, que en algunos puntos está desactualizada — ver `docs/test-plans/manuel-test-flow/`).

98 endpoints en total: 90 en el backend (12 módulos) + 8 en el Orquestador.

## Agreements — `/api/agreements`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/agreements` | Crear agreement con el detalle de purposes aceptadas/rechazadas |
| GET | `/api/agreements/{id}` | Obtener agreement por ID con su detalle de purposes |
| GET | `/api/agreements/active` | Consultar si existe un agreement ACTIVE para (dataSubjectId, templateId) |
| GET | `/api/agreements` | Listar agreements con filtros opcionales |
| PATCH | `/api/agreements/{id}/revoke` | Revocar un agreement — cambia estado a REVOKED y sus purposes asociados |
| GET | `/api/agreements/lifecycle-check` | Estado del ciclo de vida del consentimiento [B2B — Orquestador] |
| GET | `/api/agreements/subject-summary` | Estado de todas las purposes de un titular en un dominio [B2B] |
| GET | `/api/agreements/pending-deletions` | Purposes vencidas pendientes de eliminación de datos [B2B] |
| POST | `/api/agreements/confirm-deletion` | El CRM confirma que eliminó los datos de una purpose vencida [B2B] |

## Auditoría — `/api/audit`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/audit/logs` | Consultar logs de auditoría con filtros (action, table, actorEmail, paginado) |
| GET | `/api/audit/logs/verify` | Verificar integridad de la cadena de auditoría (hash chain) |
| POST | `/api/audit/integrity/verify` | Verificar integridad de una entidad bajo demanda (AGREEMENT/PURPOSE/TEMPLATE/DOCUMENT) |
| GET | `/api/audit/integrity/log` | Historial de verificaciones de integridad de una entidad |
| GET | `/api/audit/integrity/failed` | Listar verificaciones de integridad fallidas |
| GET | `/api/audit/trace/agreement/{id}` | Reconstruir la cadena AGREEMENT → DOCUMENT → TEMPLATE → PURPOSES |

## Categorías de Datos — `/api/data-categories`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/data-categories` | Listar todas las categorías activas (sistema + custom) |
| GET | `/api/data-categories/sensitive` | Listar categorías sensibles (Art. 16 Ley 21.719) |
| GET | `/api/data-categories/{id}` | Obtener categoría por ID |
| POST | `/api/data-categories` | Crear categoría custom |
| PUT | `/api/data-categories/{id}` | Editar categoría custom (no aplica a categorías de sistema) |
| DELETE | `/api/data-categories/{id}` | Desactivar categoría (soft delete) |

## Bases de Licitud — `/api/legal-basis`

Catálogo de solo lectura (sembrado por `CatalogSeeder`).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/legal-basis` | Listar todas las bases de licitud activas |
| GET | `/api/legal-basis/consent` | Bases que requieren consentimiento explícito (`consentRequired=true`) |
| GET | `/api/legal-basis/{id}` | Obtener base de licitud por ID |

## Notifications — `/api/notifications`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/notifications` | Listar mis notificaciones (más recientes primero) |
| GET | `/api/notifications/unread-count` | Cantidad de notificaciones no leídas |
| PATCH | `/api/notifications/{id}/read` | Marcar una notificación como leída |
| PATCH | `/api/notifications/read-all` | Marcar todas mis notificaciones como leídas |

## Dominios — `/api/domains`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/domains` | Crear dominio |
| GET | `/api/domains/all` | Listar todos los dominios (incluidos inactivos) |
| POST | `/api/domains/{domainId}/deactivate` | Desactivar dominio |
| POST | `/api/domains/{domainId}/reactivate` | Reactivar dominio |

## Privacy Documents — `/api/privacy-documents`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/privacy-documents` | Crear documento en DRAFT |
| GET | `/api/privacy-documents/{id}` | Obtener documento por ID |
| GET | `/api/privacy-documents` | Listar documentos con filtros opcionales (category, status) |
| PATCH | `/api/privacy-documents/{id}` | Editar documento en DRAFT |
| POST | `/api/privacy-documents/{id}/deactivate` | Desactivar documento — no lo borra, queda para auditoría *(no está en la colección Bruno)* |
| POST | `/api/privacy-documents/{id}/new-version` | Crear nueva versión del documento en la misma familia |
| GET | `/api/privacy-documents/family/{familyId}` | Listar todas las versiones activas de una familia de documentos |
| POST | `/api/privacy-documents/{id}/purposes/{purposeId}` | Vincular propósito (solo DRAFT) |
| DELETE | `/api/privacy-documents/{id}/purposes/{purposeId}` | Desvincular propósito (solo DRAFT) |
| POST | `/api/privacy-documents/{id}/submit` | DRAFT → IN_REVIEW |
| POST | `/api/privacy-documents/{id}/resubmit` | REJECTED → IN_REVIEW |
| POST | `/api/privacy-documents/{id}/approve` | IN_REVIEW → APPROVED |
| POST | `/api/privacy-documents/{id}/reject` | IN_REVIEW → REJECTED (motivo obligatorio) |
| POST | `/api/privacy-documents/{id}/publish` | APPROVED → PUBLISHED — genera PDF + SHA-256 |
| POST | `/api/privacy-documents/{id}/archive` | PUBLISHED → ARCHIVED *(ver bug conocido en `documentos-manual-test-flow.md`)* |
| GET | `/api/privacy-documents/{id}/pdf` | Descargar PDF del documento publicado |
| GET | `/api/privacy-documents/{id}/verify` | Verificar integridad SHA-256 del PDF almacenado |
| GET | `/api/privacy-documents/active` | Documento PUBLISHED activo por categoría *(la colección Bruno tiene la ruta vieja `/active-by-category`)* |

## Categorías por Finalidad + Retención — `/api/purposes/{purposeId}/data-categories`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/purposes/{purposeId}/data-categories` | Listar categorías vinculadas a la finalidad, con su política de retención |
| GET | `/api/purposes/{purposeId}/data-categories/{id}` | Obtener vínculo finalidad-categoría por ID |
| POST | `/api/purposes/{purposeId}/data-categories` | Vincular categoría a finalidad + definir retención |
| PUT | `/api/purposes/{purposeId}/data-categories/{id}/retention` | Actualizar política de retención |
| DELETE | `/api/purposes/{purposeId}/data-categories/{id}` | Desvincular categoría de la finalidad |

## Solicitudes de Finalidad — `/api/purpose-requests`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/purpose-requests` | Crear solicitud de finalidad (JEFE_DOMINIO propone al DPO) |
| GET | `/api/purpose-requests/my` | Mis solicitudes (del JEFE_DOMINIO autenticado) |
| GET | `/api/purpose-requests/pending` | Solicitudes pendientes de revisión |
| GET | `/api/purpose-requests` | Todas las solicitudes, cualquier estado |
| PATCH | `/api/purpose-requests/{requestId}/review` | Revisar solicitud: aprobar (crea la Finalidad) o rechazar |

## Finalidades — `/api/purposes`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/purposes` | Crear finalidad |
| GET | `/api/purposes` | Listar finalidades activas (filtradas por dominios si es JEFE_DOMINIO) |
| GET | `/api/purposes/{id}` | Obtener finalidad por ID |
| GET | `/api/purposes/domain/{domainId}` | Listar finalidades por dominio |
| PUT | `/api/purposes/{id}` | Editar finalidad (bloqueado si `locked: true`) |
| DELETE | `/api/purposes/{id}` | Desactivar finalidad (soft delete) |
| POST | `/api/purposes/{id}/new-version` | Crear nueva versión de una finalidad bloqueada |
| GET | `/api/purposes/family/{purposeFamilyId}` | Historial de versiones de una familia de finalidades |
| GET | `/api/purposes/active/{purposeFamilyId}` | Obtener la versión ACTIVE de una familia de finalidades |

## Templates — `/api/templates`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/templates` | Crear template en DRAFT |
| POST | `/api/templates/{id}/new-version` | Crear nueva versión del template (mismo TEMPLATE_KEY) |
| GET | `/api/templates/{id}` | Obtener template por ID |
| GET | `/api/templates` | Listar templates con filtros opcionales |
| GET | `/api/templates/family/{templateKey}` | Historial de versiones de un TEMPLATE_KEY dentro de un dominio |
| GET | `/api/templates/active/{templateKey}` | Obtener la versión activa de un TEMPLATE_KEY dentro de un dominio |
| GET | `/api/templates/resolve` | Resolver template activo + documento publicado por templateKey+domainId [B2B — Orquestador] |
| GET | `/api/templates/{id}/verify` | Verificar integridad SHA-256 del template activado |
| POST | `/api/templates/{id}/approve` | Aprobar template (DRAFT → APPROVED) |
| POST | `/api/templates/{id}/activate` | Activar template (APPROVED → ACTIVE), desactiva la versión anterior |
| POST | `/api/templates/{id}/purposes` | Vincular purpose al template (solo DRAFT) |
| DELETE | `/api/templates/{id}/purposes/{purposeId}` | Desvincular purpose del template (solo DRAFT) |
| GET | `/api/templates/{id}/purposes` | Listar purposes del template ordenadas por ORDER_POSITION |
| PATCH | `/api/templates/{id}/purposes/{purposeId}` | Actualizar ORDER_POSITION o IS_VISIBLE de una purpose en el template |

## Usuarios — `/api/users`

Modelo Keycloak-first: no depende de tabla local para identidad/roles.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/users` | Crear usuario (en Keycloak + dominios en BD local) |
| GET | `/api/users` | Listar usuarios (filtros: search, status, role) |
| GET | `/api/users/{userId}` | Obtener usuario por keycloak_id |
| PUT | `/api/users/{userId}` | Editar usuario (nombre, email, password, roles, dominios) |
| POST | `/api/users/{userId}/block` | Bloquear usuario permanentemente (irreversible desde la API) |
| POST | `/api/users/{userId}/deactivate` | Desactivar usuario (suspensión temporal, reversible) |
| POST | `/api/users/{userId}/reactivate` | Reactivar usuario desactivado |

## Orquestador (B2B) — `/consent` (puerto 8081)

Consumido por sistemas cliente externos (CRM/ERP) autenticados con JWT de su propio realm Keycloak (claim `leydata_domain` obligatorio).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/consent/check` | Verificar si un titular tiene consentimiento activo para una finalidad |
| POST | `/consent/capture` | Capturar el consentimiento de un titular (crea Agreement en LeyData) |
| POST | `/consent/revoke` | Revocar el consentimiento de un titular |
| GET | `/consent/template-content` | Obtener los textos legales del template activo del dominio |
| GET | `/consent/subject/{subjectId}` | Estado de todas las finalidades del titular (portal de preferencias) |
| POST | `/consent/revoke-purpose` | Revocar una finalidad específica sin afectar las demás (re-consent granular) |
| GET | `/consent/pending-deletions` | Datos pendientes de eliminación por vencimiento de retención |
| POST | `/consent/confirm-deletion` | Confirmar que el sistema cliente eliminó/anonimizó los datos |
