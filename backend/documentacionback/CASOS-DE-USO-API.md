# Casos de uso de la API — LeyData Backend

**Base URL:** `http://localhost:8080`  
**Autenticación:** `Authorization: Bearer <JWT de Keycloak>`  
**Roles disponibles:** `ADMIN`, `DPO`, `JEFE_DOMINIO`, `USER`, `TITULAR`

---

## Cómo funciona el sistema en términos generales

LeyData es un sistema de gestión de consentimiento que cumple con la Ley 21.719 de protección de datos personales de Chile. Su propósito es que una organización pueda documentar, gestionar y demostrar ante la ley de qué datos trata, con qué fundamento legal, por cuánto tiempo los retiene, y que los titulares de esos datos hayan sido informados o hayan dado su consentimiento explícito.

El sistema no gestiona directamente los datos personales de los ciudadanos — gestiona el **marco legal y documental** que permite tratarlos. Piénsalo como el expediente legal que respalda cada operación de tratamiento de datos.

El flujo de trabajo completo de la organización es el siguiente:

1. El **ADMIN** crea los usuarios del sistema y los dominios organizacionales (áreas de la empresa).
2. El **JEFE_DOMINIO** abre una solicitud de finalidad (PurposeRequest) para su área. Ejemplo: "quiero enviar newsletters a clientes de marketing".
3. El **DPO** revisa y aprueba la solicitud.
4. El **DPO** crea formalmente la **Finalidad** (Purpose) — la unidad atómica de permiso que el sistema evaluará como `true/false` antes de dejar que cualquier proceso interno toque un dato. La finalidad declara: qué hace, su base legal, si es obligatoria, si es revocable.
5. El **DPO** configura qué categorías de datos procesa esa finalidad y por cuánto tiempo se retienen (política de retención por combinación finalidad+categoría).
6. El **DPO** crea un **Template de consentimiento** que agrupa las finalidades aprobadas, define el orden y visibilidad de cada una, lo aprueba y activa. Al activarlo se sella el template con un hash SHA-256 y cualquier versión anterior del mismo `TEMPLATE_KEY` queda desactivada.
7. El **DPO** crea un Documento de Privacidad que agrupa una o más finalidades, lo somete a revisión, lo aprueba y lo publica. Al publicarlo se genera un PDF firmado con SHA-256 y la finalidad queda inmutable.
8. El JEFE_DOMINIO que originó la solicitud recibe una notificación de que su finalidad fue publicada.
9. El titular recibe o puede consultar ese documento publicado para conocer el tratamiento que se hace de sus datos.
10. El **orquestador** consulta `GET /api/agreements/active` para verificar si el titular ya tiene un consentimiento activo para ese template. Si no lo tiene, presenta el formulario de consentimiento.
11. El titular firma → se crea un **Agreement** (`POST /api/agreements`) que registra su decisión por cada finalidad. El sistema calcula un hash SHA-256 encadenado al agreement anterior del mismo titular (ledger de integridad). Si ya existía un Agreement ACTIVE para el mismo `(dataSubjectId, templateId)`, se revoca automáticamente y sus purposes pasan a REVOKED (reconsent).
12. Toda acción del sistema queda registrada en un log de auditoría con cadena de hashes inmutable.

---

## Índice

1. [Autenticación](#1-autenticación)
2. [Usuarios](#2-usuarios)
3. [Dominios](#3-dominios)
4. [Solicitudes de Finalidad](#4-solicitudes-de-finalidad)
5. [Finalidades](#5-finalidades)
6. [Legal Basis](#6-legal-basis)
7. [Categorías de Datos](#7-categorías-de-datos)
8. [Categorías por Finalidad + Retención](#8-categorías-por-finalidad--retención)
9. [Documentos de Privacidad](#9-documentos-de-privacidad)
10. [Auditoría](#10-auditoría)
11. [Notificaciones](#11-notificaciones)
12. [Templates de consentimiento](#12-templates-de-consentimiento)
13. [Agreements (consentimiento)](#13-agreements-consentimiento)
14. [Referencia de errores](#14-referencia-rápida-de-códigos-de-error)

---

## 1. Autenticación

### ¿Qué hace este módulo?

La autenticación está delegada completamente a **Keycloak**, un servidor de identidad externo. El backend de LeyData no almacena ni valida contraseñas — solo verifica que el JWT que llega en cada request fue emitido por el realm `leydata` de Keycloak.

Sin embargo, tener un token válido de Keycloak no es suficiente para operar. El sistema tiene una segunda capa de control: el `UserStatusFilter`. Este filtro, que se ejecuta en cada request, busca al usuario en la base de datos local y verifica que no esté suspendido ni bloqueado. Un usuario puede tener un token perfectamente válido en Keycloak y aun así recibir un 403 si fue bloqueado en LeyData.

Cuando el ADMIN bloquea a un operador, el sistema realiza dos acciones en secuencia: primero deshabilita al usuario en Keycloak (impide nuevos logins y renovación de tokens), y luego registra el bloqueo en la BD local (`user_status`). El `UserStatusFilter` corta el acceso de los tokens ya emitidos durante la ventana hasta que expiren (~5 minutos por configuración del realm).

---

### `POST /api/auth/login`
**Acceso:** público, sin token

Este endpoint es el punto de entrada al sistema. Recibe credenciales, las valida contra Keycloak, y devuelve el JWT que se debe incluir en todos los requests posteriores como header `Authorization: Bearer <token>`.

**Caso positivo — login exitoso**
```json
// Request
{ "email": "admin@empresa.cl", "password": "Admin1234!" }

// Response 200
{
  "token": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 300
}
```

**Caso negativo — credenciales incorrectas**
```json
// Response 401
{ "status": "UNAUTHORIZED", "code": 401, "message": "Acceso no autorizado. Por favor, inicia sesión nuevamente" }
```

**Caso negativo — usuario con token válido pero bloqueado en LeyData**

Este es el caso más importante de entender. El usuario inicia sesión normalmente en Keycloak y obtiene un JWT válido. Pero al consumir cualquier endpoint del sistema, el `UserStatusFilter` intercepta el request, consulta la BD local, encuentra `blocked=true` o `active=false` y rechaza el acceso antes de que llegue al controlador.

```
JWT válido de Keycloak → UserStatusFilter → blocked=true → 403 FORBIDDEN
```

```json
// Response 403 en cualquier endpoint protegido
{ "status": "FORBIDDEN", "code": 403, "message": "No tienes permiso para acceder a este recurso" }
```

---

## 2. Usuarios

### ¿Qué hace este módulo?

Gestiona el ciclo de vida de los operadores del sistema (no de los titulares de datos). Un operador puede ser un ADMIN, un DPO o un JEFE_DOMINIO.

**Modelo Keycloak-first completo:** Keycloak es la única fuente de verdad para identidad, contraseñas, tokens, roles y estado activo/inactivo. La BD local ya no almacena usuarios — solo los dominios asignados (`user_domains`) y los bloqueos permanentes (`user_status`), ambos indexados por `keycloak_id`.

El **identificador de usuario en todos los endpoints** es el `keycloak_id` (el claim `sub` del JWT), no un UUID local.

- `GET /api/users` lista directamente desde Keycloak — devuelve todos los usuarios del realm sin depender de la BD local. Los datos de dominio y bloqueo se enriquecen desde `user_domains` y `user_status`.
- Al crear un usuario, el sistema lo registra en Keycloak. Si la asignación de dominios falla, el sistema borra automáticamente al usuario de Keycloak como compensación.
- Al bloquear o desactivar, el sistema deshabilita en Keycloak (impide login y refresh). El bloqueo además registra una fila en `user_status` para que el `UserStatusFilter` corte tokens ya emitidos.

**Reglas de negocio del módulo:**
- Un usuario nunca se elimina físicamente. Solo puede desactivarse (suspensión temporal, reversible) o bloquearse (permanente e irreversible desde la API).
- **Un ADMIN no puede bloquearse ni desactivarse a sí mismo.** El sistema rechaza la operación con `400`.
- **Un ADMIN no puede bloquear ni desactivar a otro ADMIN.** El sistema rechaza la operación con `400`.
- Al quitar el rol `JEFE_DOMINIO` a un usuario, el sistema limpia automáticamente todos los dominios que tenía asignados, porque sin ese rol no tiene sentido tener dominios.
- Solo usuarios activos con rol `JEFE_DOMINIO` pueden tener dominios asignados.
- Solo se pueden asignar dominios que estén activos.

> **Todos los endpoints de este módulo requieren rol `ADMIN`.**

---

### `GET /api/users`

Lista todos los usuarios del sistema. Los datos vienen de Keycloak (roles) enriquecidos con datos locales (dominios, estado `blocked`).

Acepta parámetros opcionales — usar uno a la vez:

| Parámetro | Valores | Descripción |
|---|---|---|
| `search` | texto libre | Busca por nombre, email o username. Delegado a Keycloak. |
| `status` | `active` \| `inactive` \| `blocked` | Filtra por estado del usuario |
| `role` | `ADMIN` \| `DPO` \| `JEFE_DOMINIO` \| `USER` \| `TITULAR` | Filtra por rol exacto |

```
GET /api/users                    → todos
GET /api/users?search=juan        → usuarios cuyo nombre o email contiene "juan"
GET /api/users?status=active      → activos y no bloqueados
GET /api/users?status=inactive    → desactivados (active=false y no bloqueados)
GET /api/users?status=blocked     → bloqueados permanentemente
GET /api/users?role=DPO           → solo usuarios con rol DPO
```

```json
// Response 200
{
  "status": "success",
  "users": [
    {
      "keycloakId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "email": "pedro@empresa.cl",
      "name": "Pedro López",
      "active": true,
      "blocked": false,
      "roles": ["JEFE_DOMINIO"],
      "domains": ["Marketing", "Legal"]
    }
  ]
}
```

---

### `POST /api/users`

El ADMIN crea un nuevo operador del sistema. La operación es atómica: si algo falla, el usuario no queda en ningún lado.

**Caso positivo**
```json
// Request
{
  "email": "maria@empresa.cl",
  "name": "María González",
  "role": "DPO",
  "password": "Temporal123!"
}

// Response 201
{
  "status": "success",
  "message": "Usuario creado correctamente.",
  "keycloakId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Caso negativo — email ya registrado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Ya existe un usuario con el email maria@empresa.cl" }
```

**Caso negativo — falla la asignación de dominios después de crear en Keycloak**
```
1. Keycloak crea el usuario ✅
2. Asignación de dominio falla (dominio inactivo, no encontrado, etc.) ❌
3. Sistema compensa: keycloak.deleteUser(keycloakId) — el usuario desaparece de Keycloak
4. Response 500 — el usuario no quedó en ningún sistema
```

---

### `PUT /api/users/{userId}`

El ADMIN puede editar el nombre, email, contraseña, roles y dominios asignados de un usuario. Todos los campos son opcionales — solo se actualiza lo que se envía.

Al cambiar la contraseña, Keycloak la establece como **temporal** (`required_action: UPDATE_PASSWORD`). En el próximo login, Keycloak exige al usuario que defina una nueva contraseña antes de continuar — el backend no necesita hacer nada adicional, Keycloak lo maneja nativamente.

Al editar roles, el sistema evalúa si el nuevo conjunto incluye `JEFE_DOMINIO`. Si no lo incluye y el usuario tenía dominios asignados, esos dominios se limpian automáticamente.

**Caso positivo — cambiar email y contraseña**
```json
// Request
{
  "email": "nuevoemail@empresa.cl",
  "password": "NuevaPass123!"
}

// Response 200
{ "status": "success", "message": "Usuario actualizado correctamente", "user": { ... } }
```

En el próximo login del usuario, Keycloak le pedirá que defina una nueva contraseña antes de emitir el token.

**Caso positivo — promover a JEFE_DOMINIO con dominios**
```json
// Request
{
  "name": "Pedro López",
  "roleCodes": ["JEFE_DOMINIO"],
  "domainIds": ["uuid-marketing", "uuid-legal"]
}

// Response 200
{ "status": "success", "message": "Usuario actualizado correctamente", "user": { ... } }
```

**Caso negativo — asignar dominios a usuario sin rol JEFE_DOMINIO**

El sistema detecta que `domainIds` tiene contenido pero el rol `JEFE_DOMINIO` no está en el nuevo conjunto de roles enviado.

```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "Solo los usuarios con rol JEFE_DOMINIO pueden tener dominios asignados" }
```

**Caso negativo — asignar dominio inactivo**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "El dominio uuid-marketing no está activo" }
```

**Comportamiento silencioso — quitarle JEFE_DOMINIO a un usuario que lo tenía**
```
PUT { "roles": ["USER"], "domainIds": [] }
→ UserService detecta que JEFE_DOMINIO no está en el nuevo set de roles
→ Limpia automáticamente todos los registros de user_domains de ese usuario
→ Response 200 — sin error, el cambio es parte del flujo normal
```

---

### `POST /api/users/{userId}/block`

El bloqueo es permanente e irreversible desde la API. Representa un incidente de seguridad o una salida definitiva del operador.

**Qué hace el sistema al bloquear:**
1. `keycloak.disableUser()` — deshabilita al usuario en Keycloak: no puede hacer login nuevo ni renovar su token.
2. Crea un registro en `user_status` con `blocked=true` — el `UserStatusFilter` usa esto para rechazar tokens ya emitidos durante los ~5 minutos hasta que expiren.

Una vez bloqueado, el único camino para desbloquear es intervenir directamente en Keycloak a nivel de administración (habilitar el usuario en el Admin Console y limpiar `user_status`).

**Caso positivo**
```json
// Response 200
{ "status": "success", "message": "Usuario bloqueado permanentemente", "user": { "blocked": true } }
```

**Caso negativo — intento de bloquearse a sí mismo o bloquear a otro ADMIN**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "Un administrador no puede bloquearse a sí mismo" }
{ "status": "BAD_REQUEST", "code": 400, "message": "No se puede bloquear a otro administrador" }
```

**Caso negativo — usuario ya estaba bloqueado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "El usuario ya está bloqueado" }
```

---

### `POST /api/users/{userId}/deactivate` y `/reactivate`

La desactivación es una suspensión temporal reversible.

**Al desactivar:** el sistema llama a `keycloak.disableUser()` (impide nuevos logins y refresh). El estado `active` se lee en tiempo real desde Keycloak — no hay escritura en BD local.

**Al reactivar:** el sistema llama a `keycloak.enableUser()` (restaura la capacidad de login en Keycloak). El usuario puede volver a autenticarse normalmente.

**Caso negativo — intento de desactivarse a sí mismo o desactivar a otro ADMIN**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "Un administrador no puede desactivarse a sí mismo" }
{ "status": "BAD_REQUEST", "code": 400, "message": "No se puede desactivar a otro administrador" }
```

**Caso negativo — intentar desactivar o reactivar un usuario bloqueado**

Un usuario bloqueado está en un estado terminal. El sistema no permite transiciones desde ese estado.

```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "No se puede desactivar un usuario bloqueado" }
```

---

## 3. Dominios

### ¿Qué hace este módulo?

Un dominio representa un área organizacional de la empresa (Marketing, Legal, Recursos Humanos, etc.). Los dominios son el contenedor organizacional de las finalidades de tratamiento de datos. Cada finalidad pertenece a un dominio, y los JEFE_DOMINIO solo pueden crear solicitudes para los dominios que tienen asignados.

Los dominios siguen el mismo patrón de soft-delete que los usuarios: nunca se eliminan físicamente porque las finalidades, solicitudes y acuerdos históricos los referencian. Eliminar un dominio rompería la trazabilidad del tratamiento de datos, lo cual es inaceptable bajo la Ley 21.719.

**Reglas de negocio del módulo:**
- Un dominio puede existir sin JEFE_DOMINIO asignado. En ese caso, simplemente no llegan solicitudes nuevas desde él hasta que se asigne uno.
- Desactivar un dominio no cancela sus solicitudes pendientes ni desactiva sus finalidades aprobadas. Es una señal organizacional, no una cascada técnica.
- El código del dominio (ej. `"mkt"`) es único e inmutable — identifica al dominio en los logs.

> **Todos los endpoints requieren rol `ADMIN`.**

---

### `POST /api/domains`

**Caso positivo — con JEFE_DOMINIO asignado desde el inicio**
```json
// Request
{
  "code": "mkt",
  "name": "Marketing",
  "description": "Área de marketing digital y campañas",
  "jefeId": "uuid-pedro"
}

// Response 201
{ "status": "success", "message": "Dominio creado correctamente", "domainId": "uuid-nuevo" }
```

**Caso positivo — sin JEFE_DOMINIO**
```json
// Request
{ "code": "legal", "name": "Legal", "description": "Área jurídica" }

// Response 201 — el JEFE_DOMINIO se asigna después desde la edición de usuario
```

**Caso negativo — código de dominio ya registrado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Ya existe un dominio con el código mkt" }
```

**Caso negativo — jefeId apunta a usuario sin el rol correcto**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "El usuario uuid-pedro no tiene el rol JEFE_DOMINIO" }
```

---

### `POST /api/domains/{domainId}/deactivate` y `/reactivate`

**Comportamiento importante al desactivar:** el sistema no cancela solicitudes pendientes ni desactiva las finalidades del dominio. El DPO debe gestionar manualmente esas situaciones. La desactivación es solo una señal que indica que el área ya no opera.

**Caso negativo — dominio no encontrado**
```json
// Response 404
{ "status": "NOT_FOUND", "code": 404, "message": "Dominio no encontrado: uuid-xxx" }
```

---

## 4. Solicitudes de Finalidad

### ¿Qué hace este módulo?

Las solicitudes de finalidad son el mecanismo por el cual un JEFE_DOMINIO propone al DPO una nueva actividad de tratamiento de datos. Representa el punto de entrada del ciclo de vida de una finalidad.

El flujo es siempre el mismo: el JEFE_DOMINIO describe qué quiere hacer con datos personales, justifica por qué lo necesita, y el DPO decide si la finalidad es legalmente válida bajo la Ley 21.719. Si aprueba, el sistema crea automáticamente la Finalidad (Purpose) lista para ser configurada con categorías de datos y plazo de retención.

**Reglas de negocio del módulo:**
- Un JEFE_DOMINIO solo puede crear solicitudes para dominios que tiene asignados.
- No puede crear dos solicitudes con el mismo título para el mismo dominio mientras la primera sigue en estado PENDING. Debe esperar la resolución del DPO antes de reenviar.
- Si la solicitud es rechazada, puede crearla de nuevo (posiblemente corregida). El bloqueo aplica solo mientras está pendiente de revisión.
- El DPO debe justificar siempre el rechazo. Esta exigencia viene del principio de transparencia de la Ley 21.719.
- El ADMIN puede ver todas las solicitudes pero solo el DPO puede revisar (aprobar o rechazar).

---

### `POST /api/purpose-requests`
**Rol:** `JEFE_DOMINIO`

**Caso positivo**
```json
// Request
{
  "domainId": "uuid-marketing",
  "title": "Newsletter de ofertas",
  "justification": "Envío de comunicaciones comerciales a clientes suscritos",
  "requestedData": "Email, nombre, historial de compras"
}

// Response 201
{
  "status": "success",
  "message": "Solicitud enviada al DPO correctamente",
  "request": { "id": "uuid", "status": "PENDING", "title": "Newsletter de ofertas" }
}
```

**Caso negativo — JEFE_DOMINIO no tiene asignado el dominio**

El sistema verifica en la tabla `user_domains` que existe un vínculo entre el usuario autenticado y el dominio solicitado.

```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "No puedes crear solicitudes para un dominio que no te pertenece" }
```

**Caso negativo — solicitud duplicada en PENDING**

Si el mismo JEFE_DOMINIO ya tiene una solicitud con el mismo título para el mismo dominio esperando revisión, el sistema la bloquea. Esto evita que el DPO reciba múltiples copias idénticas de la misma solicitud.

```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Ya tienes una solicitud pendiente con el título 'Newsletter de ofertas' para este dominio. Espera la revisión del DPO antes de reenviarla." }
```

**Caso negativo — dominio desactivado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "El dominio está desactivado: Marketing" }
```

---

### `GET /api/purpose-requests/my`
**Rol:** `JEFE_DOMINIO`

Permite al jefe ver el historial de todas sus solicitudes y su estado actual. Es su ventana para saber si el DPO aprobó, rechazó o sigue evaluando sus pedidos.

```json
// Response 200
{
  "status": "success",
  "requests": [
    { "id": "uuid", "title": "Newsletter", "status": "PENDING" },
    { "id": "uuid", "title": "Publicidad segmentada", "status": "APPROVED" },
    { "id": "uuid", "title": "Campaña SMS", "status": "REJECTED", "reviewNotes": "Falta base legal" }
  ]
}
```

---

### `PATCH /api/purpose-requests/{requestId}/review`
**Rol:** `DPO`

El DPO revisa una solicitud y toma una decisión. Si aprueba, el sistema crea automáticamente la Finalidad (Purpose) con los datos de la solicitud. Si rechaza, el motivo queda registrado y el JEFE_DOMINIO recibe una notificación.

**Caso positivo — aprobación**
```json
// Request
{ "status": "APPROVED", "reviewNotes": "Finalidad válida bajo Art. 12 Ley 21.719" }

// Response 200 — se crea automáticamente la Purpose vinculada
{ "status": "success", "request": { "status": "APPROVED" } }
```

**Caso positivo — rechazo con motivo**
```json
// Request
{ "status": "REJECTED", "reviewNotes": "Falta especificar el plazo de retención de los datos" }

// Response 200
{ "request": { "status": "REJECTED", "reviewNotes": "Falta especificar..." } }
```

**Caso negativo — rechazo sin motivo**

La ley exige transparencia. El DPO no puede simplemente rechazar sin explicar por qué.

```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "El DPO debe justificar el rechazo en las notas de revisión (Ley 21.719)" }
```

**Caso negativo — solicitud ya revisada**

Una vez que el DPO tomó una decisión, no puede revisarla nuevamente. Si hay un error, el JEFE_DOMINIO debe crear una nueva solicitud.

```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "La solicitud ya fue revisada. Estado actual: APPROVED" }
```

---

## 5. Finalidades

### ¿Qué es una Finalidad?

La Finalidad es la **unidad atómica de permiso** del sistema. Es la respuesta exacta a la pregunta que exige la Ley 21.719: *"¿Para qué vas a usar mi información?"*

Cada Finalidad define **una sola acción específica** (ej. "Enviar promociones", "Facturar servicio", "Compartir con terceros"). Es el permiso individual que el motor evaluará como `true/false` antes de dejar que cualquier proceso interno toque un dato.

**Distinción clave:**
- **PurposeRequest** → el ticket de solicitud (JEFE_DOMINIO pide, DPO revisa)
- **Purpose** → la definición formal operacional que el DPO crea y que el motor consulta
- **Template** → la agrupación visual que el titular lee (agrupa múltiples Purposes)

**Campos clave:**
- `consentStatement` → texto exacto que el titular lee y acepta (ej. *"Al aceptar, autorizo a [X] a utilizar mi correo para enviar ofertas durante 365 días."*). Nullable — puede definirse después de crear la finalidad.
- `required: true` → el titular no puede rechazar esta finalidad (ej. facturación)
- `required: false` → el titular puede optar por no aceptarla (ej. marketing)
- `revocable: true` → puede retirar su consentimiento después de otorgarlo
- `locked: true` → la finalidad está en un documento PUBLISHED y es inmutable

### Endpoints

| Método | URL | Rol mínimo | Descripción |
|--------|-----|-----------|-------------|
| POST | `/api/purposes` | DPO | Crear finalidad |
| GET | `/api/purposes` | JEFE_DOMINIO | Listar activas (jefe ve solo su dominio) |
| GET | `/api/purposes/{id}` | JEFE_DOMINIO | Ver una finalidad |
| GET | `/api/purposes/domain/{domainId}` | JEFE_DOMINIO | Listar por dominio |
| PUT | `/api/purposes/{id}` | DPO | Editar (bloqueado si PUBLISHED) |
| DELETE | `/api/purposes/{id}` | DPO | Desactivar (soft delete) |

---

### CU-5.1 — DPO crea una finalidad

**Flujo:** El DPO define una nueva actividad de tratamiento de datos. Puede referenciar un PurposeRequest aprobado (para trazabilidad) o crearla directamente.

**Request exitoso:**
```
POST /api/purposes
Authorization: Bearer <token-DPO>

{
  "code": "MARKETING_NEWSLETTER",
  "name": "Envío de ofertas y promociones",
  "description": "Uso del correo electrónico y teléfono para enviar descuentos, boletines y material publicitario.",
  "shortDescription": "Envío de newsletters",
  "consentStatement": "Al aceptar, autorizo a [Organización] a utilizar mi correo electrónico para enviar ofertas y descuentos durante 365 días.",
  "required": false,
  "revocable": true,
  "presentationOrder": 3,
  "legalBasisId": "uuid-consentimiento",
  "domainId": "uuid-dominio-marketing",
  "purposeRequestId": "uuid-request-aprobado"
}
```

**Respuesta 201:**
```json
{
  "id": "uuid-purpose",
  "code": "MARKETING_NEWSLETTER",
  "name": "Envío de ofertas y promociones",
  "consentStatement": "Al aceptar, autorizo a [Organización] a utilizar mi correo electrónico para enviar ofertas y descuentos durante 365 días.",
  "required": false,
  "revocable": true,
  "legalBasisCode": "CONSENTIMIENTO",
  "domainName": "Marketing",
  "isActive": true,
  "locked": false,
  "approvedBy": "uuid-dpo",
  "createdAt": "2026-06-21T10:00:00"
}
```

**Regla de negocio:** `code` se normaliza a MAYÚSCULAS y es único en todo el sistema. `approvedBy` se registra automáticamente con el `keycloak_id` (claim `sub` del JWT) del DPO que crea — trazabilidad exacta en organizaciones con múltiples DPOs.

---

### CU-5.2 — Código duplicado

```
POST /api/purposes
Body: { "code": "MARKETING_NEWSLETTER", ... }  ← mismo código ya existente
```

**Respuesta 422:** `"Ya existe una finalidad con código: MARKETING_NEWSLETTER"`

---

### CU-5.3 — JEFE_DOMINIO lista las finalidades de su dominio

**Flujo:** El jefe del área de Marketing quiere ver qué finalidades están activas para su dominio.

```
GET /api/purposes
Authorization: Bearer <token-JEFE-marketing>
```

**Respuesta 200:** Solo las finalidades cuyo `domainId` pertenece a los dominios del jefe autenticado.

---

### CU-5.4 — JEFE_DOMINIO intenta ver finalidad de otro dominio

```
GET /api/purposes/{id-de-finalidad-de-RRHH}
Authorization: Bearer <token-JEFE-marketing>
```

**Respuesta 403:** `FORBIDDEN` — el jefe solo puede acceder a las finalidades de sus propios dominios.

---

### CU-5.5 — Editar finalidad no bloqueada

```
PUT /api/purposes/{id}
Authorization: Bearer <token-DPO>

{
  "name": "Envío de newsletter mensual",
  "required": false,
  "revocable": true
}
```

**Respuesta 200** — campos actualizados. `code` y `domainId` no se pueden cambiar.

---

### CU-5.6 — Editar finalidad bloqueada (en documento PUBLISHED)

```
PUT /api/purposes/{id}   ← finalidad vinculada a documento PUBLISHED
```

**Respuesta 422:** `"La finalidad 'Envío de newsletter mensual' está publicada en un documento activo y no puede modificarse. Crea una nueva versión del documento para desbloquearla."`

**Flujo de desbloqueo:** `POST /api/privacy-documents/{docId}/new-version` → crea nuevo DRAFT → la finalidad queda editable.

---

### CU-5.7 — Desactivar finalidad con categorías activas

```
DELETE /api/purposes/{id}   ← tiene PurposeDataCategories activos
```

**Respuesta 422:** `"La finalidad 'Newsletter' tiene categorías de datos activas. Desvincula primero todas las categorías antes de desactivar la finalidad."`

**Orden correcto:**
1. `DELETE /api/purposes/{id}/data-categories/{pdcId}` — desvincular cada categoría
2. `DELETE /api/purposes/{id}` — ahora sí, desactivar

---

### CU-5.8 — Notificación al publicar: el ticket del JEFE_DOMINIO queda cerrado

**Flujo:** El DPO publica un documento que contiene la finalidad "Newsletter" vinculada al PurposeRequest #42 de Juan (JEFE de Marketing).

- Al momento de la publicación, el sistema detecta que la finalidad tiene `purposeRequestId` apuntando al ticket de Juan.
- Se genera automáticamente una notificación `PURPOSE_REQUEST_FULFILLED` para Juan: *"La finalidad 'Envío de newsletter mensual' derivada de tu solicitud ha sido publicada en el documento 'Política de Marketing'."*
- Juan no necesita consultar el sistema activamente — recibe el cierre del ciclo en su bandeja.

---

## 6. Legal Basis (Bases de Licitud)

### ¿Qué hace este módulo?

Las bases de licitud son los fundamentos legales que justifican el tratamiento de datos personales. La Ley 21.719 define exactamente cuáles son — no se pueden inventar nuevas. Por eso este módulo es de **solo lectura**: el catálogo lo define la ley, no el DPO.

Este catálogo es crítico porque la base de licitud elegida al crear una finalidad determina todo el comportamiento posterior del sistema. Específicamente:

- Si la base es **CONSENTIMIENTO**: el titular debe otorgar una aprobación activa y explícita antes de que sus datos sean tratados. Puede revocarla en cualquier momento y el sistema cierra el procesamiento de inmediato.
- Si la base es **cualquier otra** (CONTRATO, OBLIGACION_LEGAL, etc.): el titular debe ser informado de que sus datos se tratan, pero no necesita dar permiso. No aplica la revocación.

Esta distinción es la bifurcación más importante del sistema — de ella depende si se genera un acuerdo de consentimiento activo o solo una notificación informativa.

> **Acceso:** `DPO`, `ADMIN`, `JEFE_DOMINIO`. Sin escritura desde la API.

---

### `GET /api/legal-basis`

Devuelve todas las bases de licitud activas del catálogo.

```json
// Response 200
[
  { "id": "uuid", "code": "CONSENTIMIENTO", "name": "Consentimiento del titular", "consentRequired": true, "description": "Art. 12 Ley 21.719" },
  { "id": "uuid", "code": "CONTRATO", "name": "Ejecución de contrato", "consentRequired": false, "description": "Art. 13 a) Ley 21.719" },
  { "id": "uuid", "code": "OBLIGACION_LEGAL", "name": "Obligación legal", "consentRequired": false, "description": "Art. 13 b) Ley 21.719" },
  { "id": "uuid", "code": "INTERES_VITAL", "name": "Protección de intereses vitales", "consentRequired": false },
  { "id": "uuid", "code": "INTERES_PUBLICO", "name": "Interés público", "consentRequired": false },
  { "id": "uuid", "code": "INTERES_LEGITIMO", "name": "Interés legítimo", "consentRequired": false }
]
```

### `GET /api/legal-basis/consent`

Devuelve solo las bases donde `consentRequired = true`. El frontend usa este endpoint para distinguir visualmente qué finalidades van a requerir una captura activa del titular.

### `GET /api/legal-basis/{id}`

**Caso negativo — ID no existe**
```json
// Response 404
{ "status": "NOT_FOUND", "code": 404, "message": "Base de licitud no encontrada: uuid-xxx" }
```

---

## 7. Categorías de Datos

### ¿Qué hace este módulo?

Las categorías de datos describen qué tipo de información personal trata una finalidad. Por ejemplo: datos de salud, datos financieros, datos de identificación, etc.

El catálogo es **híbrido**: hay categorías predefinidas por la ley (marcadas como `isSystem=true`) que no pueden modificarse ni desactivarse, y categorías custom que el DPO puede crear libremente para necesidades específicas de la organización.

Las categorías marcadas como **sensibles** (`isSensitive=true`) son aquellas que la Ley 21.719 lista en el Art. 16 como categorías especiales que requieren protección reforzada. Procesarlas implica obligaciones adicionales.

**Reglas de negocio del módulo:**
- Las 15 categorías sembradas inicialmente son del sistema (`isSystem=true`) y no pueden renombrarse ni desactivarse.
- Una categoría custom solo puede desactivarse si no está vinculada a ninguna finalidad activa. Si lo está, primero hay que desvincularla de todas las finalidades y luego desactivarla.
- El código de una categoría es siempre en mayúsculas y único.

> **Lectura:** `DPO`, `ADMIN`, `JEFE_DOMINIO`. **Escritura:** solo `DPO` y `ADMIN`.

---

### `GET /api/data-categories`

```json
// Response 200
[
  { "id": "uuid", "code": "SALUD", "name": "Datos de salud", "isSensitive": true, "isSystem": true, "isActive": true },
  { "id": "uuid", "code": "FINANCIERO", "name": "Datos financieros", "isSensitive": false, "isSystem": true, "isActive": true },
  { "id": "uuid", "code": "MASCOTA", "name": "Datos de mascotas del cliente", "isSensitive": false, "isSystem": false, "isActive": true }
]
```

### `GET /api/data-categories/sensitive`

Devuelve solo las categorías sensibles (Art. 16 Ley 21.719): `SALUD`, `BIOMETRICO`, `GENETICO`, `VIDA_SEXUAL`, `RELIGION`, `POLITICO`, `SINDICAL`, `RACIAL`.

---

### `POST /api/data-categories`

El DPO crea una categoría personalizada para la organización.

**Caso positivo**
```json
// Request
{ "code": "MASCOTA", "name": "Datos de mascotas del cliente", "isSensitive": false }

// Response 201
{ "id": "uuid", "code": "MASCOTA", "isSystem": false, "isActive": true }
```

**Caso negativo — código duplicado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Ya existe una categoría con código: MASCOTA" }
```

**Caso negativo — campos vacíos**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "code: no debe estar vacío" }
```

---

### `PUT /api/data-categories/{id}`

**Caso negativo — intentar editar categoría del sistema**

Las categorías sembradas por la ley son inmutables. El DPO no puede cambiarles el nombre ni la sensibilidad.

```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "Las categorías del sistema (Ley 21.719) no pueden modificarse." }
```

---

### `DELETE /api/data-categories/{id}`

Desactiva la categoría (soft-delete). No la elimina físicamente porque puede existir en el historial de documentos publicados.

**Caso positivo**
```json
// Response 200
{ "id": "uuid", "code": "MASCOTA", "isActive": false }
```

**Caso negativo — categoría del sistema**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "Las categorías del sistema (Ley 21.719) no pueden desactivarse." }
```

**Caso negativo — categoría vinculada a finalidades activas**

El sistema verifica antes de desactivar que ninguna finalidad activa esté usando esta categoría. Si hay vínculos activos, primero hay que desvincular la categoría de cada finalidad.

```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La categoría 'Datos de mascotas' está vinculada a una o más finalidades activas y no puede desactivarse. Desvincula primero la categoría de todas las finalidades." }
```

---

## 8. Categorías por Finalidad + Retención

### ¿Qué hace este módulo?

Una vez que existe una finalidad aprobada (ej. "Newsletter de Marketing"), el DPO debe declarar exactamente qué tipos de datos procesa esa finalidad, **cómo los usa**, y por cuánto tiempo los retiene. Esto es un requisito explícito de la Ley 21.719: el titular tiene derecho a saber qué datos se usan, para qué y cuándo serán eliminados o anonimizados.

**`dataUses` — usos declarados por categoría de dato:**

Cada vínculo finalidad-categoría debe declarar al menos un uso. Los valores posibles son:
- `STORAGE` — almacenamiento
- `PROCESSING` — procesamiento interno
- `TRANSFER_TO_THIRD_PARTIES` — cesión a terceros (máxima relevancia legal — Ley 21.719 exige mención explícita)
- `PROFILING` — elaboración de perfiles del titular
- `ANALYSIS` — análisis estadístico o interno

Una categoría puede tener múltiples usos simultáneos.

La política de retención (cuánto tiempo y bajo qué criterio) se define **por combinación de finalidad y categoría**. No es global. Por ejemplo: para la finalidad "Salud Ocupacional", los datos de salud se retienen 10 años, pero los datos de identificación solo 5 años.

**Regla crítica — Opción B de inmutabilidad:**

Una vez que una finalidad aparece en un documento publicado, su configuración de categorías y retención queda **bloqueada**. No se puede modificar el plazo de retención, no se puede desvincular una categoría, y tampoco se puede agregar una nueva. La razón es que el PDF publicado es el instrumento legal que el titular vio y aceptó. Cambiar esa configuración después de publicar equivale a tratar datos bajo condiciones que el titular nunca conoció.

Para hacer cambios, el DPO debe crear una nueva versión del documento. Eso genera un DRAFT sobre el que puede trabajar libremente. Cuando ese DRAFT se publica, los acuerdos anteriores siguen referenciando la versión antigua — el historial es inmutable.

> **Lectura:** `DPO`, `ADMIN`, `JEFE_DOMINIO`. **Escritura:** `DPO`, `ADMIN`.

---

### `GET /api/purposes/{purposeId}/data-categories`

Devuelve todas las categorías vinculadas a una finalidad. El campo `retentionLocked` indica si la configuración está bloqueada por un documento publicado. El frontend debe mostrar los campos de retención en modo solo lectura cuando este flag es `true`.

```json
// Response 200
[
  {
    "id": "uuid",
    "dataCategory": { "code": "SALUD", "name": "Datos de salud", "isSensitive": true },
    "required": true,
    "dataUses": ["STORAGE", "PROCESSING"],
    "retention": {
      "retentionPeriod": 10,
      "retentionUnit": "YEARS",
      "legalJustification": "Art. 17 Ley 21.719",
      "anonymizeAfter": true
    },
    "retentionLocked": true
  }
]
```

---

### `POST /api/purposes/{purposeId}/data-categories`

Vincula una categoría a la finalidad y define su política de retención en un solo paso. La retención es obligatoria — no puede quedar una categoría vinculada sin saber por cuánto tiempo se retienen esos datos.

**Caso positivo**
```json
// Request
{
  "dataCategoryId": "uuid-salud",
  "required": true,
  "dataUses": ["STORAGE", "PROCESSING"],
  "retention": {
    "retentionPeriod": 10,
    "retentionUnit": "YEARS",
    "legalJustification": "Art. 17 Ley 21.719",
    "anonymizeAfter": true
  }
}

// Response 201
{ "id": "uuid", "dataUses": ["STORAGE", "PROCESSING"], "retentionLocked": false }
```

**Caso negativo — la finalidad está en un documento PUBLISHED**

Agregar una nueva categoría a una finalidad publicada amplía el alcance del consentimiento sin que el titular lo sepa. El sistema lo bloquea completamente.

```json
// Response 409
{
  "status": "RETENTION_LOCKED",
  "code": 409,
  "message": "El período de retención de 'Newsletter de ofertas' está bloqueado porque está vinculada a un documento publicado. Crea una nueva versión del documento con POST /api/privacy-documents/{id}/new-version"
}
```

**Caso negativo — finalidad no aprobada**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La finalidad debe estar aprobada y activa para vincular categorías de datos." }
```

**Caso negativo — categoría inactiva**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La categoría de datos 'Datos de mascotas' está inactiva." }
```

**Caso negativo — vínculo duplicado**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "Esta categoría de datos ya está vinculada a la finalidad." }
```

**Caso negativo — `dataUses` vacío o ausente**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "dataUses: Debe declarar al menos un uso de la categoría de datos" }
```

**Caso negativo — unidad de retención inválida**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "retentionUnit: debe ser DAYS, MONTHS o YEARS" }
```

---

### `PUT /api/purposes/{purposeId}/data-categories/{id}/retention`

Permite actualizar el plazo de retención de una categoría ya vinculada. Solo disponible si no hay documento publicado que mencione esta finalidad.

**Caso positivo**
```json
// Request
{ "retentionPeriod": 5, "retentionUnit": "YEARS", "legalJustification": "Actualización normativa 2026", "anonymizeAfter": false }

// Response 200
{ "retentionLocked": false }
```

**Caso negativo — documento PUBLISHED**
```json
// Response 409
{ "status": "RETENTION_LOCKED", "code": 409, "message": "..." }
```

---

### `DELETE /api/purposes/{purposeId}/data-categories/{id}`

Desvincula una categoría de una finalidad y elimina su política de retención asociada. Bloqueado si hay documento publicado.

**Caso positivo**
```
Response 204 No Content
```

**Caso negativo — documento PUBLISHED**
```json
// Response 409
{ "status": "RETENTION_LOCKED", "code": 409, "message": "..." }
```

---

## 9. Documentos de Privacidad

### ¿Qué hace este módulo?

El documento de privacidad es el instrumento legal que agrupa una o más finalidades de tratamiento y que se comunica al titular. Cuando está publicado, contiene el PDF firmado que el titular puede descargar y verificar.

El flujo de estados de un documento es lineal y controlado:

```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
               ↓
           REJECTED → (corrección) → IN_REVIEW
```

Ninguna transición puede saltarse. No se puede publicar un DRAFT directamente — debe pasar por revisión y aprobación. Esto garantiza que siempre hay un par de ojos del DPO sobre el documento antes de que llegue al titular.

**Sistema de versiones por familia:**

Cada documento tiene un `documentFamilyId` que agrupa todas sus versiones. Cuando el DPO necesita actualizar un documento publicado, no lo edita — crea una nueva versión (DRAFT) dentro de la misma familia. El documento publicado anterior sigue vigente para los acuerdos ya firmados. Solo puede existir un DRAFT activo por familia a la vez.

**Control de acceso por contenido:**

DPO y ADMIN ven todos los documentos en cualquier estado, incluyendo el `content` completo y el `rejectionReason`. Cualquier otro rol (JEFE_DOMINIO, TITULAR) solo puede ver documentos PUBLISHED y recibe una versión reducida de la respuesta, sin el contenido interno ni el motivo de rechazo. Esto protege el proceso de elaboración del documento.

> **Escritura:** solo `DPO`. **Lectura básica:** cualquier usuario autenticado (solo PUBLISHED para no-DPO).

---

### `POST /api/privacy-documents`
**Rol:** `DPO`

Crea un nuevo documento en estado DRAFT. El `documentFamilyId` se auto-asigna igual al ID del documento creado — este documento es la raíz de su propia familia de versiones.

**Caso positivo**
```json
// Request
{
  "name": "Política de Privacidad — Marketing Digital",
  "category": "MARKETING",
  "content": "Este documento describe las condiciones bajo las cuales..."
}

// Response 201
{ "id": "uuid-v1", "status": "DRAFT", "version": 1, "documentFamilyId": "uuid-v1" }
```

---

### `GET /api/privacy-documents/{id}`

**Caso positivo — DPO consulta un DRAFT**
```json
// Response 200 — respuesta completa con content y rejectionReason
{ "id": "uuid", "status": "DRAFT", "content": "Este documento describe...", "rejectionReason": null }
```

**Caso negativo — JEFE_DOMINIO intenta ver un DRAFT**

El documento aún no está publicado. El sistema deniega el acceso a cualquier estado que no sea PUBLISHED para roles no privilegiados.

```json
// Response 403
{ "status": "FORBIDDEN", "code": 403, "message": "No tienes permiso para ver este documento" }
```

**Caso positivo — JEFE_DOMINIO consulta un documento PUBLISHED**
```json
// Response 200 — sin content ni rejectionReason
{ "id": "uuid", "status": "PUBLISHED", "name": "Política de Privacidad — Marketing Digital", "version": 2 }
```

---

### `PATCH /api/privacy-documents/{id}`
**Rol:** `DPO` — solo documentos en DRAFT.

**Caso negativo — intentar editar documento en IN_REVIEW**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La operación requiere estado DRAFT (actual: IN_REVIEW)" }
```

---

### `POST /api/privacy-documents/{id}/purposes/{purposeId}`
**Rol:** `DPO` — vincula una finalidad aprobada al documento DRAFT.

Un documento puede tener múltiples finalidades. Todas deben estar aprobadas para poder vincularlas.

**Caso negativo — documento no es DRAFT**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La operación requiere estado DRAFT (actual: IN_REVIEW)" }
```

---

### `POST /api/privacy-documents/{id}/submit`
**Rol:** `DPO` — DRAFT → IN_REVIEW

Antes de someter, el sistema valida que el documento tenga contenido y al menos una finalidad vinculada. Sin eso, no tiene sentido revisarlo.

**Caso negativo — documento sin finalidades**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "El documento debe tener al menos una finalidad activa vinculada" }
```

**Caso negativo — documento sin contenido**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "El contenido no puede estar vacío antes de enviar a revisión" }
```

---

### `POST /api/privacy-documents/{id}/approve` y `/reject`
**Rol:** `DPO` — solo documentos en IN_REVIEW.

Al aprobar, el documento queda listo para publicación. Al rechazar, vuelve al equipo que lo preparó para correcciones, y el motivo queda registrado.

**Caso negativo — transición inválida**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Transición inválida: DRAFT → APPROVED" }
```

**Caso negativo — rechazo sin motivo**
```json
// Response 400
{ "status": "BAD_REQUEST", "code": 400, "message": "rejectionReason: no debe estar vacío" }
```

---

### `POST /api/privacy-documents/{id}/publish`
**Rol:** `DPO` — APPROVED → PUBLISHED

Este es el evento más importante del módulo. Al publicar, el sistema genera automáticamente el PDF con todas las finalidades, sus categorías de datos y los plazos de retención. Calcula el SHA-256 del PDF y lo almacena junto con el binario. A partir de este momento, el documento es visible para titulares y JEFE_DOMINIO, y las configuraciones de sus finalidades quedan bloqueadas.

**Caso positivo**
```json
// Response 200
{ "status": "PUBLISHED", "version": 1, "hashSha256": "a3f9b2c1..." }
```

**Caso negativo — finalidades vinculadas no están todas aprobadas**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "Todas las finalidades vinculadas deben estar en estado APPROVED para publicar el documento." }
```

---

### `POST /api/privacy-documents/{id}/new-version`
**Rol:** `DPO`

Crea un nuevo DRAFT en la misma familia, copiando el contenido del documento actual. El documento origen sigue vigente. Solo puede existir un DRAFT activo por familia.

**Caso positivo**
```json
// Response 201
{
  "id": "uuid-v2",
  "status": "DRAFT",
  "version": 2,
  "documentFamilyId": "uuid-v1"
}
```

**Caso negativo — ya existe un DRAFT activo en esta familia**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "Ya existe un borrador activo en esta familia de documentos. Publicá o desactivá el DRAFT existente antes de crear una nueva versión." }
```

---

### `GET /api/privacy-documents/active?category=MARKETING`

Devuelve el documento PUBLISHED canónico de una categoría: el de mayor número de versión publicada.

**Caso negativo**
```json
// Response 404
{ "status": "NOT_FOUND", "code": 404, "message": "No hay documento publicado para la categoría MARKETING" }
```

---

### `GET /api/privacy-documents/{id}/pdf`

Descarga el PDF binario. Solo disponible para documentos PUBLISHED o ARCHIVED (que pasaron por el proceso de publicación y tienen el PDF generado).

```
Response 200 application/pdf
Content-Disposition: attachment; filename="privacy-document-uuid.pdf"
```

**Caso negativo — documento sin PDF generado (DRAFT o IN_REVIEW)**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "El documento no tiene PDF generado. Debe estar en estado PUBLISHED." }
```

---

### `GET /api/privacy-documents/{id}/verify`

Verifica la integridad del PDF almacenado comparando su SHA-256 actual con el hash registrado al momento de publicar. Si alguien modificó el PDF en la base de datos, los hashes no coincidirán.

**Caso positivo — PDF íntegro**
```json
// Response 200
{ "valid": true, "storedHash": "a3f9b2...", "computedHash": "a3f9b2...", "message": "Integridad verificada" }
```

**Caso negativo — hash no coincide**
```json
// Response 200 (no lanza error, informa)
{ "valid": false, "storedHash": "a3f9b2...", "computedHash": "zz1234...", "message": "⚠ ALERTA: el hash del PDF no coincide — posible corrupción o alteración" }
```

---

## 10. Auditoría

### ¿Qué hace este módulo?

El log de auditoría registra toda acción relevante que ocurre en el sistema: creación de usuarios, cambios de estado de documentos, aprobación de solicitudes, publicación de políticas, etc. Es la bitácora legal del sistema.

El log funciona como una cadena de bloques simplificada: cada registro incluye el hash SHA-256 del registro anterior, formando una cadena. Si alguien modifica un registro directamente en la base de datos, el hash de ese registro ya no coincide con el del siguiente, y la cadena se rompe. Esta cadena es verificable desde la API.

Como segunda capa de defensa, un trigger de PostgreSQL impide físicamente cualquier operación `UPDATE` o `DELETE` sobre la tabla `system_audit_log`. Es decir: los logs son inmutables tanto por lógica de aplicación como por restricción de base de datos.

> **Todos los endpoints requieren rol `ADMIN`.**

---

### `GET /api/audit/logs`

Permite al ADMIN consultar el historial de operaciones. Soporta filtros por acción, tabla afectada o actor, y devuelve resultados paginados.

```
GET /api/audit/logs?action=CREAR_USUARIO&page=0&size=10
GET /api/audit/logs?actorEmail=dpo@empresa.cl
GET /api/audit/logs?table=privacy_documents
```

**Caso positivo**
```json
// Response 200
{
  "status": "success",
  "logs": [
    {
      "id": "uuid",
      "tableName": "users",
      "action": "CREAR_USUARIO",
      "actorRole": "ADMIN",
      "ipAddress": "10.0.0.5",
      "newData": "{\"email\":\"maria@empresa.cl\"}",
      "logHash": "a3f9b2c1...",
      "createdAt": "2026-06-21T10:30:00"
    }
  ],
  "total": 45,
  "page": 0,
  "totalPages": 5
}
```

**Comportamiento especial — actorEmail no existe**

Si se filtra por un email que no existe en el sistema, el endpoint devuelve una lista vacía sin lanzar error. Esto es intencional para no revelar si un email existe o no.

```json
// Response 200
{ "status": "success", "logs": [], "total": 0, "totalPages": 0 }
```

---

### `GET /api/audit/logs/verify`

Recorre toda la cadena de hashes y verifica que ningún registro fue alterado. Es el instrumento para demostrar integridad ante auditores o reguladores.

**Caso positivo — cadena íntegra**
```json
// Response 200
{ "status": "success", "valid": true, "message": "Cadena de auditoría íntegra. Ningún registro ha sido alterado." }
```

**Caso negativo — cadena comprometida**
```json
// Response 200 (alerta, no excepción)
{ "status": "warning", "valid": false, "message": "ALERTA: Se detectaron inconsistencias en la cadena de auditoría. Posible alteración de registros." }
```

---

## 11. Notificaciones

### ¿Qué hace este módulo?

Las notificaciones son mensajes internos del sistema que informan a los operadores sobre eventos relevantes: una solicitud aprobada, un documento publicado, un rechazo con motivo. Son generadas automáticamente por otros módulos — no se crean desde la API directamente.

Cada usuario solo puede ver sus propias notificaciones. El sistema no expone notificaciones de otros usuarios.

> **Accesible por cualquier usuario autenticado.**

---

### `GET /api/notifications`

```json
// Response 200
[
  {
    "id": "uuid",
    "type": "PURPOSE_APPROVED",
    "title": "Solicitud aprobada: Newsletter de ofertas",
    "message": "Tu solicitud fue aprobada por el DPO.",
    "read": false,
    "createdAt": "2026-06-21T10:00:00"
  },
  {
    "id": "uuid",
    "type": "DOCUMENT_PUBLISHED",
    "title": "Documento publicado: Política de Marketing",
    "message": "El DPO publicó el documento 'Política de Marketing' para el dominio Marketing.",
    "read": true,
    "createdAt": "2026-06-20T15:30:00"
  }
]
```

### `GET /api/notifications/unread-count`

Útil para mostrar el badge de notificaciones no leídas en el frontend.

```json
// Response 200
{ "count": 3 }
```

### `PATCH /api/notifications/{id}/read`

Marca una notificación como leída. Si la notificación pertenece a otro usuario, el servicio la filtra y no la devuelve.

### `PATCH /api/notifications/read-all`

Marca todas las notificaciones del usuario autenticado como leídas.

```
Response 200 — sin cuerpo
```

---

## 12. Templates de consentimiento

### ¿Qué hace este módulo?

Un **Template** es una plantilla de consentimiento que agrupa y ordena Finalidades para ser presentadas al titular. Funciona como la capa de presentación del consentimiento: define qué finalidades se muestran, en qué orden y si son visibles u opcionales.

El módulo implementa versionado: cada `TEMPLATE_KEY` puede tener múltiples versiones, pero solo una puede estar activa en cada momento. Al activar una nueva versión, el sistema desactiva automáticamente la versión anterior del mismo `templateKey`. El contenido de cada template activo queda sellado con un hash SHA-256 para garantizar integridad.

**Estados:** `DRAFT → APPROVED → ACTIVE`

Solo los roles `DPO` y `ADMIN` pueden operar este módulo.

---

### Crear un template

**`POST /api/templates`**  
**Acceso:** DPO, ADMIN

```json
// Request
{
  "templateKey": "bienvenida-clientes",
  "name": "Template de bienvenida para clientes",
  "title": "Gestión de tus datos",
  "description": "Detalle de cómo tratamos tus datos personales al registrarte",
  "version": 1,
  "changeReason": "Versión inicial"
}

// Response 201
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "templateKey": "bienvenida-clientes",
  "name": "Template de bienvenida para clientes",
  "title": "Gestión de tus datos",
  "description": "Detalle de cómo tratamos tus datos personales al registrarte",
  "version": 1,
  "changeReason": "Versión inicial",
  "status": "DRAFT",
  "isActive": false,
  "createdBy": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "approvedBy": null,
  "approvedAt": null,
  "activationDate": null,
  "hashSha256": null,
  "previousHashSha256": null,
  "createdAt": "2026-06-27T10:00:00Z"
}
```

---

### Vincular una finalidad al template

**`POST /api/templates/{id}/purposes`**  
**Acceso:** DPO, ADMIN  
Solo se puede vincular en estado DRAFT.

```json
// Request
{
  "purposeId": "a1b2c3d4-...",
  "orderPosition": 1,
  "isVisible": true
}

// Response 204 (sin cuerpo)
```

**Caso negativo — template no está en DRAFT**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Solo se pueden vincular purposes a un template en estado DRAFT" }
```

**Caso negativo — la finalidad no está aprobada**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La finalidad debe estar aprobada y activa para vincularse a un template" }
```

---

### Listar purposes del template

**`GET /api/templates/{id}/purposes`**  
**Acceso:** DPO, ADMIN

```json
// Response 200
[
  {
    "purposeId": "a1b2c3d4-...",
    "purposeName": "Envío de newsletters",
    "orderPosition": 1,
    "isVisible": true
  }
]
```

---

### Actualizar orden o visibilidad de una purpose

**`PATCH /api/templates/{id}/purposes/{purposeId}`**  
**Acceso:** DPO, ADMIN  
Solo se puede modificar en estado DRAFT.

```json
// Request (campos opcionales)
{ "orderPosition": 2, "isVisible": false }

// Response 200 — TemplatePurposeResponse actualizado
```

---

### Aprobar un template

**`POST /api/templates/{id}/approve`**  
**Acceso:** DPO, ADMIN  
Requiere que el template tenga al menos una purpose con `isVisible=true`.

```json
// Response 200 — TemplateResponse con status: "APPROVED"
```

**Caso negativo — sin purposes visibles**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "El template debe tener al menos una finalidad visible para ser aprobado" }
```

---

### Activar un template

**`POST /api/templates/{id}/activate`**  
**Acceso:** DPO, ADMIN  
El template debe estar en estado APPROVED. Al activar: se calcula y sella el SHA-256 sobre `templateKey+version+name+description+title+purposes(id:order:visible)` y se desactiva cualquier versión anterior del mismo `templateKey`.

```json
// Response 200 — TemplateResponse con status: "ACTIVE", hashSha256: "abc123..."
```

**Caso negativo — no está aprobado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Solo se puede activar un template en estado APPROVED" }
```

---

### Verificar integridad SHA-256

**`GET /api/templates/{id}/verify`**  
**Acceso:** DPO, ADMIN  
Recalcula el hash del template activo y lo compara con el almacenado. Permite auditar si el contenido fue alterado fuera del sistema.

```json
// Response 200
{
  "templateId": "3fa85f64-...",
  "storedHash": "abc123def456...",
  "computedHash": "abc123def456...",
  "valid": true
}
```

---

### Crear nueva versión

**`POST /api/templates/{id}/new-version`**  
**Acceso:** DPO, ADMIN  
Crea un nuevo template en DRAFT copiando el `templateKey` y nombre, con `version` incrementado. La versión anterior no se modifica.

```json
// Response 201 — TemplateResponse con version: 2, status: "DRAFT"
```

---

### Listar templates con filtros

**`GET /api/templates`**  
**Acceso:** DPO, ADMIN  
Parámetros opcionales: `templateKey`, `isActive`, `createdBy` (keycloak_id), `approvedBy` (keycloak_id), `createdAfter`, `createdBefore` (ISO 8601).

```json
// Response 200
[
  {
    "id": "3fa85f64-...",
    "templateKey": "bienvenida-clientes",
    "version": 1,
    "status": "ACTIVE",
    ...
  }
]
```

---

### Historial de versiones

**`GET /api/templates/family/{templateKey}`**  
**Acceso:** DPO, ADMIN  
Devuelve todas las versiones del mismo `templateKey` en orden ascendente de versión.

---

### Versión activa de un templateKey

**`GET /api/templates/active/{templateKey}`**  
**Acceso:** DPO, ADMIN  
Devuelve la versión actualmente activa. Si no hay ninguna activa devuelve 404.

---

### Reglas de negocio clave

- Un `templateKey` puede tener múltiples versiones pero solo **una puede estar ACTIVE** al mismo tiempo.
- La vinculación y desvinculación de purposes solo es posible en estado **DRAFT**.
- Para aprobar un template necesita al menos **una purpose con `isVisible=true`**.
- El hash SHA-256 se calcula en el momento de **activación** y queda inmutable.
- `createdBy` y `approvedBy` son el `sub` del JWT (Keycloak ID) — no el UUID local del usuario.
- Desvincular una purpose de un template no elimina la purpose del sistema.

---

## 13. Agreements (consentimiento)

### ¿Qué hace este módulo?

Un **Agreement** es el registro formal de la decisión que toma un titular de datos respecto a un Template de consentimiento. Documenta, finalidad por finalidad, si el titular aceptó o rechazó cada una.

Cada agreement genera un hash SHA-256 que encadena al hash del agreement anterior del sistema, formando un **ledger de integridad** análogo al de auditoría. Esto permite detectar cualquier alteración posterior de los registros de consentimiento.

**Estados de un agreement:** `ACTIVE | REVOKED | EXPIRED`

**Reconsent automático:** si ya existe un agreement ACTIVE para el mismo `(dataSubjectId, templateId)` y se crea uno nuevo, el anterior se revoca automáticamente y todas sus `AgreementsPurposes` pasan a `REVOKED`.

> **Acceso:** cualquier usuario autenticado (todos los roles).

---

### `POST /api/agreements`

El orquestador o el frontend registra la decisión del titular tras presentarle el template.

**Caso positivo — primer consentimiento**
```json
// Request
{
  "dataSubjectId": "uuid-titular",
  "templateId": "uuid-template-activo",
  "documentId": "uuid-documento-publicado",
  "purposes": [
    { "purposeId": "uuid-newsletter", "accepted": true },
    { "purposeId": "uuid-publicidad", "accepted": false }
  ],
  "metadata": {
    "captureChannel": "WEB",
    "signatureToken": null,
    "authProvider": "keycloak",
    "extraVariables": null
  }
}

// Response 201
{
  "id": "uuid-agreement",
  "dataSubjectId": "uuid-titular",
  "templateId": "uuid-template",
  "status": "ACTIVE",
  "hashSha256": "a3f9b2c1...",
  "createdAt": "2026-06-28T10:00:00Z",
  "purposes": [
    { "purposeId": "uuid-newsletter", "purposeName": "Newsletter de ofertas", "accepted": true, "status": "ACTIVE" },
    { "purposeId": "uuid-publicidad", "purposeName": "Publicidad segmentada", "accepted": false, "status": "REJECTED" }
  ]
}
```

**Caso positivo — reconsent (ya existía un ACTIVE)**
```
POST /api/agreements  ← mismo dataSubjectId + templateId con ACTIVE existente

→ Agreement anterior: status ACTIVE → REVOKED, sus AgreementsPurposes → REVOKED
→ Nuevo agreement: status ACTIVE con las nuevas decisiones
→ Response 201 con el nuevo agreement
```

**Caso negativo — template no activo**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "El template debe estar en estado ACTIVE para crear un agreement" }
```

**Caso negativo — purpose obligatoria rechazada**
```json
// Response 422
{ "status": "UNPROCESSABLE_ENTITY", "code": 422, "message": "La purpose 'Facturación' es obligatoria y no puede rechazarse" }
```

---

### `GET /api/agreements/active?dataSubjectId=&templateId=`

El orquestador consulta si el titular ya dio consentimiento antes de iniciar un flujo de datos.

```
GET /api/agreements/active?dataSubjectId=uuid-titular&templateId=uuid-template
```

**Caso positivo — existe consentimiento activo**
```
Response 200 — AgreementResponse con status: ACTIVE
```

**Caso negativo — no hay consentimiento activo**
```
Response 404 — el orquestador debe solicitar consentimiento antes de continuar
```

---

### `GET /api/agreements`

Lista agreements con filtros opcionales.

```
GET /api/agreements
GET /api/agreements?dataSubjectId=uuid-titular
GET /api/agreements?templateId=uuid-template
GET /api/agreements?status=ACTIVE
GET /api/agreements?status=REVOKED
```

---

### `GET /api/agreements/{id}`

Devuelve el agreement con su detalle completo de purposes.

```json
// Response 200
{
  "id": "uuid-agreement",
  "status": "ACTIVE",
  "hashSha256": "a3f9b2c1...",
  "purposes": [ ... ],
  "metadata": { "captureChannel": "WEB", ... }
}
```

---

### `POST /api/agreements/{id}/verify-integrity`

Recalcula el SHA-256 del agreement y lo compara con el almacenado. Registra el resultado en el ledger de integridad.

```json
// Request (opcional — por defecto checkType = "MANUAL")
{ "checkType": "MANUAL" }

// Response 200
{
  "agreementId": "uuid-agreement",
  "storedHash": "a3f9b2c1...",
  "recalculatedHash": "a3f9b2c1...",
  "isValid": true,
  "checkType": "MANUAL",
  "createdAt": "2026-06-28T10:05:00Z"
}
```

**Caso negativo — hash no coincide (posible alteración)**
```json
// Response 200 (informa, no lanza excepción)
{
  "isValid": false,
  "storedHash": "a3f9b2c1...",
  "recalculatedHash": "zz991234...",
  "errorDetail": "El hash recalculado no coincide con el almacenado"
}
```

---

### `GET /api/agreements/{id}/integrity-log`

Historial de todas las verificaciones de integridad del agreement. Permite auditar cuándo se verificó y si fue válido cada vez.

---

### `GET /api/agreements/integrity-log/failed`

Lista todas las verificaciones fallidas (`isValid = false`) de todos los agreements del sistema. Endpoint para el equipo de auditoría — permite detectar registros de consentimiento que pudieron haber sido alterados.

---

### `PATCH /api/agreements/{id}/revoke`

Revoca manualmente un agreement activo. Cambia su estado a `REVOKED` y el de todas sus `AgreementsPurposes` a `REVOKED`.

**Caso positivo**
```json
// Request
PATCH /api/agreements/uuid-agreement/revoke

{
  "subjectId": "uuid-titular"
}

// Response 200
{
  "id": "uuid-agreement",
  "dataSubjectId": "uuid-titular",
  "status": "REVOKED",
  "hashSha256": "a3f9b2c1...",
  "purposes": [
    { "purposeId": "uuid-newsletter", "accepted": true, "status": "REVOKED" },
    { "purposeId": "uuid-publicidad", "accepted": false, "status": "REVOKED" }
  ]
}
```

**Efectos secundarios tras el commit:**
- El evento `AgreementRevokedEvent` dispara `AgreementRevocationCacheListener` (`@TransactionalEventListener(AFTER_COMMIT)`)
- El listener elimina las claves `consent:{subjectId}:{purposeId}` del Redis del Orquestador
- Garantía: la caché nunca se invalida antes de que el cambio en Postgres sea definitivo (sin split-brain)

**Caso negativo — agreement ya revocado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "El agreement ya está en estado REVOKED" }
```

**Caso negativo — agreement no encontrado**
```json
// Response 404
{ "status": "NOT_FOUND", "code": 404, "message": "Agreement no encontrado" }
```

> **Acceso:** cualquier usuario autenticado. En llamadas M2M desde el Orquestador, el `subjectId` del body se valida contra el `dataSubjectId` del agreement.

---

## 14. Orquestador B2B (puerto 8081)

### ¿Qué hace el Orquestador?

El **Orquestador** es la capa de acceso B2B de Ley Data. Los sistemas cliente externos (CRM, ERP, plataformas de marketing) se conectan al Orquestador en lugar de conectarse directamente al backend. El Orquestador:

- Valida el JWT del sistema externo contra el JWKS del realm `empresa-cliente`
- Consulta y actualiza Redis como caché de consentimiento (clave `consent:{subjectId}:{purposeId}`, TTL 300 s)
- Propaga operaciones al backend con credenciales M2M (`client_credentials` desde el realm `leydata`)

El Orquestador corre en el puerto **8081**. El backend (`8080`) no tiene exposición directa al exterior.

> **Prerrequisito:** ejecutar `bash scripts/setup-empresa-cliente-realm.sh` para crear el realm `empresa-cliente` con el cliente `crm-sistema` y el usuario de prueba `operador@empresa.cl / operador123`.

---

### `GET /consent/check`

Verifica si un titular (identificado con `subjectId` opaco) tiene consentimiento activo para una finalidad.

```json
// Request
GET /consent/check?subjectId=abc123&purposeId=uuid-finalidad
Authorization: Bearer <external-jwt>

// Response 200
{
  "subjectId": "abc123",
  "purposeId": "uuid-finalidad",
  "status": "ALLOWED",
  "legalBasisCode": "ART6_1_A",
  "validUntil": "2027-06-28T10:00:00"
}
```

**Valores de `status`:**

| Valor | Significado |
|---|---|
| `ALLOWED` | Consentimiento activo y válido |
| `REVOKED` | El titular revocó el consentimiento |
| `DENIED` | El agreement expiró |
| `PENDING` | Nunca se ha registrado consentimiento |

**Flujo de caché:**
1. Busca clave `consent:{subjectId}:{purposeId}` en Redis
2. Si hay hit (JSON válido): deserializa y devuelve sin consultar LeyData
3. Si hay miss o formato antiguo inválido: consulta `GET /api/agreements/active` en LeyData, construye la respuesta enriquecida y pre-calienta Redis

---

### `POST /consent/capture`

Registra el consentimiento de un titular a través del Orquestador.

```json
// Request
{
  "subjectId": "abc123",
  "templateId": "uuid-template-activo",
  "documentId": "uuid-documento-publicado",
  "purposes": [
    { "purposeId": "uuid-finalidad", "accepted": true }
  ]
}

// Response 201
{
  "subjectId": "abc123",
  "agreementId": "uuid-agreement",
  "status": "ALLOWED"
}
```

**Post-condición:** Redis pre-calentado con `ALLOWED` por cada purpose aceptada. Los campos `legalBasisCode` y `validUntil` se toman del acuerdo real devuelto por LeyData (no se inventan).

---

### `POST /consent/revoke`

Revoca el consentimiento de un titular a través del Orquestador.

```json
// Request
{
  "subjectId": "abc123",
  "agreementId": "uuid-agreement"
}

// Response 200
{
  "subjectId": "abc123",
  "agreementId": "uuid-agreement",
  "status": "REVOKED"
}
```

**Post-condición:** Redis actualizado con `REVOKED` por cada purpose del acuerdo. El Orquestador escribe (no borra) para que el dato `REVOKED` esté disponible inmediatamente mientras el `AgreementRevocationCacheListener` del backend completa el ciclo `AFTER_COMMIT`.

---

## 15. Referencia rápida de códigos de error

| Código HTTP | Status JSON | Cuándo ocurre |
|-------------|-------------|---------------|
| 400 | `BAD_REQUEST` | Campos vacíos, formato inválido, UUID mal formado, valor fuera de rango |
| 401 | `UNAUTHORIZED` | Sin token, token expirado o credenciales incorrectas |
| 403 | `FORBIDDEN` | Token válido pero sin el rol requerido; o usuario bloqueado/inactivo interceptado por UserStatusFilter |
| 404 | `NOT_FOUND` | Recurso no encontrado por ID |
| 405 | `METHOD_NOT_ALLOWED` | Método HTTP incorrecto para esa ruta |
| 409 | `CONFLICT` | Duplicado o estado inválido para la operación (ej. usuario ya bloqueado) |
| 409 | `RETENTION_LOCKED` | Intento de modificar configuración de finalidad vinculada a documento PUBLISHED |
| 422 | `UNPROCESSABLE_ENTITY` | Datos técnicamente válidos pero que violan una regla de negocio |
| 500 | `INTERNAL_SERVER_ERROR` | Error no controlado del servidor |

---

## Notas transversales de seguridad y comportamiento

**Doble capa de revocación de acceso**

Cuando se bloquea o desactiva un usuario, el sistema actúa en dos niveles simultáneamente:

1. **Keycloak (nivel de identidad):** `disableUser()` impide que el usuario haga login nuevo o renueve su token (`/token` con `refresh_token`). Efecto permanente hasta que se habilite de nuevo.
2. **`UserStatusFilter` (nivel de token existente):** después de que Keycloak valida el JWT, el filtro consulta `user_status` y `users.active`. Si el usuario está bloqueado o inactivo, el request se rechaza con 403 aunque el token sea técnicamente válido. Esto corta el acceso durante la ventana (~5 min) en que los tokens ya emitidos todavía no han expirado.

La combinación de ambas capas garantiza que el ADMIN pueda revocar el acceso de un operador con efecto inmediato ante un incidente de seguridad, sin esperar a que expire el token.

**IP en auditoría — sin X-Forwarded-For**

El sistema registra la IP de conexión usando `request.getRemoteAddr()` directamente, sin leer el header `X-Forwarded-For`. Esto evita que un actor malintencionado falsifique su IP en el log de auditoría enviando ese header. En producción, donde el tráfico pasa por un proxy o load balancer, Tomcat está configurado con `RemoteIpValve` para reescribir transparentemente el `remoteAddr` con la IP real del cliente antes de que llegue al código de aplicación.

**Sin hard-delete en ningún módulo**

Usuarios, dominios, categorías de datos y documentos nunca se eliminan físicamente. Todo opera con flags `isActive`/`active`/`blocked`. Los registros de auditoría son la única entidad donde incluso el soft-delete está prohibido — son completamente inmutables por diseño y por trigger de base de datos.

**Filtrado de contenido por rol**

Los endpoints de documentos devuelven respuestas diferentes según el rol del usuario autenticado. DPO y ADMIN reciben la respuesta completa (con `content`, `rejectionReason`, etc.). Cualquier otro rol solo recibe los campos públicos de documentos PUBLISHED. Esto protege el proceso interno de elaboración y revisión de documentos.
