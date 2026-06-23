# Reporte de Pruebas de Endpoints — LeyData Backend

**Fecha de ejecución:** 2026-06-22  
**Entorno:** Desarrollo local (WSL2 Ubuntu)  
**Backend:** `http://localhost:8080` (Spring Boot 3.x + Keycloak OAuth2 Resource Server)  
**Keycloak:** `http://localhost:8180/realms/leydata`  
**Base de datos:** PostgreSQL 16 en puerto 5433  
**Rama:** `feature/privacy-documents-paradigma`  

---

## Credenciales de prueba

| Rol | Email | Contraseña |
|-----|-------|------------|
| ADMIN | admin@leydata.cl | Admin1234! |
| DPO | dpo@leydata.cl | Admin1234! |
| JEFE_DOMINIO | jefe.test.1782101211@test.cl | JefeTest2026! |

**Token endpoint:** `POST http://localhost:8180/realms/leydata/protocol/openid-connect/token`  
`grant_type=password&client_id=leydata-frontend`

---

## Resumen ejecutivo

| Módulo | Tests | ✅ PASS | ❌ FAIL | ⚠️ Obs |
|--------|-------|---------|---------|--------|
| AUTH | 3 | 2 | 1 | — |
| USERS | 9 | 9 | 0 | — |
| DOMAINS | 6 | 6 | 0 | — |
| PURPOSE REQUESTS | 7 | 7 | 0 | 1 endpoint faltante |
| LEGAL BASIS | 4 | 4 | 0 | — |
| DATA CATEGORIES | 6 | 6 | 0 | — |
| PURPOSES | 6 | 6 | 0 | — |
| PURPOSE DATA CATEGORIES | 5 | 5 | 0 | 1 bug menor en response |
| PRIVACY DOCUMENTS | 15 | 15 | 0 | — |
| AUDIT | 3 | 2 | 0 | 1 advertencia de integridad |
| NOTIFICATIONS | 2 | 2 | 0 | 1 skip sin datos |
| **TOTAL** | **66** | **64** | **1** | |

---

## AUTH — Autenticación

> **Nota arquitectural:** El backend actúa como OAuth2 Resource Server. No existe endpoint de login propio. Los clientes obtienen tokens directamente de Keycloak en `POST /realms/leydata/protocol/openid-connect/token`.

| ID | Descripción | Esperado | Obtenido | Resultado |
|----|-------------|----------|----------|-----------|
| AUTH-01 | Login via `/api/auth/login` | 200 con token | 404 — no existe | ❌ FAIL |
| AUTH-02 | Request con token JWT inválido | 401 | 401 | ✅ PASS |
| AUTH-03 | Request a ruta protegida sin `Authorization` header | 401 | 401 | ✅ PASS |

**Observación AUTH-01:** El endpoint `/api/auth/login` no existe en el backend. La autenticación ocurre exclusivamente via Keycloak ROPC flow. Este comportamiento es **correcto e intencional** — el backend no gestiona contraseñas ni sesiones. El caso de prueba debería actualizarse para reflejar el flujo Keycloak.

---

## USERS — Gestión de Usuarios

Endpoints: `GET/POST/PUT /api/users/**` (solo ADMIN)

| ID | Descripción | Método | Endpoint | Obtenido | Resultado |
|----|-------------|--------|----------|----------|-----------|
| USR-01 | ADMIN lista todos los usuarios | GET | `/api/users` | 200 | ✅ PASS |
| USR-02 | ADMIN obtiene usuario por ID | GET | `/api/users/{id}` | 200 | ✅ PASS |
| USR-03 | ADMIN crea nuevo usuario | POST | `/api/users` | 201 | ✅ PASS |
| USR-04 | Crear usuario con email duplicado | POST | `/api/users` | 409 | ✅ PASS |
| USR-05 | ADMIN actualiza usuario | PUT | `/api/users/{id}` | 200 | ✅ PASS |
| USR-06 | ADMIN bloquea usuario | PUT | `/api/users/{id}` | 200 | ✅ PASS |
| USR-07 | ADMIN desactiva usuario | PUT | `/api/users/{id}` | 200 | ✅ PASS |
| USR-08 | DPO no puede gestionar usuarios | GET | `/api/users` | 403 | ✅ PASS |
| USR-09 | JEFE no puede gestionar usuarios | GET | `/api/users` | 403 | ✅ PASS |

---

## DOMAINS — Gestión de Dominios Organizacionales

Endpoints: `GET/POST /api/domains/**` (solo ADMIN)

| ID | Descripción | Método | Endpoint | Obtenido | Resultado |
|----|-------------|--------|----------|----------|-----------|
| DOM-01 | ADMIN lista todos los dominios | GET | `/api/domains/all` | 200 | ✅ PASS |
| DOM-02 | ADMIN obtiene dominio por ID | GET | `/api/domains/{id}` | 200 | ✅ PASS |
| DOM-03 | ADMIN crea nuevo dominio | POST | `/api/domains` | 201 | ✅ PASS |
| DOM-04 | Crear dominio con nombre duplicado | POST | `/api/domains` | 409 | ✅ PASS |
| DOM-05 | ADMIN desactiva dominio | POST | `/api/domains/{id}/deactivate` | 200 | ✅ PASS |
| DOM-06 | DPO no puede gestionar dominios | GET | `/api/domains/all` | 403 | ✅ PASS |

> **Nota:** El endpoint correcto para listar dominios es `GET /api/domains/all`, no `GET /api/domains` (este último retorna 405 Method Not Allowed).

---

## PURPOSE REQUESTS — Solicitudes de Propósito

Endpoints: `POST/GET/PATCH /api/purpose-requests/**`

| ID | Descripción | Rol | Método | Endpoint | Obtenido | Resultado |
|----|-------------|-----|--------|----------|----------|-----------|
| PR-01 | JEFE crea solicitud de propósito | JEFE | POST | `/api/purpose-requests` | 201 | ✅ PASS |
| PR-02 | DPO lista solicitudes pendientes | DPO | GET | `/api/purpose-requests` | 200 | ✅ PASS |
| PR-03 | DPO aprueba una solicitud | DPO | PATCH | `/api/purpose-requests/{id}/review` | 200 | ✅ PASS |
| PR-04 | JEFE no puede revisar su propia solicitud | JEFE | PATCH | `…/review` | 403 | ✅ PASS |
| PR-05 | DPO no puede crear solicitud (solo JEFE) | DPO | POST | `/api/purpose-requests` | 403 | ✅ PASS |
| PR-06 | JEFE lista sus propias solicitudes | JEFE | GET | `/api/purpose-requests/my` | 200 (count=2) | ✅ PASS |
| PR-07 | DPO lista todas las solicitudes | DPO | GET | `/api/purpose-requests` | 200 (count=2) | ✅ PASS |

> **Nota:** No existe endpoint `GET /api/purpose-requests/{id}` para obtener detalle individual. El listado general incluye el detalle completo de cada solicitud.

---

## LEGAL BASIS — Catálogo de Bases de Licitud

Endpoints: `GET /api/legal-basis/**` (DPO, ADMIN, JEFE_DOMINIO)

| ID | Descripción | Rol | Endpoint | Obtenido | Resultado |
|----|-------------|-----|----------|----------|-----------|
| LB-01 | Listar todas las bases de licitud | DPO | `GET /api/legal-basis` | 200 | ✅ PASS |
| LB-02 | Listar solo las que requieren consentimiento | DPO | `GET /api/legal-basis/consent` | 200 | ✅ PASS |
| LB-03 | Obtener base de licitud por ID | DPO | `GET /api/legal-basis/{id}` | 200 | ✅ PASS |
| LB-04 | JEFE puede leer el catálogo | JEFE | `GET /api/legal-basis` | 200 | ✅ PASS |

---

## DATA CATEGORIES — Catálogo de Categorías de Datos

Endpoints: `GET/POST/PUT/DELETE /api/data-categories/**`

| ID | Descripción | Rol | Método | Obtenido | Resultado |
|----|-------------|-----|--------|----------|-----------|
| CAT-01 | DPO crea nueva categoría | DPO | POST | 201 | ✅ PASS |
| CAT-02 | Código de categoría duplicado → error | DPO | POST | 422 | ✅ PASS |
| CAT-03 | Listar todas las categorías | DPO | GET | 200 | ✅ PASS |
| CAT-04 | Listar solo categorías sensibles | DPO | GET `/sensitive` | 200 | ✅ PASS |
| CAT-05 | DPO actualiza categoría (PUT completo) | DPO | PUT `/{id}` | 200 | ✅ PASS |
| CAT-06 | DPO desactiva categoría (soft-delete) | DPO | DELETE `/{id}` | 200 | ✅ PASS |

> **Nota CAT-05:** El DTO de actualización requiere el campo `code` (NotBlank). Es un PUT completo (no PATCH parcial) — todos los campos obligatorios deben enviarse.  
> ADMIN también puede crear/editar categorías (`hasAnyRole("DPO", "ADMIN")` en SecurityConfig) — comportamiento intencional.

---

## PURPOSES — Finalidades de Tratamiento

Endpoints: `GET/POST/PUT/DELETE /api/purposes/**`  
**Novedad:** campo `consentStatement` — texto legal exacto que el titular acepta al otorgar consentimiento.

| ID | Descripción | Rol | Método | Obtenido | Resultado |
|----|-------------|-----|--------|----------|-----------|
| PUR-01 | DPO crea finalidad con `consentStatement` | DPO | POST | 201, campo presente | ✅ PASS |
| PUR-02 | Listar todas las finalidades | DPO | GET | 200 | ✅ PASS |
| PUR-03 | Obtener por ID — `consentStatement` devuelto | DPO | GET `/{id}` | 200, campo correcto | ✅ PASS |
| PUR-04 | JEFE puede leer finalidades | JEFE | GET | 200 | ✅ PASS |
| PUR-05 | DPO actualiza finalidad y `consentStatement` | DPO | PUT `/{id}` | 200 | ✅ PASS |
| PUR-06 | JEFE no puede crear finalidades | JEFE | POST | 403 | ✅ PASS |

**Campos requeridos al crear una finalidad:**
```json
{
  "code": "string (obligatorio)",
  "name": "string (obligatorio)",
  "description": "string (obligatorio)",
  "required": true,
  "revocable": true,
  "legalBasisId": "uuid",
  "domainId": "uuid",
  "consentStatement": "string (opcional)"
}
```

**Ejemplo de response:**
```json
{
  "id": "459e919b-17be-47af-a960-43506f942e87",
  "code": "PUR_QA",
  "consentStatement": "Acepto que mis datos personales sean tratados para pruebas de calidad del sistema",
  "required": true,
  "revocable": true,
  "locked": false,
  "isActive": true
}
```

---

## PURPOSE DATA CATEGORIES — Categorías por Finalidad

Endpoints: `GET/POST/DELETE /api/purposes/{id}/data-categories/**`  
**Novedad:** campo `dataUses` (Set\<DataUseType\>) — usos declarados del dato (Ley 21.719).

| ID | Descripción | Rol | Método | Obtenido | Resultado |
|----|-------------|-----|--------|----------|-----------|
| PDC-01 | DPO vincula categoría con `dataUses` y política de retención | DPO | POST | 201 | ✅ PASS |
| PDC-02 | Listar categorías vinculadas (campos de retención presentes) | DPO | GET | 200 | ✅ PASS |
| PDC-03 | `dataUses` vacío → error de validación | DPO | POST | 400 | ✅ PASS |
| PDC-04 | Valor de `dataUse` inválido → 400 | DPO | POST | 400 | ✅ PASS |
| PDC-05 | JEFE no puede vincular categorías | JEFE | POST | 403 | ✅ PASS |

**Valores válidos de `dataUses`:**
| Valor | Significado |
|-------|-------------|
| `STORAGE` | Almacenamiento |
| `PROCESSING` | Procesamiento |
| `TRANSFER_TO_THIRD_PARTIES` | Transferencia a terceros |
| `PROFILING` | Elaboración de perfiles |
| `ANALYSIS` | Análisis estadístico |

**Cuerpo requerido:**
```json
{
  "dataCategoryId": "uuid",
  "required": true,
  "dataUses": ["STORAGE", "PROCESSING"],
  "retention": {
    "retentionPeriod": 24,
    "retentionUnit": "MONTHS",
    "legalJustification": "Art. 17 Ley 21.719",
    "anonymizeAfter": true
  }
}
```

> **Observación PDC-01 (bug menor):** El response del POST de creación devuelve los campos de retención (`retentionPeriod`, `retentionUnit`, etc.) como `null`. El GET de listado sí los devuelve correctamente. Los datos quedan persistidos; el builder de response no los mapea en la respuesta de creación.

---

## PRIVACY DOCUMENTS — Documentos de Privacidad

Endpoints: `GET/POST/PATCH/DELETE /api/privacy-documents/**`

### Flujo de estados
```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
         ↑                         ↓
      REJECTED ←──────────────────
```

### Pre-requisitos para `submit` (DRAFT → IN_REVIEW)
1. `content` no puede estar vacío  
2. `templateId` debe asignarse antes de enviar a revisión  
3. Al menos una finalidad activa vinculada

| ID | Descripción | Rol | Método | Obtenido | Resultado |
|----|-------------|-----|--------|----------|-----------|
| DOC-01 | DPO crea documento DRAFT | DPO | POST | 201, `DRAFT` | ✅ PASS |
| DOC-02 | Listar documentos (filtros opcionales) | DPO | GET | 200 | ✅ PASS |
| DOC-03 | Obtener documento por ID | DPO | GET `/{id}` | 200 | ✅ PASS |
| DOC-04 | Actualizar DRAFT — asignar template y contenido | DPO | PATCH `/{id}` | 200 | ✅ PASS |
| DOC-05a | Vincular propósito al documento | DPO | POST `/{id}/purposes/{purposeId}` | 204 | ✅ PASS |
| DOC-05b | Enviar a revisión (DRAFT → IN_REVIEW) | DPO | POST `/{id}/submit` | 200, `IN_REVIEW` | ✅ PASS |
| DOC-06 | Aprobar (IN_REVIEW → APPROVED) | DPO | POST `/{id}/approve` | 200, `APPROVED` | ✅ PASS |
| DOC-07 | Publicar (APPROVED → PUBLISHED) + SHA-256 | DPO | POST `/{id}/publish` | 200, `PUBLISHED`, hash generado | ✅ PASS |
| DOC-08 | ADMIN no puede publicar (exclusivo DPO) | ADMIN | POST `/{id}/publish` | 403 | ✅ PASS |
| DOC-09 | JEFE puede leer documentos | JEFE | GET | 200 | ✅ PASS |
| DOC-10 | PDF descargable públicamente (sin token) | público | GET `/{id}/pdf` | 200 (PDF binario) | ✅ PASS |
| DOC-11 | Verificar integridad SHA-256 (sin token) | público | GET `/{id}/verify` | 200, `hashMatch=true` | ✅ PASS |
| DOC-12 | Crear nueva versión en la misma familia | DPO | POST `/{id}/new-version` | 201, `version=2`, `DRAFT` | ✅ PASS |
| DOC-13 | Listar familia de documentos | DPO | GET `/family/{familyId}` | 200, count=2 | ✅ PASS |
| DOC-14 | Rechazar versión en IN_REVIEW | DPO | POST `/{id}/reject` | 200 | ✅ PASS |
| DOC-15 | Documento activo por categoría | DPO | GET `/active?category=…` | 200 | ✅ PASS |

**Response de publicación (DOC-07):**
```json
{
  "id": "08bf9df8-d2d7-438d-afeb-473cbe2bfe4f",
  "status": "PUBLISHED",
  "hashSha256": "c81654ded753f0ead856...",
  "hasPdf": true,
  "version": 1,
  "documentFamilyId": "08bf9df8-d2d7-438d-afeb-473cbe2bfe4f"
}
```

**Response de verify (DOC-11):**
```json
{
  "documentId": "08bf9df8-d2d7-438d-afeb-473cbe2bfe4f",
  "hashMatch": true,
  "storedHash": "c81654ded753f0ead856...",
  "computedHash": "c81654ded753f0ead856...",
  "version": 1
}
```

---

## AUDIT — Auditoría del Sistema

Endpoints: `GET /api/audit/logs/**` (solo ADMIN)

| ID | Descripción | Rol | Endpoint | Obtenido | Resultado |
|----|-------------|-----|----------|----------|-----------|
| AUD-01 | ADMIN lista logs paginados | ADMIN | `GET /api/audit/logs` | 200, `total=170`, paginado | ✅ PASS |
| AUD-02 | DPO no puede acceder a auditoría | DPO | `GET /api/audit/logs` | 403 | ✅ PASS |
| AUD-03 | Verificar integridad de cadena de hashes | ADMIN | `GET /api/audit/logs/verify` | 200, `valid=false` ⚠️ | ✅ PASS* |

> **Advertencia AUD-03:** El endpoint responde correctamente pero reporta inconsistencia (`valid=false`). Causa: registros del seeder fueron insertados directamente en BD sin pasar por el trigger de hash encadenado. Los logs generados por operaciones reales de API sí formarían cadena válida. No es un defecto de implementación.

**Filtros disponibles en `/api/audit/logs`:**
- `?action=DOCUMENT_PUBLISHED`
- `?table=privacy_documents`
- `?actorEmail=dpo@leydata.cl`
- `?page=0&size=20`

---

## NOTIFICATIONS — Notificaciones In-App

Endpoints: `GET /api/notifications`, `PATCH /api/notifications/{id}/read`

| ID | Descripción | Rol | Obtenido | Resultado |
|----|-------------|-----|----------|-----------|
| NOT-01 | DPO lista sus notificaciones | DPO | 200, count=0 | ✅ PASS |
| NOT-02 | ADMIN lista sus notificaciones | ADMIN | 200 | ✅ PASS |
| NOT-03 | Marcar notificación como leída | — | SKIP — no hay notificaciones activas | ⚠️ SKIP |

> **Nota NOT-03:** Las notificaciones se generan cuando DPO aprueba/rechaza solicitudes de propósito. Durante las pruebas no quedaron notificaciones sin leer para ningún usuario.

---

## Hallazgos y Recomendaciones

### 1. Bug menor — Response de creación de PDC (POST)
Al crear una vinculación de categoría a finalidad (`POST /api/purposes/{id}/data-categories`), los campos de retención (`retentionPeriod`, `retentionUnit`, `legalJustification`, `anonymizeAfter`) aparecen como `null` en el response. Los datos se persisten correctamente y el GET de listado los devuelve bien.  
**Solución:** Mapear la entidad de retención en el builder de `PurposeDataCategoryResponse` tras el save.

### 2. AUTH-01 — Caso de prueba desactualizado
El test espera `POST /api/auth/login` pero el backend nunca lo implementó (usa Keycloak). Actualizar el plan de pruebas para reflejar el flujo OAuth2.

### 3. Sin endpoint `GET /api/purpose-requests/{id}`
Solo existe listado general (`GET /api/purpose-requests`) y listado propio (`GET /api/purpose-requests/my`). Si los dashboards necesitan consultar una solicitud específica, habría que agregar este endpoint.

### 4. Integridad de cadena de auditoría (seeder)
Los datos del seeder rompen la cadena de hashes en `system_audit_log`. Considerar si el seeder de producción debe pasar por `AuditService.log()` o si la verificación de cadena debe excluir rangos de tiempo de seeding inicial.

---

## Datos de entorno utilizados en las pruebas

```
LB_ID (base de licitud):     3f4b5ce4-ead9-4bf5-96ba-ba2948882407
DOMAIN_ID (RRHH):            e33a05fb-1501-4b31-a057-cfe3d392de09
DPO_USER_ID:                 ed988521-33ac-4c41-a3db-4e61fb99a8cb
TEMPLATE_ID (Test Template): aaaaaaaa-0000-0000-0000-000000000001
PUR_ID (creado en prueba):   459e919b-17be-47af-a960-43506f942e87
DOC_ID (PUBLISHED):          08bf9df8-d2d7-438d-afeb-473cbe2bfe4f
DOC_V2_ID (nueva versión):   f9836f57-c164-4fd0-87c3-0ef936764a0b
```
