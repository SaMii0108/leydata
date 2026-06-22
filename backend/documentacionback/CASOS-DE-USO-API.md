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
2. El **JEFE_DOMINIO** solicita al DPO crear una nueva finalidad de tratamiento para su área. Ejemplo: "quiero enviar newsletters a clientes de marketing".
3. El **DPO** revisa la solicitud. Si la aprueba, queda registrada una Finalidad en el sistema con su base legal (ej. consentimiento, contrato, obligación legal).
4. El **DPO** configura qué categorías de datos procesa esa finalidad y por cuánto tiempo se retienen.
5. El **DPO** crea un Documento de Privacidad que agrupa una o más finalidades, lo somete a revisión, lo aprueba y lo publica. Al publicarlo se genera un PDF firmado con SHA-256.
6. El titular recibe o puede consultar ese documento publicado para conocer el tratamiento que se hace de sus datos.
7. Toda acción del sistema queda registrada en un log de auditoría con cadena de hashes inmutable.

---

## Índice

1. [Autenticación](#1-autenticación)
2. [Usuarios](#2-usuarios)
3. [Dominios](#3-dominios)
4. [Solicitudes de Finalidad](#4-solicitudes-de-finalidad)
5. [Legal Basis](#5-legal-basis)
6. [Categorías de Datos](#6-categorías-de-datos)
7. [Categorías por Finalidad + Retención](#7-categorías-por-finalidad--retención)
8. [Documentos de Privacidad](#8-documentos-de-privacidad)
9. [Auditoría](#9-auditoría)
10. [Notificaciones](#10-notificaciones)
11. [Referencia de errores](#11-referencia-rápida-de-códigos-de-error)

---

## 1. Autenticación

### ¿Qué hace este módulo?

La autenticación está delegada completamente a **Keycloak**, un servidor de identidad externo. El backend de LeyData no almacena ni valida contraseñas — solo verifica que el JWT que llega en cada request fue emitido por el realm `leydata` de Keycloak.

Sin embargo, tener un token válido de Keycloak no es suficiente para operar. El sistema tiene una segunda capa de control: el `UserStatusFilter`. Este filtro, que se ejecuta en cada request, busca al usuario en la base de datos local y verifica que no esté suspendido ni bloqueado. Un usuario puede tener un token perfectamente válido en Keycloak y aun así recibir un 403 si fue bloqueado en LeyData.

Esto permite al ADMIN actuar con efecto inmediato ante un incidente de seguridad (bloquear a un operador) sin tener que revocar tokens en Keycloak.

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

Gestiona el ciclo de vida de los operadores del sistema (no de los titulares de datos). Un operador puede ser un ADMIN, un DPO o un JEFE_DOMINIO. Cada usuario existe en dos sistemas al mismo tiempo: **Keycloak** (gestiona la autenticación, contraseñas y tokens) y **la BD local de LeyData** (gestiona el estado del usuario, sus roles internos y sus dominios asignados).

Esta doble persistencia crea una regla importante: cuando el ADMIN crea un usuario, primero lo registra en Keycloak y luego en la BD local. Si la BD local falla por cualquier motivo, el sistema automáticamente borra al usuario recién creado en Keycloak para evitar que quede un usuario "fantasma" que puede autenticarse pero no operar. Esta compensación se hace sin transacciones distribuidas formales — es código manual de rollback.

**Reglas de negocio del módulo:**
- Un usuario nunca se elimina físicamente. Solo puede desactivarse (suspensión temporal, reversible) o bloquearse (permanente e irreversible desde la API).
- El ADMIN no puede aplicar ninguna acción destructiva sobre sí mismo.
- Al quitar el rol `JEFE_DOMINIO` a un usuario, el sistema limpia automáticamente todos los dominios que tenía asignados, porque sin ese rol no tiene sentido tener dominios.
- Solo usuarios activos con rol `JEFE_DOMINIO` pueden tener dominios asignados.
- Solo se pueden asignar dominios que estén activos.

> **Todos los endpoints de este módulo requieren rol `ADMIN`.**

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
  "message": "Usuario creado correctamente en el sistema y en autenticación.",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Caso negativo — email ya registrado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "Ya existe un usuario con el email maria@empresa.cl" }
```

**Caso negativo — falla la BD después de crear en Keycloak**
```
1. Keycloak crea el usuario ✅
2. INSERT en BD local falla ❌
3. Sistema compensa: keycloak.deleteUser(keycloakId) — el usuario desaparece de Keycloak
4. Response 500 — el usuario no quedó en ningún sistema
```

---

### `PUT /api/users/{userId}`

El ADMIN puede editar el nombre, los roles y los dominios asignados de un usuario.

Al editar roles, el sistema evalúa si el nuevo conjunto de roles incluye `JEFE_DOMINIO`. Si no lo incluye y el usuario tenía dominios asignados, esos dominios se limpian automáticamente. Esto es importante: el sistema no pregunta, simplemente lo hace como parte de la consistencia del modelo.

**Caso positivo — promover a JEFE_DOMINIO con dominios**
```json
// Request
{
  "name": "Pedro López",
  "roles": ["JEFE_DOMINIO"],
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

El bloqueo es permanente e irreversible desde la API. Representa un incidente de seguridad o una salida definitiva del operador. Una vez bloqueado, el único camino para desbloquear es intervenir directamente en Keycloak a nivel de administración.

**Caso positivo**
```json
// Response 200
{ "status": "success", "message": "Usuario bloqueado permanentemente", "user": { "blocked": true } }
```

**Caso negativo — ADMIN intenta bloquearse a sí mismo**

El sistema identifica al actor del request y compara con el objetivo. No permite autoacciones destructivas.

```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "No puedes aplicar esta acción sobre tu propio usuario" }
```

**Caso negativo — usuario ya estaba bloqueado**
```json
// Response 409
{ "status": "CONFLICT", "code": 409, "message": "El usuario ya está bloqueado" }
```

---

### `POST /api/users/{userId}/deactivate` y `/reactivate`

La desactivación es una suspensión temporal. El token de Keycloak sigue siendo válido técnicamente, pero el `UserStatusFilter` rechaza al usuario en cada request hasta que sea reactivado.

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

## 5. Legal Basis

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

## 6. Categorías de Datos

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

## 7. Categorías por Finalidad + Retención

### ¿Qué hace este módulo?

Una vez que existe una finalidad aprobada (ej. "Newsletter de Marketing"), el DPO debe declarar exactamente qué tipos de datos procesa esa finalidad y por cuánto tiempo los retiene. Esto es un requisito explícito de la Ley 21.719: el titular tiene derecho a saber qué datos se usan y cuándo serán eliminados o anonimizados.

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
  "retention": {
    "retentionPeriod": 10,
    "retentionUnit": "YEARS",
    "legalJustification": "Art. 17 Ley 21.719",
    "anonymizeAfter": true
  }
}

// Response 201
{ "id": "uuid", "retentionLocked": false }
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

## 8. Documentos de Privacidad

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

## 9. Auditoría

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

## 10. Notificaciones

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

## 11. Referencia rápida de códigos de error

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

**UserStatusFilter — doble capa de autenticación**

Después de que Keycloak valida el JWT, el `UserStatusFilter` verifica en la BD local que el usuario tenga `active=true` y `blocked=false`. Esto permite revocar el acceso de un operador con efecto inmediato sin necesidad de invalidar tokens en Keycloak. Si el usuario está suspendido o bloqueado, recibe un 403 en cualquier request, aunque su token sea perfectamente válido.

**IP en auditoría — sin X-Forwarded-For**

El sistema registra la IP de conexión usando `request.getRemoteAddr()` directamente, sin leer el header `X-Forwarded-For`. Esto evita que un actor malintencionado falsifique su IP en el log de auditoría enviando ese header. En producción, donde el tráfico pasa por un proxy o load balancer, Tomcat está configurado con `RemoteIpValve` para reescribir transparentemente el `remoteAddr` con la IP real del cliente antes de que llegue al código de aplicación.

**Sin hard-delete en ningún módulo**

Usuarios, dominios, categorías de datos y documentos nunca se eliminan físicamente. Todo opera con flags `isActive`/`active`/`blocked`. Los registros de auditoría son la única entidad donde incluso el soft-delete está prohibido — son completamente inmutables por diseño y por trigger de base de datos.

**Filtrado de contenido por rol**

Los endpoints de documentos devuelven respuestas diferentes según el rol del usuario autenticado. DPO y ADMIN reciben la respuesta completa (con `content`, `rejectionReason`, etc.). Cualquier otro rol solo recibe los campos públicos de documentos PUBLISHED. Esto protege el proceso interno de elaboración y revisión de documentos.
