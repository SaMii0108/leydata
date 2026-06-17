# Pruebas de Endpoints — LeyData Backend (curl)

**Base URL:** `http://localhost:8080`  
**Keycloak:** `http://localhost:8180/realms/leydata`  
**Auth:** Bearer JWT en cada request (`Authorization: Bearer $TOKEN`)

---

## Compatibilidad por plataforma

| Plataforma | Soporte | Prerrequisitos |
|------------|---------|----------------|
| **macOS** (Terminal / zsh) | ✅ Funciona sin cambios | `brew install jq` |
| **WSL** (Ubuntu/Debian en Windows) | ✅ Funciona sin cambios | `sudo apt install jq curl` |
| **Windows PowerShell** | ⚠️ Requiere adaptación | Ver sección PowerShell al final |
| **Git Bash (Windows)** | ✅ Funciona sin cambios | Instalar `jq` manualmente → ver abajo |

> **Recomendación en Windows:** usar WSL o Git Bash en lugar de PowerShell nativo para estas pruebas. Las diferencias son: PowerShell no entiende `$()` en el mismo sentido, las funciones bash no existen, y `curl` en PowerShell es un alias de `Invoke-WebRequest` (distinto al binario real).

### Instalar jq en Windows (Git Bash)

```bash
# Opción 1: winget (Windows 11)
winget install jqlang.jq

# Opción 2: chocolatey
choco install jq

# Opción 3: scoop
scoop install jq
```

Después de instalar, reiniciar Git Bash.

---

## 0. Setup: obtener tokens JWT (macOS / WSL / Git Bash)

El backend es un Resource Server OAuth2. Los tokens los emite Keycloak con `grant_type=password` (requiere que el cliente tenga **Direct Access Grants** habilitado).

```bash
# Función helper: obtener token para cualquier usuario
get_token() {
  curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=leydata-frontend&username=$1&password=$2" \
    | jq -r '.access_token'
}

# Tokens por rol (ajustar credenciales según tu Keycloak)
TOKEN_ADMIN=$(get_token "admin@leydata.cl" "Admin1234!")
TOKEN_DPO=$(get_token "dpo@leydata.cl" "Dpo1234!")
TOKEN_JEFE=$(get_token "jefe@leydata.cl" "Jefe1234!")

# Verificar que el token es válido (debe mostrar claims del JWT)
echo $TOKEN_ADMIN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

> **Nota:** Si el cliente usa `client_secret`, agregar `-d "client_secret=SECRETO"` al request.

---

## 1. Módulo Users — `/api/users`

> Rol requerido: **ADMIN** en todos los endpoints.

### 1.1 Crear usuario

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jefe@leydata.cl",
    "name": "Juan Pérez",
    "password": "Jefe1234!",
    "roleCode": "JEFE_DOMINIO",
    "domainIds": []
  }' | jq .
```

**Respuesta esperada (201):**
```json
{
  "status": "success",
  "message": "Usuario creado correctamente en el sistema y en autenticación.",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Reglas de negocio:**
- `email` debe ser único → `409 CONFLICT` si ya existe
- `password` es obligatorio (va a Keycloak, nunca se persiste en la BD) → `400` si está vacío
- `roleCode` debe existir en BD → `400` si no existe
- `domainIds` solo aplica si `roleCode = JEFE_DOMINIO` → `400` si se envían dominios para otro rol
- Si la BD falla después de crear en Keycloak, se elimina el usuario de Keycloak (compensación automática)

**Errores comunes:**
```bash
# 409 — email duplicado
{ "status": "CONFLICT", "code": 409, "message": "Ya existe un usuario con el email: jefe@leydata.cl" }

# 400 — rol inexistente
{ "status": "BAD_REQUEST", "code": 400, "message": "Rol no encontrado: ROL_INEXISTENTE" }
```

---

### 1.2 Listar todos los usuarios

```bash
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "users": [
    {
      "id": "uuid",
      "email": "admin@leydata.cl",
      "name": "Administrador",
      "active": true,
      "blocked": false,
      "roles": ["ADMIN"],
      "domains": []
    }
  ]
}
```

---

### 1.3 Obtener usuario por ID

```bash
USER_ID="550e8400-e29b-41d4-a716-446655440000"

curl -s http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "user": { "id": "...", "email": "...", "name": "...", "active": true, "blocked": false, "roles": [...], "domains": [...] }
}
```

**Error (404):**
```json
{ "status": "NOT_FOUND", "code": 404, "message": "Usuario no encontrado: 550e8400-..." }
```

---

### 1.4 Editar usuario

```bash
curl -s -X PUT http://localhost:8080/api/users/$USER_ID \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Juan Pérez Actualizado",
    "roleCodes": ["JEFE_DOMINIO"],
    "domainIds": []
  }' | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "message": "Usuario actualizado correctamente",
  "user": { "id": "...", "name": "Juan Pérez Actualizado", ... }
}
```

**Reglas de negocio:**
- El ADMIN no puede editarse a sí mismo → `400`
- Un usuario bloqueado no puede editarse → `409`
- Si pierde el rol `JEFE_DOMINIO`, se desvincula automáticamente de todos sus dominios
- Todos los campos son opcionales (PATCH semántico)

---

### 1.5 Bloquear usuario (irreversible desde la API)

```bash
curl -s -X POST http://localhost:8080/api/users/$USER_ID/block \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "message": "Usuario bloqueado permanentemente",
  "user": { "id": "...", "active": false, "blocked": true, ... }
}
```

**Reglas de negocio:**
- El ADMIN no puede bloquearse a sí mismo → `400`
- Un usuario ya bloqueado no puede bloquearse de nuevo → `409`
- El bloqueo desactiva al usuario (`active=false`) de forma simultánea
- **Irreversible desde la API** (solo en BD directamente por DBA)

---

### 1.6 Desactivar usuario (suspensión temporal)

```bash
curl -s -X POST http://localhost:8080/api/users/$USER_ID/deactivate \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Reglas de negocio:**
- El ADMIN no puede desactivarse a sí mismo → `400`
- Un usuario bloqueado no puede desactivarse → `409`
- Un usuario ya desactivado → `409`

---

### 1.7 Reactivar usuario

```bash
curl -s -X POST http://localhost:8080/api/users/$USER_ID/reactivate \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Reglas de negocio:**
- Un usuario bloqueado **no puede reactivarse** → `409`
- Un usuario ya activo → `409`

---

## 2. Módulo Dominios — `/api/domains`

> Rol requerido: **ADMIN** en todos los endpoints.

### 2.1 Crear dominio

```bash
curl -s -X POST http://localhost:8080/api/domains \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "RRHH",
    "name": "Recursos Humanos",
    "description": "Dominio de gestión de personas",
    "jefeId": null
  }' | jq .
```

**Respuesta esperada (201):**
```json
{
  "status": "success",
  "message": "Dominio creado correctamente",
  "domainId": "uuid-del-dominio"
}
```

**Reglas de negocio:**
- `code` debe ser único → `400` si ya existe
- `jefeId` es opcional. Si se proporciona:
  - El usuario debe existir → `400` si no existe
  - El usuario debe tener rol `JEFE_DOMINIO` → `400` si no lo tiene
  - Se vincula automáticamente al dominio (tabla `user_domains`)

---

### 2.2 Listar todos los dominios (incluye inactivos)

```bash
curl -s http://localhost:8080/api/domains/all \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "domains": [
    {
      "id": "uuid",
      "code": "RRHH",
      "name": "Recursos Humanos",
      "description": "...",
      "active": true,
      "createdAt": "2026-06-15T10:00:00"
    }
  ]
}
```

---

### 2.3 Desactivar dominio

```bash
DOMAIN_ID="uuid-del-dominio"

curl -s -X POST http://localhost:8080/api/domains/$DOMAIN_ID/deactivate \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Reglas de negocio:**
- Dominio inexistente → `404`
- Dominio ya desactivado → `409`
- La desactivación es **reversible** (a diferencia del bloqueo de usuarios)

---

### 2.4 Reactivar dominio

```bash
curl -s -X POST http://localhost:8080/api/domains/$DOMAIN_ID/reactivate \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Reglas de negocio:**
- Dominio inexistente → `404`
- Dominio ya activo → `409`

---

## 3. Módulo Solicitudes de Propósito — `/api/purpose-requests`

### 3.1 Crear solicitud *(JEFE_DOMINIO)*

```bash
curl -s -X POST http://localhost:8080/api/purpose-requests \
  -H "Authorization: Bearer $TOKEN_JEFE" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Solicitud de tratamiento de datos de nómina",
    "justification": "Necesitamos procesar datos personales para el pago mensual de sueldos.",
    "requestedData": "Nombre, RUT, cuenta bancaria, sueldo base",
    "domainId": "uuid-del-dominio"
  }' | jq .
```

**Respuesta esperada (201):**
```json
{
  "status": "success",
  "message": "Solicitud enviada al DPO correctamente",
  "request": {
    "id": "uuid",
    "title": "Solicitud de tratamiento...",
    "status": "PENDING",
    "domainId": "uuid",
    "domainName": "Recursos Humanos",
    "requesterId": "uuid",
    "requesterName": "Juan Pérez",
    "createdAt": "2026-06-15T10:00:00"
  }
}
```

**Reglas de negocio:**
- Solo el jefe puede crear solicitudes para **sus propios dominios** → `400` si el dominio no le pertenece
- El dominio debe estar **activo** → `409` si está desactivado
- El dominio debe existir → `400` si no existe

---

### 3.2 Ver mis solicitudes *(JEFE_DOMINIO)*

```bash
curl -s http://localhost:8080/api/purpose-requests/my \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

Retorna solo las solicitudes creadas por el jefe autenticado.

---

### 3.3 Ver solicitudes pendientes *(DPO)*

```bash
curl -s http://localhost:8080/api/purpose-requests/pending \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

Retorna todas las solicitudes con `status = PENDING`.

---

### 3.4 Ver todas las solicitudes *(DPO)*

```bash
curl -s http://localhost:8080/api/purpose-requests \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

Historial completo: PENDING, APPROVED y REJECTED.

---

### 3.5 Revisar solicitud — aprobar *(DPO)*

```bash
REQUEST_ID="uuid-de-la-solicitud"

curl -s -X PATCH http://localhost:8080/api/purpose-requests/$REQUEST_ID/review \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "APPROVED",
    "reviewNotes": "Tratamiento justificado y proporcional según Ley 21.719 art. 3."
  }' | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "message": "Solicitud revisada correctamente",
  "request": { "id": "...", "status": "APPROVED", "reviewerId": "uuid-dpo", ... }
}
```

---

### 3.6 Revisar solicitud — rechazar *(DPO)*

```bash
curl -s -X PATCH http://localhost:8080/api/purpose-requests/$REQUEST_ID/review \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "REJECTED",
    "reviewNotes": "El tratamiento excede los datos mínimos necesarios. Revisar principio de minimización (Art. 3 Ley 21.719)."
  }' | jq .
```

**Reglas de negocio:**
- Solo acepta `status = APPROVED` o `REJECTED` → `400` si se envía otro valor
- El rechazo **requiere `reviewNotes` obligatorio** (principio de transparencia, Art. 14 Ley 21.719) → `400` si está vacío
- Solo se puede revisar una solicitud `PENDING` → `409` si ya fue revisada

---

## 4. Módulo Auditoría — `/api/audit`

> Rol requerido: **ADMIN** en todos los endpoints.

### 4.1 Listar todos los logs (sin filtro)

```bash
curl -s "http://localhost:8080/api/audit/logs" \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta esperada (200):**
```json
{
  "status": "success",
  "logs": [
    {
      "id": "uuid",
      "tableName": "users",
      "recordId": "uuid",
      "action": "CREAR_USUARIO",
      "oldData": null,
      "newData": "{\"email\":\"jefe@leydata.cl\",\"active\":true,...}",
      "actorId": "uuid-admin",
      "actorRole": "ADMIN",
      "ipAddress": "127.0.0.1",
      "userAgent": "curl/7.88.1",
      "createdAt": "2026-06-15T10:00:00",
      "logHash": "a3f2c1..."
    }
  ],
  "total": 1,
  "page": 0,
  "totalPages": 1
}
```

### 4.2 Filtrar por acción

```bash
curl -s "http://localhost:8080/api/audit/logs?action=BLOQUEAR_USUARIO" \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

Acciones disponibles:

| Módulo              | Acciones                                                                                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Usuarios            | `CREAR_USUARIO`, `EDITAR_USUARIO`, `DESACTIVAR_USUARIO`, `REACTIVAR_USUARIO`, `BLOQUEAR_USUARIO`                                                                                                       |
| Dominios            | `CREAR_DOMINIO`, `DESACTIVAR_DOMINIO`, `REACTIVAR_DOMINIO`                                                                                                                                             |
| Solicitudes         | `SOLICITAR_PROPOSITO`, `APROBAR_SOLICITUD`, `RECHAZAR_SOLICITUD`                                                                                                                                       |
| Documentos privacidad | `CREAR_DOCUMENTO`, `EDITAR_DOCUMENTO`, `DESACTIVAR_DOCUMENTO`, `VINCULAR_PROPOSITO`, `DESVINCULAR_PROPOSITO`, `ENVIAR_A_REVISION`, `REENVIAR_A_REVISION`, `APROBAR_DOCUMENTO`, `RECHAZAR_DOCUMENTO`, `PUBLICAR_DOCUMENTO`, `ARCHIVAR_DOCUMENTO` |

### 4.3 Filtrar por tabla

```bash
curl -s "http://localhost:8080/api/audit/logs?table=domains" \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

Tablas: `users`, `domains`, `purpose_requests`.

### 4.4 Filtrar por email del actor

```bash
curl -s "http://localhost:8080/api/audit/logs?actorEmail=admin@leydata.cl" \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

### 4.5 Paginación

```bash
curl -s "http://localhost:8080/api/audit/logs?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

### 4.6 Verificar integridad de la cadena de auditoría

```bash
curl -s http://localhost:8080/api/audit/logs/verify \
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .
```

**Respuesta OK (200):**
```json
{ "status": "success", "valid": true, "message": "Cadena de auditoría íntegra. Ningún registro ha sido alterado." }
```

**Respuesta con alerta (200):**
```json
{ "status": "warning", "valid": false, "message": "ALERTA: Se detectaron inconsistencias en la cadena de auditoría. Posible alteración de registros." }
```

> La cadena de auditoría usa SHA-256 encadenado (hash de cada log incluye el hash del log anterior). Cualquier modificación directa en la BD rompe la cadena.

---

## 5. Módulo Documentos de Privacidad — `/api/privacy-documents`

Flujo de estados: `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED`  
Rechazo: `IN_REVIEW → REJECTED → IN_REVIEW` (tras correcciones con resubmit)

### 5.1 Crear documento en DRAFT *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{
    "category": "POLITICA_PRIVACIDAD",
    "name": "Política de Privacidad RRHH 2026",
    "content": "En cumplimiento de la Ley 21.719...",
    "templateId": null
  }' | jq .
```

**Categorías disponibles:**
- `POLITICA_PRIVACIDAD` — Art. 12, tratamiento general
- `AVISO_COOKIES` — Art. 12, cookies y rastreo
- `DATOS_SENSIBLES` — Art. 13, salud/biométricos/origen racial
- `MARKETING_DIRECTO` — Art. 12, comunicaciones comerciales
- `MENORES_EDAD` — Art. 14, menores de 14 años
- `TRANSFERENCIA_TERCEROS` — Art. 16, cesión a terceros

**Respuesta esperada (201):**
```json
{
  "id": "uuid-del-documento",
  "documentFamilyId": "uuid-del-documento",
  "category": "POLITICA_PRIVACIDAD",
  "status": "DRAFT",
  "version": 1,
  "name": "Política de Privacidad RRHH 2026",
  "content": "...",
  "hasPdf": false,
  "createdAt": "2026-06-15T10:00:00",
  "purposeIds": []
}
```

> **Nota:** `documentFamilyId` siempre se inicializa igual al propio `id` al crear. Agrupa todas las versiones del mismo documento lógico.

---

### 5.2 Listar documentos con filtros opcionales

```bash
# Sin filtros
curl -s http://localhost:8080/api/privacy-documents \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .

# Por categoría
curl -s "http://localhost:8080/api/privacy-documents?category=POLITICA_PRIVACIDAD" \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .

# Por estado
curl -s "http://localhost:8080/api/privacy-documents?status=DRAFT" \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

---

### 5.3 Obtener documento por ID

```bash
DOC_ID="uuid-del-documento"

curl -s http://localhost:8080/api/privacy-documents/$DOC_ID \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

---

### 5.4 Editar documento en DRAFT *(DPO)*

```bash
curl -s -X PATCH http://localhost:8080/api/privacy-documents/$DOC_ID \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Política de Privacidad RRHH v2",
    "content": "Texto legal actualizado...",
    "templateId": "uuid-de-template"
  }' | jq .
```

**Reglas:**
- Solo funciona si el documento está en estado `DRAFT` → `409` en cualquier otro estado.
- Editar un borrador **no incrementa el número de versión**. La versión solo sube al crear una nueva versión con `POST /{id}/new-version`.

---

### 5.5 Vincular propósito a documento *(DPO)*

```bash
PURPOSE_ID="uuid-del-proposito"

curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/purposes/$PURPOSE_ID \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -w "\nHTTP Status: %{http_code}\n"
```

**Respuesta:** `204 No Content` (sin body).  
**Regla:** Solo en estado `DRAFT`.

---

### 5.6 Desvincular propósito *(DPO)*

```bash
curl -s -X DELETE http://localhost:8080/api/privacy-documents/$DOC_ID/purposes/$PURPOSE_ID \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -w "\nHTTP Status: %{http_code}\n"
```

**Respuesta:** `204 No Content`.

---

### 5.7 Enviar a revisión: DRAFT → IN_REVIEW *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/submit \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Respuesta esperada (200):**
```json
{ "id": "...", "status": "IN_REVIEW", ... }
```

**Regla:** Solo desde `DRAFT` → `409` si está en otro estado.

---

### 5.8 Reenviar tras correcciones: REJECTED → IN_REVIEW *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/resubmit \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Regla:** Solo desde `REJECTED` → `409` si está en otro estado.

---

### 5.9 Aprobar documento: IN_REVIEW → APPROVED *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/approve \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Regla:** Solo desde `IN_REVIEW` → `409` en otro estado.

---

### 5.10 Rechazar documento: IN_REVIEW → REJECTED *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/reject \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "El contenido no especifica el período de retención de datos (Art. 12 Ley 21.719)."
  }' | jq .
```

**Regla:** `reason` es **obligatorio** → `400` si está vacío. Solo desde `IN_REVIEW`.

---

### 5.11 Publicar documento: APPROVED → PUBLISHED *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/publish \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Respuesta esperada (200):**
```json
{
  "id": "...",
  "status": "PUBLISHED",
  "hasPdf": true,
  "hashSha256": "a3f2c1d4...",
  "publishAt": "2026-06-15T10:30:00",
  "version": 1
}
```

**Reglas:**
- Genera el PDF y lo almacena como `bytea` en la BD
- Calcula el hash SHA-256 del PDF para verificación de integridad posterior
- Múltiples versiones de una misma categoría pueden estar `PUBLISHED` simultáneamente — la versión canónica es la de mayor número de versión
- **No archiva automáticamente** versiones anteriores publicadas; cada versión publicada sigue vigente para los consentimientos ya otorgados
- Solo desde `APPROVED` → `409` en otro estado

---

### 5.12 Archivar documento: PUBLISHED → ARCHIVED *(DPO)*

```bash
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/archive \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Regla:** Solo desde `PUBLISHED`.

---

### 5.13 Descargar PDF del documento

```bash
curl -s -o documento.pdf \
  http://localhost:8080/api/privacy-documents/$DOC_ID/pdf \
  -H "Authorization: Bearer $TOKEN_JEFE"

# Verificar el archivo descargado
file documento.pdf
```

**Regla:** Solo disponible cuando `status = PUBLISHED` o `ARCHIVED` (tiene PDF generado) → `400` si no hay PDF.

---

### 5.14 Verificar integridad SHA-256 del PDF

```bash
curl -s http://localhost:8080/api/privacy-documents/$DOC_ID/verify \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

**Respuesta esperada (200):**
```json
{
  "valid": true,
  "storedHash": "a3f2c1d4...",
  "computedHash": "a3f2c1d4..."
}
```

Si los hashes no coinciden, el PDF fue alterado en la BD.

---

### 5.15 Documento activo por categoría

```bash
curl -s "http://localhost:8080/api/privacy-documents/active?category=POLITICA_PRIVACIDAD" \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

Retorna la versión canónica vigente (mayor número de versión publicado) para esa categoría.  
**Error (404):** Si no existe ninguno publicado.

---

### 5.16 Desactivar documento en DRAFT *(DPO)*

El documento **no se borra** — queda en BD con `isActive: false` para trazabilidad de auditoría. Ya no aparece en el listado `GET /api/privacy-documents`.

```bash
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/deactivate" \
  -H "Authorization: Bearer $TOKEN_DPO" | jq '{id: .id, status: .status, isActive: .isActive}'
```

**Respuesta:** `200 OK` — el documento con `"isActive": false`.  
**Regla:** El documento **no puede tener finalidades activas** asociadas (`is_active = true` en `document_purposes`). Si las tiene → `422`. No hay restricción de estado — cualquier documento sin finalidades activas puede desactivarse, independientemente de si está en `DRAFT`, `APPROVED`, etc.  
**Consulta posterior:** `GET /api/privacy-documents/$DOC_ID` sigue devolviendo el documento (con `isActive: false`), pero no aparece en `GET /api/privacy-documents`.

---

### 5.17 Crear nueva versión de un documento *(DPO)*

Crea un nuevo `DRAFT` en la misma familia del documento origen, copiando su categoría, nombre, contenido y templateId. La versión se incrementa respecto al máximo de la familia.

```bash
# DOC_ID debe ser un documento PUBLISHED (o cualquier estado si no hay DRAFT activo en la familia)
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/new-version" \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .
```

**Respuesta esperada (201):**
```json
{
  "id": "uuid-nueva-version",
  "documentFamilyId": "uuid-del-documento-origen",
  "status": "DRAFT",
  "version": 2,
  "name": "Política de Privacidad RRHH 2026",
  "content": "..."
}
```

**Reglas:**
- Si ya existe un `DRAFT` activo en la misma familia → `422` con mensaje explicativo
- La versión origen **no se desactiva** al crear la nueva; ambas pueden coexistir
- `documentFamilyId` de la nueva versión es siempre el `id` del primer documento de la familia

---

### 5.18 Listar todas las versiones activas de una familia

```bash
FAMILY_ID="uuid-documentFamilyId"

curl -s "http://localhost:8080/api/privacy-documents/family/$FAMILY_ID" \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

**Respuesta esperada (200):** array ordenado de mayor a menor versión:
```json
[
  { "id": "...", "documentFamilyId": "...", "version": 2, "status": "DRAFT", ... },
  { "id": "...", "documentFamilyId": "...", "version": 1, "status": "PUBLISHED", ... }
]
```

> El `FAMILY_ID` es el `documentFamilyId` de cualquier versión de la familia — siempre es igual al `id` del primer documento creado.

---

## 6. Módulo Notificaciones — `/api/notifications`

> Cada usuario ve y gestiona **únicamente sus propias notificaciones**.  
> Las notificaciones se generan automáticamente cuando el DPO aprueba/rechaza una solicitud de propósito o publica un documento de privacidad.

```bash
# Variables necesarias (obtener tokens de la sección 1)
TOKEN_JEFE="<token del JEFE_DOMINIO que tiene notificaciones>"

# ID de una notificación — obtenerlo del GET /api/notifications
NOTIF_ID="<uuid de la notificación>"
```

---

### 6.1 Listar mis notificaciones

```bash
curl -s http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

Retorna todas las notificaciones del usuario autenticado, ordenadas por fecha descendente.

**Respuesta esperada (200):**
```json
[
  {
    "id": "uuid",
    "type": "PURPOSE_APPROVED",
    "title": "Solicitud aprobada: Mi propósito",
    "message": "Tu solicitud fue aprobada por el DPO.",
    "referenceId": "uuid-de-la-solicitud",
    "read": false,
    "createdAt": "2026-06-16T10:00:00"
  }
]
```

**Tipos de notificación disponibles:** `PURPOSE_APPROVED`, `PURPOSE_REJECTED`, `DOCUMENT_PUBLISHED`

---

### 6.2 Conteo de no leídas

```bash
curl -s http://localhost:8080/api/notifications/unread-count \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

Útil para mostrar un badge en la UI.

**Respuesta esperada (200):**
```json
{ "count": 2 }
```

---

### 6.3 Marcar una notificación como leída

```bash
curl -s -X PATCH "http://localhost:8080/api/notifications/$NOTIF_ID/read" \
  -H "Authorization: Bearer $TOKEN_JEFE" \
  -w "\nHTTP Status: %{http_code}\n"
```

**Respuesta esperada (200):**
```json
{
  "id": "<uuid>",
  "type": "PURPOSE_APPROVED",
  "title": "...",
  "message": "...",
  "referenceId": "<uuid>",
  "read": true,
  "createdAt": "2026-06-16T10:00:00"
}
```
**Error (400):** Si `$NOTIF_ID` pertenece a otro usuario — `"No puedes marcar notificaciones de otro usuario"`.

---

### 6.4 Marcar todas como leídas

```bash
curl -s -X PATCH http://localhost:8080/api/notifications/read-all \
  -H "Authorization: Bearer $TOKEN_JEFE" \
  -w "\nHTTP Status: %{http_code}\n"
```

**Respuesta:** `204 No Content`. Ejecuta un UPDATE masivo en BD — más eficiente que marcar una por una.

---

### 6.5 Flujo completo: generar y consumir una notificación

```bash
# Paso 1: JEFE_DOMINIO crea una solicitud de propósito
SOLICITUD_ID=$(curl -s -X POST http://localhost:8080/api/purpose-requests \
  -H "Authorization: Bearer $TOKEN_JEFE" \
  -H "Content-Type: application/json" \
  -d "{\"domainId\":\"$DOMAIN_ID\",\"title\":\"Datos de ventas\",\"justification\":\"Análisis de comportamiento\",\"requestedData\":\"email, historial de compras\"}" \
  | jq -r '.id')

echo "Solicitud creada: $SOLICITUD_ID"

# Paso 2: DPO la aprueba
curl -s -X PATCH "http://localhost:8080/api/purpose-requests/$SOLICITUD_ID/review" \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{"status":"APPROVED","reviewNotes":"Propósito válido según Ley 21.719"}' | jq .

# Paso 3: JEFE_DOMINIO consulta sus notificaciones — debe aparecer PURPOSE_APPROVED
curl -s http://localhost:8080/api/notifications \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq '.[0]'

# Paso 4: verificar conteo
curl -s http://localhost:8080/api/notifications/unread-count \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

---

## Tabla resumen de errores HTTP

| Código | Status | Cuándo ocurre |
|--------|--------|---------------|
| 400 | BAD_REQUEST | Datos inválidos, campo faltante, regla de negocio violada |
| 401 | UNAUTHORIZED | Token expirado, inválido o ausente |
| 403 | FORBIDDEN | Rol insuficiente, usuario bloqueado o desactivado |
| 404 | NOT_FOUND | Recurso no existe (usuario, dominio, documento) |
| 409 | CONFLICT | Recurso duplicado, estado incorrecto para la operación |
| 422 | UNPROCESSABLE_ENTITY | Validación de negocio fallida (privacydoc) |
| 500 | INTERNAL_SERVER_ERROR | Error inesperado del servidor |

**Formato estándar de error:**
```json
{
  "timestamp": "2026-06-15T10:00:00",
  "status": "NOT_FOUND",
  "code": 404,
  "message": "Usuario no encontrado: 550e8400-..."
}
```

---

## Flujos completos de prueba

### Flujo A — Crear usuario JEFE_DOMINIO y asignar dominio

```bash
# 1. Crear dominio
DOMAIN_ID=$(curl -s -X POST http://localhost:8080/api/domains \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"code":"IT","name":"Tecnología","description":"Dominio TI"}' \
  | jq -r '.domainId')

# 2. Crear usuario JEFE_DOMINIO
USER_ID=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"jefe@leydata.cl\",\"name\":\"Jefe TI\",\"password\":\"Jefe1234!\",\"roleCode\":\"JEFE_DOMINIO\",\"domainIds\":[\"$DOMAIN_ID\"]}" \
  | jq -r '.userId')

echo "Dominio: $DOMAIN_ID | Usuario: $USER_ID"
```

### Flujo B — Ciclo completo de solicitud de propósito

```bash
# 1. JEFE crea solicitud
REQUEST_ID=$(curl -s -X POST http://localhost:8080/api/purpose-requests \
  -H "Authorization: Bearer $TOKEN_JEFE" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Datos de nómina\",\"justification\":\"Pago mensual\",\"requestedData\":\"Nombre, RUT\",\"domainId\":\"$DOMAIN_ID\"}" \
  | jq -r '.request.id')

# 2. DPO aprueba
curl -s -X PATCH http://localhost:8080/api/purpose-requests/$REQUEST_ID/review \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d '{"status":"APPROVED","reviewNotes":"Conforme con Ley 21.719"}' | jq .
```

### Flujo C — Ciclo completo de documento de privacidad

```bash
# 1. DPO crea en DRAFT (solo DPO puede crear documentos de privacidad)
DOC_ID=$(curl -s -X POST http://localhost:8080/api/privacy-documents \
  -H "Authorization: Bearer $TOKEN_DPO" \
  -H "Content-Type: application/json" \
  -d "{\"category\":\"POLITICA_PRIVACIDAD\",\"name\":\"Política RRHH\",\"content\":\"En cumplimiento de Ley 21.719...\"}" \
  | jq -r '.id')

# 2. DPO envía a revisión
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/submit \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .status

# 3. DPO aprueba
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/approve \
  -H "Authorization: Bearer $TOKEN_DPO" | jq .status

# 4. DPO publica y genera PDF
curl -s -X POST http://localhost:8080/api/privacy-documents/$DOC_ID/publish \
  -H "Authorization: Bearer $TOKEN_DPO" | jq '{status: .status, hasPdf: .hasPdf, hash: .hashSha256}'

# 5. Descargar PDF
curl -s -o politica_rrhh.pdf http://localhost:8080/api/privacy-documents/$DOC_ID/pdf \
  -H "Authorization: Bearer $TOKEN_JEFE"

# 6. Verificar integridad
curl -s http://localhost:8080/api/privacy-documents/$DOC_ID/verify \
  -H "Authorization: Bearer $TOKEN_JEFE" | jq .
```

---

## Apéndice — Equivalentes para Windows PowerShell

PowerShell nativo no entiende funciones bash ni `$(...)` de la misma forma. Usar `curl.exe` (el binario real que viene con Windows 10+, no el alias `curl` de PowerShell que apunta a `Invoke-WebRequest`).

> **Prerequisito:** instalar `jq` con `winget install jqlang.jq` y reiniciar la terminal.

### Setup de tokens en PowerShell

```powershell
# Obtener token ADMIN
$TOKEN_ADMIN = (curl.exe -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!" `
  | jq -r '.access_token')

# Obtener token DPO
$TOKEN_DPO = (curl.exe -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password&client_id=leydata-frontend&username=dpo@leydata.cl&password=Dpo1234!" `
  | jq -r '.access_token')

# Obtener token JEFE_DOMINIO
$TOKEN_JEFE = (curl.exe -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password&client_id=leydata-frontend&username=jefe@leydata.cl&password=Jefe1234!" `
  | jq -r '.access_token')
```

### Ejemplos de endpoints en PowerShell

```powershell
# Listar usuarios
curl.exe -s http://localhost:8080/api/users `
  -H "Authorization: Bearer $TOKEN_ADMIN" | jq .

# Crear dominio
curl.exe -s -X POST http://localhost:8080/api/domains `
  -H "Authorization: Bearer $TOKEN_ADMIN" `
  -H "Content-Type: application/json" `
  -d '{"code":"IT","name":"Tecnologia","description":"Dominio TI"}' | jq .

# Capturar ID de respuesta
$DOMAIN_ID = (curl.exe -s -X POST http://localhost:8080/api/domains `
  -H "Authorization: Bearer $TOKEN_ADMIN" `
  -H "Content-Type: application/json" `
  -d '{"code":"RRHH","name":"Recursos Humanos","description":"RRHH"}' `
  | jq -r '.domainId')

# Crear usuario con el domainId capturado
$body = "{`"email`":`"jefe@leydata.cl`",`"name`":`"Jefe TI`",`"password`":`"Jefe1234!`",`"roleCode`":`"JEFE_DOMINIO`",`"domainIds`":[`"$DOMAIN_ID`"]}"
curl.exe -s -X POST http://localhost:8080/api/users `
  -H "Authorization: Bearer $TOKEN_ADMIN" `
  -H "Content-Type: application/json" `
  -d $body | jq .

# Descargar PDF (PowerShell)
curl.exe -s -o documento.pdf `
  "http://localhost:8080/api/privacy-documents/$DOC_ID/pdf" `
  -H "Authorization: Bearer $TOKEN_JEFE"
```

> **Diferencias clave vs bash:**
> - Usar `curl.exe` (con `.exe`) para forzar el binario real, no el alias de PowerShell
> - Continuación de línea con backtick `` ` `` en lugar de `\`
> - Variables con `$NOMBRE` igual que bash, pero las funciones no existen — repetir el comando completo
> - JSON con comillas anidadas necesita escapado con backtick: `` `" `` dentro de strings
> - Alternativa más limpia para JSON complejo: guardarlo en un archivo `.json` y usar `-d "@body.json"`

### Alternativa con archivo JSON (PowerShell — más limpio)

```powershell
# Guardar el body en un archivo temporal
@'
{
  "email": "jefe@leydata.cl",
  "name": "Jefe TI",
  "password": "Jefe1234!",
  "roleCode": "JEFE_DOMINIO",
  "domainIds": []
}
'@ | Out-File -Encoding utf8 body.json

# Usar el archivo en el request
curl.exe -s -X POST http://localhost:8080/api/users `
  -H "Authorization: Bearer $TOKEN_ADMIN" `
  -H "Content-Type: application/json" `
  -d "@body.json" | jq .

# Limpiar
Remove-Item body.json
```
