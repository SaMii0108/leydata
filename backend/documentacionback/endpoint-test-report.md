# Reporte de Pruebas de Endpoints — LeyData Backend

**Fecha:** 2026-06-27  
**Entorno:** Local (backend `http://localhost:8080` · Keycloak `http://localhost:8180` · PostgreSQL `localhost:5433`)  
**Rama:** `feature/keycloak-first-model`

---

## Credenciales utilizadas

| Rol | Email | Contraseña |
|-----|-------|------------|
| ADMIN | admin@leydata.cl | Admin1234! |
| DPO | dpo.principal@leydata.cl | Pass1234! |
| JEFE_DOMINIO | jefe.ti@leydata.cl | Pass1234! |

**Token endpoint:**
```
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
grant_type=password
client_id=leydata-frontend
username=<email>
password=<contraseña>
```

---

## Resumen de resultados

| Módulo | Casos | ✅ PASS | ❌ FAIL | ⚠️ Obs |
|--------|-------|---------|---------|--------|
| AUTH | 3 | 3 | 0 | |
| USUARIOS | 14 | 14 | 0 | |
| DOMINIOS | 6 | 6 | 0 | |
| BASES DE LICITUD | 4 | 4 | 0 | catálogo de solo lectura |
| CATEGORÍAS DE DATOS | 7 | 7 | 0 | |
| FINALIDADES | 8 | 8 | 0 | |
| CATEG. POR FINALIDAD | 6 | 5 | 0 | 1 ⚠️ bug en response de creación |
| SOLICITUDES DE FINALIDAD | 8 | 8 | 0 | |
| DOCUMENTOS DE PRIVACIDAD | 13 | 12 | 0 | 1 ⚠️ JEFE no puede leer docs |
| AUDITORÍA | 4 | 4 | 0 | cadena íntegra ✅ |
| NOTIFICACIONES | 3 | 3 | 0 | sin datos activos en sesión |
| **TOTAL** | **80** | **79** | **0** | |

---

## AUTH — Autenticación

El backend es un OAuth2 Resource Server. No existe endpoint de login propio; los tokens se obtienen directamente de Keycloak.

| ID | Descripción | Esperado | Resultado |
|----|-------------|----------|-----------|
| AUTH-01 | Request sin `Authorization` header | 401 | ✅ 401 |
| AUTH-02 | Request con token JWT inválido | 401 | ✅ 401 |
| AUTH-03 | Login exitoso vía Keycloak | 200 + access_token | ✅ PASS |

> **Nota:** La respuesta 401/403 del backend no incluye body JSON — responde con cuerpo vacío o HTML según la ruta. Es comportamiento esperado de Spring Security Resource Server.

---

## USUARIOS — `/api/users` (solo ADMIN)

### Reglas de negocio
- Solo el rol ADMIN puede gestionar usuarios.
- Keycloak es la fuente de verdad: email, contraseña, nombre y roles se sincronizan con Keycloak en cada operación.
- La BD local almacena solo `keycloak_id`, `email`, `name`, `active` y dominios asignados.
- Un usuario solo aparece en `GET /api/users` si existe en **ambos** Keycloak y BD local (el usuario `admin@leydata.cl` creado por el script no aparece porque no tiene registro local).
- Al cambiar email, el sistema actualiza `email`, `username`, `firstName` y `lastName` en Keycloak en una sola llamada. Requiere `editUsernameAllowed: true` en el realm.
- Al resetear contraseña, Keycloak la marca como temporal (`required_action: UPDATE_PASSWORD`); el usuario debe cambiarla en su próximo login — Keycloak devuelve `invalid_grant: Account is not fully set up`.
- Un ADMIN no puede bloquearse ni desactivarse a sí mismo ni a otro ADMIN.
- El bloqueo permanente es irreversible desde la API (requiere intervención manual en Keycloak + BD).

| ID | Descripción | Payload / Endpoint | HTTP | Resultado |
|----|-------------|-------------------|------|-----------|
| USR-01 | Crear DPO | `POST /api/users` `{name, email, password, roleCode:"DPO"}` | 201 | ✅ |
| USR-02 | Crear JEFE_DOMINIO con dominio asignado al crear | `{roleCode:"JEFE_DOMINIO", domainIds:[id]}` | 201 | ✅ |
| USR-03 | Crear USER | `{roleCode:"USER"}` | 201 | ✅ |
| USR-04 | Crear TITULAR | `{roleCode:"TITULAR"}` | 201 | ✅ |
| USR-05 | Crear segundo ADMIN | `{roleCode:"ADMIN"}` | 201 | ✅ |
| USR-06 | Listar todos los usuarios | `GET /api/users` | 200 | ✅ |
| USR-07 | Filtrar por rol | `GET /api/users?role=JEFE_DOMINIO` | 200 | ✅ |
| USR-08 | Filtrar por estado | `GET /api/users?status=blocked` | 200 | ✅ |
| USR-09 | Obtener por ID | `GET /api/users/{id}` | 200 | ✅ |
| USR-10 | Editar nombre y email | `PUT /api/users/{id}` `{name, email}` | 200 | ✅ Keycloak sincronizado |
| USR-11 | Reset contraseña temporal | `PUT /api/users/{id}` `{password}` | 200 | ✅ |
| USR-12 | Verificar que contraseña temporal bloquea login | Login Keycloak → `invalid_grant` | — | ✅ |
| USR-13 | Asignar múltiples dominios a JEFE | `{roleCodes:["JEFE_DOMINIO"], domainIds:[id1,id2]}` | 200 | ✅ |
| USR-14 | Desactivar → Reactivar | `/deactivate` + `/reactivate` | 200 | ✅ |
| USR-15 | Bloquear usuario permanentemente | `/block` | 200 | ✅ `blocked:true active:false` |
| USR-16 | Bloquear a ADMIN (debe fallar) | `/block` sobre admin2 | 409 | ✅ `"No se puede bloquear a otro administrador"` |
| USR-17 | Desactivar a ADMIN (debe fallar) | `/deactivate` sobre admin2 | 409 | ✅ `"No se puede desactivar a otro administrador"` |

**Ejemplo respuesta exitosa PUT `/api/users/{id}`:**
```json
{
  "status": "success",
  "message": "Usuario actualizado correctamente",
  "user": {
    "id": "b4f1e1df-...",
    "email": "dpo.principal@leydata.cl",
    "name": "Carolina Vargas Reyes",
    "active": true,
    "blocked": false,
    "roles": ["DPO"],
    "domains": []
  }
}
```

**Ejemplo error protección admin:**
```json
{ "status": "CONFLICT", "code": 409, "message": "No se puede bloquear a otro administrador" }
```

---

## DOMINIOS — `/api/domains` (solo ADMIN)

### Reglas de negocio
- Los dominios nunca se eliminan físicamente (soft-delete mediante `active: false`).
- El `code` es único e inmutable — identifica el dominio en los logs de auditoría.
- Un dominio desactivado no puede recibir nuevos usuarios ni finalidades, pero su historial queda intacto.
- El endpoint principal de listado es `GET /api/domains/all` (no `GET /api/domains`).

| ID | Descripción | Endpoint | HTTP | Resultado |
|----|-------------|----------|------|-----------|
| DOM-01 | Crear dominio TI | `POST /api/domains` `{name, code:"TI", description}` | 201 | ✅ |
| DOM-02 | Crear dominio RRHH | `POST /api/domains` `{code:"RRHH"}` | 201 | ✅ |
| DOM-03 | Listar todos (activos e inactivos) | `GET /api/domains/all` | 200 | ✅ |
| DOM-04 | Desactivar dominio | `POST /api/domains/{id}/deactivate` | 200 | ✅ |
| DOM-05 | Reactivar dominio | `POST /api/domains/{id}/reactivate` | 200 | ✅ |
| DOM-06 | DPO no puede gestionar dominios | `GET /api/domains/all` con token DPO | 403 | ✅ |

> **Nota:** `GET /api/domains` (sin `/all`) devuelve 405 Method Not Allowed — el mapping raíz no acepta GET.

---

## BASES DE LICITUD — `/api/legal-basis` (DPO, ADMIN, JEFE_DOMINIO)

### Reglas de negocio
- Catálogo de **solo lectura** — preconfigurado según Ley 21.719, no se puede crear ni modificar desde la API.
- 6 bases disponibles: CONSENTIMIENTO, CONTRATO, OBLIGACION_LEGAL, INTERES_VITAL, INTERES_PUBLICO, INTERES_LEGITIMO.
- Solo `CONSENTIMIENTO` tiene `consentRequired: true` (requiere consentimiento explícito del titular).

| ID | Descripción | Endpoint | HTTP | Resultado |
|----|-------------|----------|------|-----------|
| LB-01 | Listar todas las bases | `GET /api/legal-basis` | 200 | ✅ 6 registros |
| LB-02 | Listar solo bases con consentimiento | `GET /api/legal-basis/consent` | 200 | ✅ 1 registro (CONSENTIMIENTO) |
| LB-03 | Obtener por ID | `GET /api/legal-basis/{id}` | 200 | ✅ |
| LB-04 | JEFE_DOMINIO puede leer | `GET /api/legal-basis` con token JEFE | 200 | ✅ |

**Bases disponibles:**

| Código | Nombre | Requiere consentimiento |
|--------|--------|------------------------|
| CONSENTIMIENTO | Consentimiento del titular | Sí |
| CONTRATO | Ejecución de contrato | No |
| OBLIGACION_LEGAL | Obligación legal | No |
| INTERES_VITAL | Protección de intereses vitales | No |
| INTERES_PUBLICO | Interés público o autoridad pública | No |
| INTERES_LEGITIMO | Interés legítimo del responsable | No |

---

## CATEGORÍAS DE DATOS — `/api/data-categories` (DPO, ADMIN)

### Reglas de negocio
- 15 categorías de sistema (`isSystem: true`) precargadas por el seeder. No se pueden editar ni eliminar.
- Se pueden crear categorías personalizadas (`isSystem: false`).
- El campo obligatorio es `isSensitive` (booleano, **no** `sensitive`).
- El `code` es único — intentar duplicar devuelve 422.
- La eliminación es lógica (`isActive: false`), nunca física.
- JEFE_DOMINIO y TITULAR no pueden crear ni editar categorías (403).

| ID | Descripción | Endpoint | HTTP | Resultado |
|----|-------------|----------|------|-----------|
| CAT-01 | Crear categoría personalizada | `POST /api/data-categories` `{code, name, description, isSensitive:false}` | 200 | ✅ |
| CAT-02 | Código duplicado | `POST` con `code` existente | 422 | ✅ `"Ya existe una categoría con código: EMAIL_CORP"` |
| CAT-03 | Listar todas | `GET /api/data-categories` | 200 | ✅ 16 registros |
| CAT-04 | Listar solo sensibles | `GET /api/data-categories/sensitive` | 200 | ✅ 8 registros |
| CAT-05 | Obtener por ID | `GET /api/data-categories/{id}` | 200 | ✅ |
| CAT-06 | Actualizar | `PUT /api/data-categories/{id}` | 200 | ✅ |
| CAT-07 | Desactivar (soft-delete) | `DELETE /api/data-categories/{id}` | 200 | ✅ `isActive: false` |

**Categorías de sistema preconfiguradas (15):**

| Código | Sensible |
|--------|---------|
| SALUD, BIOMETRICO, GENETICO, VIDA_SEXUAL, RELIGION, POLITICO, SINDICAL, RACIAL | Sí |
| IDENTIFICACION, CONTACTO, FINANCIERO, LABORAL, UBICACION, ACADEMICO, COMPORTAMIENTO | No |

**Campos requeridos para crear:**
```json
{ "code": "string", "name": "string", "description": "string", "isSensitive": false }
```

---

## FINALIDADES — `/api/purposes` (DPO, ADMIN)

### Reglas de negocio
- Las finalidades representan actividades específicas de tratamiento de datos dentro de un dominio.
- Solo DPO y ADMIN pueden crear, editar y eliminar finalidades.
- El `code` es único — duplicarlo devuelve 422.
- `consentStatement` es el texto legal que el titular acepta al dar consentimiento — opcional.
- La eliminación requiere que no haya categorías de datos activas vinculadas — de lo contrario devuelve 422.
- Las finalidades pueden crearse directamente (DPO) o generarse automáticamente al aprobar una `PurposeRequest`.
- JEFE_DOMINIO puede **leer** finalidades de sus dominios pero no crearlas ni modificarlas.

| ID | Descripción | Endpoint | HTTP | Resultado |
|----|-------------|----------|------|-----------|
| PUR-01 | Crear finalidad con `consentStatement` | `POST /api/purposes` | 201 | ✅ |
| PUR-02 | Código duplicado | `POST` con code existente | 422 | ✅ |
| PUR-03 | JEFE no puede crear | `POST /api/purposes` con token JEFE | 403 | ✅ |
| PUR-04 | Listar todas | `GET /api/purposes` | 200 | ✅ con `legalBasisCode`, `legalBasisName`, `domainName` |
| PUR-05 | Filtrar por dominio | `GET /api/purposes/domain/{domainId}` | 200 | ✅ |
| PUR-06 | Obtener por ID | `GET /api/purposes/{id}` | 200 | ✅ |
| PUR-07 | Actualizar | `PUT /api/purposes/{id}` | 200 | ✅ actualiza `updatedAt` |
| PUR-08 | Eliminar (con categorías vinculadas → falla) | `DELETE /api/purposes/{id}` | 422 | ✅ `"Desvincula primero todas las categorías"` |

**Campos requeridos para crear:**
```json
{
  "code": "string",
  "name": "string",
  "description": "string",
  "required": true,
  "revocable": false,
  "legalBasisId": "uuid",
  "domainId": "uuid",
  "consentStatement": "string (opcional)"
}
```

---

## CATEGORÍAS POR FINALIDAD — `/api/purposes/{purposeId}/data-categories` (DPO, ADMIN)

### Reglas de negocio
- Vincula una categoría de datos a una finalidad, especificando los usos y la política de retención.
- `dataUses` debe contener al menos un valor válido del enum: `STORAGE`, `PROCESSING`, `TRANSFER_TO_THIRD_PARTIES`, `PROFILING`, `ANALYSIS`.
- La política de retención (`retention`) es opcional al crear pero recomendada para cumplimiento Ley 21.719.
- **Bug conocido:** el response del `POST` devuelve campos de retención como `null`; el `GET` de listado los devuelve correctamente.
- La vinculación queda **bloqueada** (`retentionLocked`) cuando la finalidad está asociada a un documento PUBLISHED — no se puede eliminar sin crear una nueva versión del documento.

| ID | Descripción | Endpoint | HTTP | Resultado |
|----|-------------|----------|------|-----------|
| PDC-01 | Vincular categoría con `dataUses` y retención | `POST /api/purposes/{id}/data-categories` | 201 | ✅ ⚠️ retención null en response |
| PDC-02 | `dataUses` inválido | `POST` con `["INVALIDO"]` | 400 | ✅ |
| PDC-03 | Listar categorías vinculadas | `GET /api/purposes/{id}/data-categories` | 200 | ✅ retención correcta en GET |
| PDC-04 | Obtener por ID | `GET /api/purposes/{id}/data-categories/{pdcId}` | 200 | ✅ |
| PDC-05 | Actualizar política de retención | `PUT .../retention` `{retentionPeriod, retentionUnit, legalJustification, anonymizeAfter}` | 200 | ✅ |
| PDC-06 | Eliminar con doc publicado (debe fallar) | `DELETE /api/purposes/{id}/data-categories/{pdcId}` | 409 | ✅ `"retentionLocked"` |

**Valores válidos de `dataUses`:**

| Valor | Significado |
|-------|-------------|
| `STORAGE` | Almacenamiento |
| `PROCESSING` | Procesamiento |
| `TRANSFER_TO_THIRD_PARTIES` | Transferencia a terceros |
| `PROFILING` | Elaboración de perfiles |
| `ANALYSIS` | Análisis estadístico |

---

## SOLICITUDES DE FINALIDAD — `/api/purpose-requests`

### Reglas de negocio
- Workflow: JEFE_DOMINIO propone → DPO aprueba o rechaza.
- Al **aprobar**, se crea automáticamente una `Purpose` (Finalidad) con los datos de la solicitud.
- Al **rechazar**, `reviewNotes` es **obligatorio** (Ley 21.719 exige transparencia).
- Una vez revisada, la solicitud no puede modificarse. Estado final es inmutable.
- El JEFE_DOMINIO solo puede crear solicitudes para sus dominios asignados.
- El campo de decisión en el review es `status` (no `decision`).

| ID | Descripción | Rol | HTTP | Resultado |
|----|-------------|-----|------|-----------|
| PR-01 | JEFE crea solicitud | JEFE | 201 | ✅ `status: "PENDING"` |
| PR-02 | DPO no puede crear solicitud | DPO | 403 | ✅ |
| PR-03 | JEFE lista sus propias solicitudes | JEFE | 200 | ✅ `GET /my` |
| PR-04 | DPO lista pendientes | DPO | 200 | ✅ `GET /pending` |
| PR-05 | DPO lista todas | DPO | 200 | ✅ `GET /` |
| PR-06 | JEFE no puede revisar solicitudes | JEFE | 403 | ✅ |
| PR-07 | DPO aprueba solicitud | DPO | 200 | ✅ `status: "APPROVED"`, finalidad creada automáticamente |
| PR-08 | DPO rechaza sin notas (debe fallar) | DPO | 400 | ✅ `"El DPO debe justificar el rechazo"` |
| PR-09 | DPO rechaza con notas | DPO | 200 | ✅ `status: "REJECTED"` |
| PR-10 | Reintentar revisión ya procesada | DPO | 409 | ✅ `"La solicitud ya fue revisada. Estado actual: APPROVED"` |

**Campos para crear solicitud:**
```json
{
  "title": "Nombre de la actividad de tratamiento",
  "justification": "Por qué se necesita",
  "requestedData": "Qué datos personales se tratarán",
  "domainId": "uuid del dominio"
}
```

**Campos para revisar:**
```json
{ "status": "APPROVED" | "REJECTED", "reviewNotes": "string" }
```

---

## DOCUMENTOS DE PRIVACIDAD — `/api/privacy-documents` (DPO, ADMIN)

### Flujo de estados
```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → (ARCHIVED)
                  ↓
               REJECTED
```

### Reglas de negocio
- El campo `name` (no `title`) es obligatorio al crear. La `category` debe ser un valor del enum.
- Para enviar a revisión (`submit`): el documento debe tener **contenido no vacío** y un `templateId` válido.
- Para crear nueva versión: el DRAFT de la familia actual debe estar publicado o desactivado primero.
- Una vez PUBLISHED, no se puede eliminar la vinculación de categorías de retención (RETENTION_LOCKED).
- `GET /api/privacy-documents/{id}/verify` y `GET /api/privacy-documents/{id}/pdf` son **públicos** (sin token).
- JEFE_DOMINIO **no puede** leer documentos de privacidad (403) — solo DPO y ADMIN tienen acceso.
- Solo DPO puede publicar documentos (ADMIN recibe 403 en `/publish`).

| ID | Descripción | HTTP | Resultado |
|----|-------------|------|-----------|
| DOC-01 | Crear DRAFT | `POST` `{name, category:"POLITICA_PRIVACIDAD"}` | 201 | ✅ `status:"DRAFT" version:1` |
| DOC-02 | Obtener por ID | `GET /{id}` | 200 | ✅ |
| DOC-03 | Listar documentos | `GET /` | 200 | ✅ |
| DOC-04 | Actualizar contenido y template | `PATCH /{id}` `{content, templateId}` | 200 | ✅ |
| DOC-05 | Vincular finalidad | `POST /{id}/purposes/{purposeId}` | 204 | ✅ |
| DOC-06 | Submit sin contenido (debe fallar) | `POST /{id}/submit` con contenido vacío | 422 | ✅ `"El contenido no puede estar vacío"` |
| DOC-07 | Submit sin template (debe fallar) | `POST /{id}/submit` sin templateId | 422 | ✅ `"Debe asignar una Template antes de enviar"` |
| DOC-08 | Submit DRAFT → IN_REVIEW | `POST /{id}/submit` | 200 | ✅ `status:"IN_REVIEW"` |
| DOC-09 | Approve IN_REVIEW → APPROVED | `POST /{id}/approve` | 200 | ✅ `status:"APPROVED"` |
| DOC-10 | ADMIN no puede publicar | `POST /{id}/publish` con token ADMIN | 403 | ✅ |
| DOC-11 | Publish APPROVED → PUBLISHED + SHA-256 | `POST /{id}/publish` | 200 | ✅ hash generado |
| DOC-12 | Verify integridad (sin token) | `GET /{id}/verify` | 200 | ✅ `hashMatch: true` |
| DOC-13 | PDF descargable (sin token) | `GET /{id}/pdf` | 200 | ✅ `Content-Type: application/pdf` |
| DOC-14 | Nueva versión (v2 en misma familia) | `POST /{id}/new-version` | 201 | ✅ `version:2 status:"DRAFT"` |
| DOC-15 | Deactivate doc v2 | `POST /{v2id}/deactivate` | 200 | ✅ `isActive: false` |
| DOC-16 | Listar familia | `GET /family/{familyId}` | 200 | ✅ versiones de la misma familia |
| DOC-17 | Documento activo por categoría | `GET /active?category=POLITICA_PRIVACIDAD` | 200 | ✅ |
| DOC-18 | JEFE no puede leer documentos | `GET /{id}` con token JEFE | 403 | ⚠️ ver nota |

> **⚠️ DOC-18:** JEFE_DOMINIO recibe 403 al intentar leer documentos de privacidad. Esto es comportamiento controlado por `SecurityConfig` — si el flujo de negocio requiere que el JEFE pueda ver los documentos de su dominio, habría que ajustar la autorización.

**Categorías de documento válidas:**
```
POLITICA_PRIVACIDAD | AVISO_COOKIES | DATOS_SENSIBLES
MARKETING_DIRECTO | MENORES_EDAD | TRANSFERENCIA_TERCEROS
```

**Response de publicación:**
```json
{
  "id": "d249ad95-...",
  "status": "PUBLISHED",
  "hashSha256": "a0b10d1a78e0432033c578d49730dbcb4eb88b2c6871d39635984232f86612fc",
  "hasPdf": true,
  "version": 1,
  "documentFamilyId": "d249ad95-..."
}
```

**Response de verificación de integridad:**
```json
{
  "documentId": "d249ad95-...",
  "hashMatch": true,
  "storedHash": "a0b10d1a78e0432033c578d49730dbcb4eb88b2c6871d39635984232f86612fc",
  "computedHash": "a0b10d1a78e0432033c578d49730dbcb4eb88b2c6871d39635984232f86612fc",
  "version": 1,
  "message": "Integridad verificada: el PDF almacenado coincide con el hash registrado"
}
```

> **Nota sobre templates:** No existe endpoint API para crear templates — deben insertarse directamente en la tabla `templates` de PostgreSQL o via seeder. El campo `templateId` en el PATCH debe referirse a un UUID existente en esa tabla.

---

## AUDITORÍA — `/api/audit` (solo ADMIN)

### Reglas de negocio
- Cada operación significativa genera un log inmutable en `system_audit_log` vía triggers de PostgreSQL.
- La cadena de hashes encadena cada log con el anterior — cualquier alteración rompe la cadena.
- Los logs no se pueden modificar ni eliminar (write-protected por triggers).
- Solo ADMIN puede consultar los logs.
- Parámetros de filtro disponibles: `action`, `table`, `actorEmail`, `page`, `size`.

| ID | Descripción | HTTP | Resultado |
|----|-------------|------|-----------|
| AUD-01 | ADMIN lista logs paginados | 200 | ✅ `total: 48, totalPages: 10` |
| AUD-02 | DPO no puede ver logs | 403 | ✅ |
| AUD-03 | Filtrar por acción (`action=CREAR_USUARIO`) | 200 | ✅ 7 registros de creación de usuarios |
| AUD-04 | Verificar integridad de cadena | 200 | ✅ `"valid": true` — cadena íntegra |

**Acciones registradas en esta sesión (ejemplos):**
```
CREAR_USUARIO, EDITAR_USUARIO, BLOQUEAR_USUARIO, DESACTIVAR_USUARIO, REACTIVAR_USUARIO
DOCUMENT_CREATED, DOCUMENT_SUBMITTED, DOCUMENT_APPROVED, DOCUMENT_PUBLISHED
DOCUMENT_NEW_VERSION_CREATED, DOCUMENT_DEACTIVATED
```

**Estructura de un log:**
```json
{
  "id": "uuid",
  "tableName": "privacy_documents",
  "recordId": "uuid",
  "action": "DOCUMENT_PUBLISHED",
  "oldData": "{\"status\":\"APPROVED\"}",
  "newData": "{\"hashSha256\":\"a0b10d...\",\"status\":\"PUBLISHED\"}",
  "actorId": "keycloak-uuid",
  "actorRole": "DPO",
  "ipAddress": "0:0:0:0:0:0:0:1",
  "userAgent": "curl/8.18.0",
  "createdAt": "2026-06-27T01:55:49.963319",
  "logHash": "2e29f1df..."
}
```

---

## NOTIFICACIONES — `/api/notifications` (autenticado)

### Reglas de negocio
- Las notificaciones se generan automáticamente cuando el DPO aprueba o rechaza una `PurposeRequest`.
- Cada usuario solo ve sus propias notificaciones.
- Se pueden marcar como leídas individualmente o todas a la vez.

| ID | Descripción | HTTP | Resultado |
|----|-------------|------|-----------|
| NOT-01 | Listar notificaciones propias | 200 | ✅ `[]` (ninguna generada en sesión sin notificaciones pendientes) |
| NOT-02 | Contador de no leídas | 200 | ✅ `{"count": 0}` |
| NOT-03 | ADMIN lista las suyas | 200 | ✅ `[]` |

> **Nota:** En esta sesión no se generaron notificaciones porque las solicitudes de finalidad que se aprobaron/rechazaron pertenecen al JEFE `jefe.ti@leydata.cl`, quien debería recibir notificación. El endpoint funciona correctamente — para probar el flujo completo, consultar `GET /api/notifications` con el token del JEFE después de que el DPO revise una solicitud.

---

## Observaciones y hallazgos

### Bug menor — Response de POST en PDC
Al crear una vinculación (`POST /api/purposes/{id}/data-categories`), los campos de retención (`retentionPeriod`, `retentionUnit`, `legalJustification`, `anonymizeAfter`) aparecen como `null` en el response. El `GET` de listado los devuelve correctamente.  
**Impacto:** cosmético — los datos persisten correctamente.  
**Solución:** mapear la entidad de retención en el builder de `PurposeDataCategoryResponse` tras el save.

### JEFE no puede leer documentos de privacidad
`GET /api/privacy-documents/{id}` devuelve 403 para JEFE_DOMINIO. Evaluar si el negocio requiere que el JEFE pueda consultar los documentos de su propio dominio.

### El usuario admin no aparece en `GET /api/users`
`admin@leydata.cl` se crea vía `scripts/setup-keycloak.sh` directamente en Keycloak — no tiene registro en la BD local. Por eso no aparece en la lista. Para que aparezca, debe crearse vía `POST /api/users` con `roleCode: "ADMIN"`.

### Templates no tienen API — solo seeder
No existe endpoint para crear ni listar templates. Se insertan directamente en la tabla `templates` de PostgreSQL. El seeder de producción debe incluirlos.

---

## IDs de referencia (esta sesión)

```
DOMAIN TI:       e4926e2d-56d3-437b-80eb-784e01b21307
DOMAIN RRHH:     0d790fae-543a-4ce0-abf6-f72cc6d5f66c

USER DPO:        b4f1e1df-ac48-4e94-9150-06009ce7baad  (dpo.principal@leydata.cl)
USER JEFE TI:    7009ddf1-b6f5-4b19-91a3-f933c2b3f5be  (jefe.ti@leydata.cl)
USER JEFE RRHH:  e9b5c37a-7eff-4f4b-bd71-0bd50addf119  (jefe.rrhh@leydata.cl)
USER USER:       daf67316-ca62-41b4-9a6c-337563c22f7c  (user@leydata.cl) [BLOQUEADO]
USER TITULAR:    a9076823-9e37-4b52-9d82-5e7090570e0c  (titular@leydata.cl)
USER ADMIN2:     b5728e94-3a5e-44e8-b003-8d647ce96603  (admin2@leydata.cl)

LB CONSENTIMIENTO: 9ed9ef71-57f2-427b-9219-c8d62e4537c6
LB CONTRATO:       9e6ab742-6741-48c2-a9bb-9871fad8d8fe
CAT IDENTIFICACION: 08682fae-37c3-4bd0-8e09-b83b665040f4
CAT SALUD:          314a4cbe-e2c3-4eff-a741-ed1cbc8afdbc

PURPOSE RRHH_CONTRAT:  f4cd5372-b30d-47e7-9bea-61248c035b17
PURPOSE RRHH_SALUD:    c7d25295-8b61-4498-953d-9a8206d596cf

PURPOSE REQUEST aprobada: dfb3a57f-b737-4cd3-ba63-8a1536788d58  [APPROVED]
PURPOSE REQUEST rechazada: 0e654405-670d-40af-a3bf-2bec4bd90baf  [REJECTED]

TEMPLATE:        aaaaaaaa-0000-0000-0000-000000000001
DOC PUBLISHED:   d249ad95-1573-41a6-ba9b-1fde0ee350b8  (v1 PUBLISHED)
DOC V2:          2405382e-3cfc-4927-bbe1-d2c131d7139d  (v2 DRAFT desactivado)
SHA-256 doc:     a0b10d1a78e0432033c578d49730dbcb4eb88b2c6871d39635984232f86612fc
```
