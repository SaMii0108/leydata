# Reporte de pruebas de endpoints y reglas de negocio
**Proyecto:** LeyData Backend  
**Rama:** feature/privacy-documents-paradigma  
**Fecha:** 2026-06-21  
**Entorno:** localhost:8080 + PostgreSQL 5433 + Keycloak 8080 (realm leydata)

---

## Convenciones

| Símbolo | Significado |
|---------|-------------|
| ✅ PASS | Respuesta coincide con lo esperado |
| ❌ FAIL | Respuesta no coincide con lo esperado |
| ⬜ PENDING | No ejecutado aún |

Todos los requests autenticados usan `Authorization: Bearer <token>` obtenido del login previo.

---

## 1. Autenticación

### TC-AUTH-01 — Login exitoso como ADMIN
```
POST /api/auth/login
Body: { "email": "admin@empresa.cl", "password": "Admin1234!" }
```
**Esperado:** 200 — devuelve `token`, `refreshToken`, `expiresIn`  
**Resultado:** ⬜ PENDING

---

### TC-AUTH-02 — Login con contraseña incorrecta
```
POST /api/auth/login
Body: { "email": "admin@empresa.cl", "password": "incorrecta" }
```
**Esperado:** 401 — `status: UNAUTHORIZED`  
**Resultado:** ⬜ PENDING

---

### TC-AUTH-03 — Request sin token a endpoint protegido
```
GET /api/users
Headers: (sin Authorization)
```
**Esperado:** 401 — `status: UNAUTHORIZED`  
**Resultado:** ⬜ PENDING

---

### TC-AUTH-04 — UserStatusFilter bloquea usuario suspendido con token válido
```
1. POST /api/users/{idPedro}/deactivate   (como ADMIN)
2. GET /api/purpose-requests/my           (como Pedro, con su JWT aún válido)
```
**Esperado:** paso 1 → 200; paso 2 → 403 `FORBIDDEN`  
**Regla de negocio:** el JWT de Keycloak puede seguir siendo válido, pero el UserStatusFilter revisa `active` en BD local  
**Resultado:** ⬜ PENDING

---

### TC-AUTH-05 — UserStatusFilter bloquea usuario bloqueado
```
1. POST /api/users/{idMaria}/block        (como ADMIN)
2. GET /api/notifications                 (como María, con su JWT aún válido)
```
**Esperado:** paso 1 → 200; paso 2 → 403 `FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

## 2. Usuarios

### TC-USR-01 — Crear usuario DPO exitosamente
```
POST /api/users
Rol actor: ADMIN
Body: { "email": "dpo@empresa.cl", "name": "María DPO", "role": "DPO", "password": "Pass123!" }
```
**Esperado:** 201 — devuelve `userId`  
**Regla de negocio:** usuario creado en Keycloak y BD local en la misma transacción compensada  
**Resultado:** ⬜ PENDING

---

### TC-USR-02 — Crear usuario con email duplicado
```
POST /api/users
Rol actor: ADMIN
Body: { "email": "dpo@empresa.cl", ... }   ← mismo email que TC-USR-01
```
**Esperado:** 409 — `status: CONFLICT`, mensaje menciona el email  
**Resultado:** ⬜ PENDING

---

### TC-USR-03 — DPO intenta crear usuario (rol insuficiente)
```
POST /api/users
Rol actor: DPO
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

### TC-USR-04 — Crear JEFE_DOMINIO y asignarle dominio activo
```
POST /api/users
Body: { "email": "jefe@empresa.cl", "role": "JEFE_DOMINIO", ... }
Luego:
PUT /api/users/{id}
Body: { "roles": ["JEFE_DOMINIO"], "domainIds": ["uuid-dominio-activo"] }
```
**Esperado:** ambos → 200/201  
**Resultado:** ⬜ PENDING

---

### TC-USR-05 — Asignar dominio a usuario sin rol JEFE_DOMINIO
```
PUT /api/users/{idDPO}
Body: { "roles": ["DPO"], "domainIds": ["uuid-dominio"] }
```
**Esperado:** 400 — `status: BAD_REQUEST`, mensaje indica que solo JEFE_DOMINIO puede tener dominios  
**Regla de negocio:** dominios solo aplican para JEFE_DOMINIO  
**Resultado:** ⬜ PENDING

---

### TC-USR-06 — Asignar dominio inactivo a JEFE_DOMINIO
```
PUT /api/users/{idJefe}
Body: { "roles": ["JEFE_DOMINIO"], "domainIds": ["uuid-dominio-inactivo"] }
```
**Esperado:** 400 — `status: BAD_REQUEST`  
**Resultado:** ⬜ PENDING

---

### TC-USR-07 — Quitar rol JEFE_DOMINIO limpia dominios automáticamente
```
1. Verificar que el usuario tiene dominios asignados: GET /api/users/{id}
2. PUT /api/users/{id} con roles: ["DPO"]
3. GET /api/users/{id} — verificar que domains está vacío
```
**Esperado:** paso 3 → `domains: []`  
**Regla de negocio:** al perder JEFE_DOMINIO los vínculos de dominio se eliminan sin error explícito  
**Resultado:** ⬜ PENDING

---

### TC-USR-08 — Bloqueo permanente de usuario
```
POST /api/users/{id}/block
Rol actor: ADMIN
```
**Esperado:** 200 — `blocked: true`  
**Resultado:** ⬜ PENDING

---

### TC-USR-09 — ADMIN intenta bloquearse a sí mismo
```
POST /api/users/{idDelAdmin}/block
Rol actor: ese mismo ADMIN
```
**Esperado:** 409 — `status: CONFLICT`  
**Regla de negocio:** autoprotección — el ADMIN no puede bloquearse  
**Resultado:** ⬜ PENDING

---

### TC-USR-10 — Intentar reactivar usuario bloqueado
```
POST /api/users/{id}/reactivate   (usuario con blocked=true)
```
**Esperado:** 409 — `status: CONFLICT`  
**Regla de negocio:** el bloqueo es irreversible desde la API  
**Resultado:** ⬜ PENDING

---

### TC-USR-11 — Desactivar y reactivar usuario (ciclo normal)
```
POST /api/users/{id}/deactivate → 200, active=false
POST /api/users/{id}/reactivate → 200, active=true
```
**Esperado:** ciclo completo sin errores  
**Resultado:** ⬜ PENDING

---

### TC-USR-12 — UUID inválido en path variable
```
GET /api/users/no-es-un-uuid
```
**Esperado:** 400 — `status: BAD_REQUEST`  
**Resultado:** ⬜ PENDING

---

## 3. Dominios

### TC-DOM-01 — Crear dominio sin JEFE_DOMINIO
```
POST /api/domains
Rol actor: ADMIN
Body: { "code": "legal", "name": "Legal", "description": "Área jurídica" }
```
**Esperado:** 201 — devuelve `domainId`  
**Regla de negocio:** un dominio puede existir sin jefe asignado — simplemente no podrá recibir solicitudes hasta que se asigne uno  
**Resultado:** ⬜ PENDING

---

### TC-DOM-02 — Crear dominio con JEFE_DOMINIO válido
```
POST /api/domains
Body: { "code": "mkt", "name": "Marketing", "jefeId": "uuid-jefe-con-rol" }
```
**Esperado:** 201 — dominio y vínculo en user_domains  
**Resultado:** ⬜ PENDING

---

### TC-DOM-03 — Crear dominio con jefeId sin el rol correcto
```
POST /api/domains
Body: { "code": "ops", "name": "Operaciones", "jefeId": "uuid-usuario-dpo" }
```
**Esperado:** 400 — `status: BAD_REQUEST`  
**Resultado:** ⬜ PENDING

---

### TC-DOM-04 — Código de dominio duplicado
```
POST /api/domains
Body: { "code": "legal", ... }   ← mismo código que TC-DOM-01
```
**Esperado:** 409 — `status: CONFLICT`  
**Resultado:** ⬜ PENDING

---

### TC-DOM-05 — Desactivar dominio no cancela sus solicitudes pendientes
```
1. Crear PurposeRequest para dominio "mkt" como JEFE_DOMINIO
2. POST /api/domains/{idMkt}/deactivate (como ADMIN)
3. GET /api/purpose-requests/pending — verificar que la solicitud sigue ahí
```
**Esperado:** solicitud sigue en estado PENDING  
**Regla de negocio:** desactivar dominio no es una cascada de cancelación  
**Resultado:** ⬜ PENDING

---

### TC-DOM-06 — Reactivar dominio inactivo
```
POST /api/domains/{id}/reactivate
```
**Esperado:** 200 — `active: true`  
**Resultado:** ⬜ PENDING

---

## 4. Solicitudes de Finalidad (PurposeRequests)

### TC-PR-01 — JEFE_DOMINIO crea solicitud para su dominio
```
POST /api/purpose-requests
Rol actor: JEFE_DOMINIO (asignado a dominio "mkt")
Body: { "domainId": "uuid-mkt", "title": "Newsletter", "justification": "Comunicación comercial", "requestedData": "email, nombre" }
```
**Esperado:** 201 — `status: PENDING`  
**Resultado:** ⬜ PENDING

---

### TC-PR-02 — JEFE_DOMINIO crea solicitud para dominio ajeno
```
POST /api/purpose-requests
Rol actor: JEFE_DOMINIO (asignado solo a "mkt")
Body: { "domainId": "uuid-legal", ... }
```
**Esperado:** 400 — `status: BAD_REQUEST`, "No puedes crear solicitudes para un dominio que no te pertenece"  
**Regla de negocio:** cada jefe solo puede solicitar para sus propios dominios  
**Resultado:** ⬜ PENDING

---

### TC-PR-03 — Solicitud duplicada mientras hay una PENDING con el mismo título
```
1. TC-PR-01 → crea "Newsletter" para dominio "mkt" → PENDING
2. POST /api/purpose-requests mismo body nuevamente
```
**Esperado:** 409 — `status: CONFLICT`, "Ya tienes una solicitud pendiente con el título 'Newsletter'..."  
**Regla de negocio:** no duplicados mientras la solicitud esté en estado PENDING — evita spam al DPO  
**Resultado:** ⬜ PENDING

---

### TC-PR-04 — Misma solicitud es válida después de ser rechazada
```
1. TC-PR-01 → "Newsletter" PENDING
2. DPO rechaza la solicitud
3. JEFE_DOMINIO vuelve a crear "Newsletter" para mismo dominio
```
**Esperado:** paso 3 → 201 — permitido porque la anterior ya no está en PENDING  
**Regla de negocio:** el bloqueo aplica solo en estado PENDING  
**Resultado:** ⬜ PENDING

---

### TC-PR-05 — DPO aprueba solicitud
```
PATCH /api/purpose-requests/{id}/review
Rol actor: DPO
Body: { "status": "APPROVED", "reviewNotes": "Aprobada bajo Art. 12 Ley 21.719" }
```
**Esperado:** 200 — `status: APPROVED`  
**Resultado:** ⬜ PENDING

---

### TC-PR-06 — DPO rechaza sin motivo
```
PATCH /api/purpose-requests/{id}/review
Body: { "status": "REJECTED", "reviewNotes": "" }
```
**Esperado:** 400 — `status: BAD_REQUEST`, "El DPO debe justificar el rechazo..."  
**Regla de negocio:** principio de transparencia de Ley 21.719 — el rechazo siempre requiere justificación  
**Resultado:** ⬜ PENDING

---

### TC-PR-07 — Revisar solicitud que ya fue revisada
```
PATCH /api/purpose-requests/{id}/review   ← solicitud ya APPROVED
Body: { "status": "REJECTED", "reviewNotes": "me arrepentí" }
```
**Esperado:** 409 — `status: CONFLICT`, "La solicitud ya fue revisada. Estado actual: APPROVED"  
**Resultado:** ⬜ PENDING

---

### TC-PR-08 — ADMIN intenta revisar (solo DPO puede)
```
PATCH /api/purpose-requests/{id}/review
Rol actor: ADMIN
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

### TC-PR-09 — JEFE_DOMINIO consulta solo sus solicitudes
```
GET /api/purpose-requests/my
Rol actor: JEFE_DOMINIO
```
**Esperado:** 200 — solo las solicitudes creadas por ese jefe  
**Resultado:** ⬜ PENDING

---

### TC-PR-10 — JEFE_DOMINIO no puede ver todas las solicitudes
```
GET /api/purpose-requests
Rol actor: JEFE_DOMINIO
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

## 5. Legal Basis

### TC-LB-01 — Listar todas las bases de licitud
```
GET /api/legal-basis
Rol actor: DPO
```
**Esperado:** 200 — lista con 6 entradas seeded; CONSENTIMIENTO con `consentRequired: true`, el resto `false`  
**Resultado:** ⬜ PENDING

---

### TC-LB-02 — Filtrar solo bases que requieren consentimiento
```
GET /api/legal-basis/consent
```
**Esperado:** 200 — lista con solo CONSENTIMIENTO  
**Regla de negocio:** `consentRequired: true` es la bifurcación clave del sistema — determina si se necesita un Agreement activo  
**Resultado:** ⬜ PENDING

---

### TC-LB-03 — Obtener base por ID inexistente
```
GET /api/legal-basis/00000000-0000-0000-0000-000000000000
```
**Esperado:** 404 — `status: NOT_FOUND`  
**Resultado:** ⬜ PENDING

---

### TC-LB-04 — TITULAR no puede acceder al catálogo
```
GET /api/legal-basis
Rol actor: TITULAR
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

## 6. Categorías de Datos

### TC-DC-01 — Listar todas las categorías activas
```
GET /api/data-categories
Rol actor: DPO
```
**Esperado:** 200 — incluye las 15 del sistema (isSystem=true) + las custom activas  
**Resultado:** ⬜ PENDING

---

### TC-DC-02 — Listar categorías sensibles
```
GET /api/data-categories/sensitive
```
**Esperado:** 200 — exactamente 8 categorías: SALUD, BIOMETRICO, GENETICO, VIDA_SEXUAL, RELIGION, POLITICO, SINDICAL, RACIAL  
**Resultado:** ⬜ PENDING

---

### TC-DC-03 — Crear categoría custom
```
POST /api/data-categories
Rol actor: DPO
Body: { "code": "MASCOTA", "name": "Datos de mascotas", "isSensitive": false }
```
**Esperado:** 201 — `isSystem: false`, `isActive: true`  
**Resultado:** ⬜ PENDING

---

### TC-DC-04 — Código duplicado
```
POST /api/data-categories
Body: { "code": "MASCOTA", ... }   ← mismo de TC-DC-03
```
**Esperado:** 409 — `status: CONFLICT`  
**Resultado:** ⬜ PENDING

---

### TC-DC-05 — Editar categoría del sistema
```
PUT /api/data-categories/{idSalud}
Body: { "name": "Datos médicos modificados" }
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`, "Las categorías del sistema (Ley 21.719) no pueden modificarse."  
**Regla de negocio:** isSystem=true es inmutable — son los tipos exigidos por la ley  
**Resultado:** ⬜ PENDING

---

### TC-DC-06 — Desactivar categoría del sistema
```
DELETE /api/data-categories/{idSalud}
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-DC-07 — Desactivar categoría custom sin vínculos
```
DELETE /api/data-categories/{idMascota}   ← no vinculada a ninguna finalidad
```
**Esperado:** 200 — `isActive: false`  
**Resultado:** ⬜ PENDING

---

### TC-DC-08 — Desactivar categoría custom con vínculos activos (regla nueva)
```
1. Vincular MASCOTA a una finalidad aprobada: POST /api/purposes/{id}/data-categories
2. DELETE /api/data-categories/{idMascota}
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`, "La categoría 'Datos de mascotas' está vinculada a una o más finalidades activas..."  
**Regla de negocio:** no se puede desactivar si tiene PurposeDataCategories — evita referencias huérfanas  
**Resultado:** ⬜ PENDING

---

### TC-DC-09 — Desactivar categoría custom después de desvincularla
```
1. Desvincular MASCOTA: DELETE /api/purposes/{id}/data-categories/{pdcId}
2. DELETE /api/data-categories/{idMascota}
```
**Esperado:** paso 2 → 200 — `isActive: false`  
**Regla de negocio:** el orden correcto (desvincular primero) habilita la desactivación  
**Resultado:** ⬜ PENDING

---

## 7. Categorías por Finalidad y Retención (PurposeDataCategories)

### TC-PDC-01 — Vincular categoría a finalidad aprobada (sin doc publicado)
```
POST /api/purposes/{idFinalidadAprobada}/data-categories
Rol actor: DPO
Body: {
  "dataCategoryId": "uuid-salud",
  "required": true,
  "retention": {
    "retentionPeriod": 10,
    "retentionUnit": "YEARS",
    "legalJustification": "Art. 17 Ley 21.719",
    "anonymizeAfter": true
  }
}
```
**Esperado:** 201 — `retentionLocked: false`  
**Regla de negocio:** la retención se define por combinación (finalidad + categoría), no globalmente  
**Resultado:** ⬜ PENDING

---

### TC-PDC-02 — Vincular a finalidad que no está aprobada
```
POST /api/purposes/{idFinalidadPending}/data-categories
Body: { ... }
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-PDC-03 — Vincular categoría inactiva
```
POST /api/purposes/{id}/data-categories
Body: { "dataCategoryId": "uuid-categoria-inactiva", ... }
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-PDC-04 — Vincular categoría duplicada
```
1. TC-PDC-01 exitoso (SALUD vinculada)
2. Mismo POST con mismo dataCategoryId
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`, categoría ya vinculada  
**Resultado:** ⬜ PENDING

---

### TC-PDC-05 — retentionUnit inválida
```
POST /api/purposes/{id}/data-categories
Body: { ..., "retention": { "retentionUnit": "SEMANAS", ... } }
```
**Esperado:** 400 — `status: BAD_REQUEST`  
**Resultado:** ⬜ PENDING

---

### TC-PDC-06 — Lock: vincular nueva categoría cuando hay doc PUBLISHED (regla nueva)
```
1. Crear finalidad → vincular a documento → publicar el documento
2. POST /api/purposes/{id}/data-categories con una nueva categoría
```
**Esperado:** 409 — `status: CONFLICT` o `RETENTION_LOCKED`  
**Regla de negocio:** agregar categorías a una finalidad en doc PUBLISHED amplía el alcance del consentimiento sin que el titular lo haya visto — requiere una nueva versión del documento  
**Resultado:** ⬜ PENDING

---

### TC-PDC-07 — Lock: actualizar retención cuando hay doc PUBLISHED
```
1. Estado igual a TC-PDC-06 (doc publicado con la finalidad)
2. PUT /api/purposes/{id}/data-categories/{pdcId}/retention
   Body: { "retentionPeriod": 5, "retentionUnit": "YEARS", "legalJustification": "..." }
```
**Esperado:** 409 — `status: CONFLICT` o `RETENTION_LOCKED`  
**Resultado:** ⬜ PENDING

---

### TC-PDC-08 — Lock: desvincular categoría cuando hay doc PUBLISHED
```
DELETE /api/purposes/{id}/data-categories/{pdcId}   ← finalidad en doc publicado
```
**Esperado:** 409 — `status: CONFLICT` o `RETENTION_LOCKED`  
**Resultado:** ⬜ PENDING

---

### TC-PDC-09 — Desbloquear creando nueva versión del documento
```
1. Estado: doc PUBLISHED bloqueando la finalidad
2. POST /api/privacy-documents/{docId}/new-version → nuevo DRAFT (versión 2)
3. PUT /api/purposes/{id}/data-categories/{pdcId}/retention → ahora SIN lock
```
**Esperado:** paso 3 → 200 — `retentionLocked: false`  
**Regla de negocio:** la nueva versión en DRAFT "libera" las modificaciones; el lock se reactiva cuando esa nueva versión se publique  
**Resultado:** ⬜ PENDING

---

### TC-PDC-10 — Flag retentionLocked en respuesta lista
```
GET /api/purposes/{id}/data-categories   ← finalidad en doc publicado
```
**Esperado:** 200 — todos los items tienen `retentionLocked: true`  
**Resultado:** ⬜ PENDING

---

## 8. Documentos de Privacidad

### TC-DOC-01 — Crear documento DRAFT
```
POST /api/privacy-documents
Rol actor: DPO
Body: { "name": "Política de Marketing", "category": "MARKETING", "content": "Este documento..." }
```
**Esperado:** 201 — `status: DRAFT`, `version: 1`, `documentFamilyId` igual al propio `id`  
**Regla de negocio:** la primera versión de una familia siempre es su propio documentFamilyId  
**Resultado:** ⬜ PENDING

---

### TC-DOC-02 — JEFE_DOMINIO no puede crear documentos
```
POST /api/privacy-documents
Rol actor: JEFE_DOMINIO
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-03 — DPO consulta su DRAFT
```
GET /api/privacy-documents/{idDraft}
Rol actor: DPO
```
**Esperado:** 200 — incluye `content` y `rejectionReason`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-04 — JEFE_DOMINIO no puede ver DRAFT
```
GET /api/privacy-documents/{idDraft}
Rol actor: JEFE_DOMINIO
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Regla de negocio:** roles no privilegiados solo ven PUBLISHED — los borradores son internos  
**Resultado:** ⬜ PENDING

---

### TC-DOC-05 — JEFE_DOMINIO ve PUBLISHED sin campos internos
```
GET /api/privacy-documents/{idPublished}
Rol actor: JEFE_DOMINIO
```
**Esperado:** 200 — sin `content` ni `rejectionReason` en la respuesta  
**Regla de negocio:** filtrado de campos por rol en PrivacyDocumentResponse  
**Resultado:** ⬜ PENDING

---

### TC-DOC-06 — Vincular finalidad aprobada a DRAFT
```
POST /api/privacy-documents/{idDraft}/purposes/{idFinalidad}
Rol actor: DPO
```
**Esperado:** 204 — No Content  
**Resultado:** ⬜ PENDING

---

### TC-DOC-07 — No se puede vincular finalidad a documento que no está en DRAFT
```
POST /api/privacy-documents/{idEnReview}/purposes/{idFinalidad}
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-08 — Submit sin contenido
```
1. Crear DRAFT sin content (o con content vacío)
2. POST /api/privacy-documents/{id}/submit
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-09 — Submit sin finalidades vinculadas
```
POST /api/privacy-documents/{id}/submit   ← DRAFT con content pero sin purposes
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-10 — Flujo completo DRAFT → PUBLISHED
```
1. POST /api/privacy-documents                           → 201 DRAFT
2. POST /{id}/purposes/{purposeId}                       → 204
3. POST /{id}/submit                                     → 200 IN_REVIEW
4. POST /{id}/approve                                    → 200 APPROVED
5. POST /{id}/publish                                    → 200 PUBLISHED + PDF generado
```
**Esperado:** cada paso en el estado correcto; paso 5 devuelve `hashSha256` y `pdfUrl`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-11 — Flujo con rechazo y resubmit
```
1. DRAFT → submit → IN_REVIEW
2. POST /{id}/reject   Body: { "rejectionReason": "Falta base legal explícita" }  → REJECTED
3. POST /{id}/resubmit                                                              → IN_REVIEW
4. POST /{id}/approve                                                               → APPROVED
```
**Esperado:** flujo sin errores; la razón de rechazo queda grabada en `rejectionReason`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-12 — Transición inválida (DRAFT → APPROVED directo)
```
POST /api/privacy-documents/{idDraft}/approve
```
**Esperado:** 409 — `status: CONFLICT`, transición inválida  
**Regla de negocio:** el flujo de estados es estrictamente lineal  
**Resultado:** ⬜ PENDING

---

### TC-DOC-13 — Descargar PDF de documento PUBLISHED
```
GET /api/privacy-documents/{idPublished}/pdf
```
**Esperado:** 200 — Content-Type: application/pdf, binario descargable  
**Resultado:** ⬜ PENDING

---

### TC-DOC-14 — Descargar PDF de DRAFT (sin PDF generado)
```
GET /api/privacy-documents/{idDraft}/pdf
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`, el PDF solo existe para PUBLISHED  
**Resultado:** ⬜ PENDING

---

### TC-DOC-15 — Verificar integridad SHA-256 del PDF
```
GET /api/privacy-documents/{idPublished}/verify
```
**Esperado:** 200 — `{ "valid": true, "storedHash": "abc...", "computedHash": "abc..." }`  
**Regla de negocio:** PDF debe coincidir con el hash grabado en publicación — detecta modificaciones  
**Resultado:** ⬜ PENDING

---

### TC-DOC-16 — Nueva versión del documento
```
POST /api/privacy-documents/{idPublished}/new-version
```
**Esperado:** 201 — `version: 2`, `status: DRAFT`, `documentFamilyId` igual al original  
**Regla de negocio:** el paradigma de versioning mantiene la misma familia  
**Resultado:** ⬜ PENDING

---

### TC-DOC-17 — No se pueden crear dos DRAFTs en la misma familia
```
1. TC-DOC-16 exitoso → DRAFT v2
2. POST /api/privacy-documents/{idPublished}/new-version nuevamente
```
**Esperado:** 422 — `status: UNPROCESSABLE_ENTITY`, "ya existe un DRAFT activo en esta familia"  
**Regla de negocio:** máximo un DRAFT activo por familia — evita versiones paralelas  
**Resultado:** ⬜ PENDING

---

### TC-DOC-18 — Listar versiones de la familia (DPO)
```
GET /api/privacy-documents/family/{familyId}
Rol actor: DPO
```
**Esperado:** 200 — lista ordenada por versión descendente, incluye PUBLISHED y DRAFT  
**Resultado:** ⬜ PENDING

---

### TC-DOC-19 — JEFE_DOMINIO lista familia solo ve PUBLISHED
```
GET /api/privacy-documents/family/{familyId}
Rol actor: JEFE_DOMINIO
```
**Esperado:** 200 — solo las versiones con `status: PUBLISHED`  
**Resultado:** ⬜ PENDING

---

### TC-DOC-20 — Documento activo por categoría
```
GET /api/privacy-documents/active?category=MARKETING
```
**Esperado:** 200 — documento PUBLISHED de mayor versión para esa categoría  
**Resultado:** ⬜ PENDING

---

### TC-DOC-21 — Documento activo para categoría sin publicaciones
```
GET /api/privacy-documents/active?category=RRHH
```
**Esperado:** 404 — `status: NOT_FOUND`  
**Resultado:** ⬜ PENDING

---

## 9. Auditoría

### TC-AUD-01 — ADMIN consulta todos los logs (paginado)
```
GET /api/audit/logs?page=0&size=20
Rol actor: ADMIN
```
**Esperado:** 200 — lista paginada con `total`, `page`, `totalPages`  
**Resultado:** ⬜ PENDING

---

### TC-AUD-02 — Filtrar logs por acción
```
GET /api/audit/logs?action=SOLICITAR_PROPOSITO
```
**Esperado:** 200 — solo logs con `action: SOLICITAR_PROPOSITO`  
**Resultado:** ⬜ PENDING

---

### TC-AUD-03 — Filtrar por email que no existe
```
GET /api/audit/logs?actorEmail=noexiste@empresa.cl
```
**Esperado:** 200 — `logs: []`, `total: 0` (no 404)  
**Regla de negocio:** no revelar si el email existe en el sistema  
**Resultado:** ⬜ PENDING

---

### TC-AUD-04 — DPO no puede acceder a los logs
```
GET /api/audit/logs
Rol actor: DPO
```
**Esperado:** 403 — `status: FORBIDDEN`  
**Resultado:** ⬜ PENDING

---

### TC-AUD-05 — Verificar integridad de la cadena de hashes
```
GET /api/audit/logs/verify
Rol actor: ADMIN
```
**Esperado:** 200 — `{ "valid": true }` — cadena SHA-256 íntegra  
**Regla de negocio:** la cadena de hashes detecta cualquier modificación a los logs en BD  
**Resultado:** ⬜ PENDING

---

### TC-AUD-06 — Toda operación exitosa genera un log
```
1. Ejecutar cualquier operación de escritura (ej. aprobar solicitud)
2. GET /api/audit/logs?action=APROBAR_SOLICITUD
```
**Esperado:** aparece un nuevo registro con actor, IP y datos  
**Resultado:** ⬜ PENDING

---

## 10. Notificaciones

### TC-NOT-01 — Usuario ve sus notificaciones
```
GET /api/notifications
Rol actor: JEFE_DOMINIO
```
**Esperado:** 200 — lista de notificaciones propias, ordenadas por fecha descendente  
**Resultado:** ⬜ PENDING

---

### TC-NOT-02 — Contador de no leídas
```
GET /api/notifications/unread-count
```
**Esperado:** 200 — `{ "count": N }` donde N refleja las pendientes de leer  
**Resultado:** ⬜ PENDING

---

### TC-NOT-03 — Marcar una notificación como leída
```
PATCH /api/notifications/{id}/read
```
**Esperado:** 200 — `read: true`  
**Resultado:** ⬜ PENDING

---

### TC-NOT-04 — Marcar todas como leídas
```
PATCH /api/notifications/read-all
GET /api/notifications/unread-count   (verificación)
```
**Esperado:** `PATCH` → 200; `GET` → `{ "count": 0 }`  
**Resultado:** ⬜ PENDING

---

### TC-NOT-05 — Notificación generada al aprobar solicitud
```
1. JEFE_DOMINIO crea solicitud
2. DPO aprueba con PATCH /api/purpose-requests/{id}/review
3. JEFE_DOMINIO consulta GET /api/notifications
```
**Esperado:** aparece notificación de tipo `PURPOSE_APPROVED`  
**Resultado:** ⬜ PENDING

---

### TC-NOT-06 — Notificación generada al publicar documento
```
1. DPO publica documento que incluye finalidad del dominio "mkt"
2. JEFE_DOMINIO de "mkt" consulta GET /api/notifications
```
**Esperado:** aparece notificación de tipo `DOCUMENT_PUBLISHED`  
**Resultado:** ⬜ PENDING

---

## 11. Resumen de reglas de negocio críticas

| ID | Regla | Módulo | TC relacionados |
|----|-------|--------|-----------------|
| RN-01 | Usuarios nunca se eliminan físicamente — solo desactivar o bloquear | Usuarios | TC-USR-08 a 11 |
| RN-02 | ADMIN no puede aplicar acciones destructivas sobre sí mismo | Usuarios | TC-USR-09 |
| RN-03 | Bloqueo es irreversible desde la API | Usuarios | TC-USR-10 |
| RN-04 | Al quitar JEFE_DOMINIO, se limpian los dominios automáticamente | Usuarios | TC-USR-07 |
| RN-05 | Dominios solo para usuarios con rol JEFE_DOMINIO | Usuarios | TC-USR-05 |
| RN-06 | UserStatusFilter rechaza tokens de Keycloak válidos si el usuario está bloqueado/inactivo en BD | Auth | TC-AUTH-04, 05 |
| RN-07 | Desactivar dominio no cancela solicitudes ni finalidades | Dominios | TC-DOM-05 |
| RN-08 | No se pueden crear solicitudes duplicadas (mismo título+dominio+jefe) en estado PENDING | Purpose Requests | TC-PR-03, 04 |
| RN-09 | Rechazo de solicitud siempre requiere justificación escrita | Purpose Requests | TC-PR-06 |
| RN-10 | Solo el DPO puede revisar solicitudes | Purpose Requests | TC-PR-08, 10 |
| RN-11 | Categorías del sistema (isSystem=true) son completamente inmutables | Data Categories | TC-DC-05, 06 |
| RN-12 | No se puede desactivar una categoría con vínculos activos en finalidades | Data Categories | TC-DC-08, 09 |
| RN-13 | Vincular, modificar retención y desvincular están bloqueados si la finalidad aparece en un documento PUBLISHED | PDC | TC-PDC-06, 07, 08 |
| RN-14 | La política de retención se define por combinación (finalidad + categoría), no por categoría sola | PDC | TC-PDC-01 |
| RN-15 | PurposeDataCategory siempre incluye su DataRetentionPolicy — no pueden crearse separados | PDC | TC-PDC-01 |
| RN-16 | DPO y ADMIN ven documentos en todos los estados; otros roles solo ven PUBLISHED | Documentos | TC-DOC-04, 05 |
| RN-17 | Solo puede existir un DRAFT activo por familia de documentos | Documentos | TC-DOC-17 |
| RN-18 | El flujo de estados del documento es estrictamente lineal: DRAFT→IN_REVIEW→APPROVED→PUBLISHED | Documentos | TC-DOC-12 |
| RN-19 | Al publicar se generan PDF + SHA-256 automáticamente — no se puede publicar sin ellos | Documentos | TC-DOC-10 |
| RN-20 | Todos los logs de auditoría son inmutables — PostgreSQL trigger impide UPDATE/DELETE + hash chain para detectar manipulación | Auditoría | TC-AUD-05, 06 |
