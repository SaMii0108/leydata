# Plan de Pruebas — API Ley Data

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)
**Backend URL:** `http://localhost:8080`
**Keycloak URL:** `http://localhost:8180`
**Fecha:** 2026-06-16
**Arquitectura:** Modular DDD — ver [ESTRUCTURA-PROYECTO.md](ESTRUCTURA-PROYECTO.md)

---

## Índice

1. [Lo más importante antes de empezar](#1-lo-más-importante-antes-de-empezar)
2. [Cómo obtener un token en Postman](#2-cómo-obtener-un-token-en-postman)
3. [Credenciales del entorno de pruebas](#3-credenciales-del-entorno-de-pruebas)
4. [Roles y qué puede hacer cada uno](#4-roles-y-qué-puede-hacer-cada-uno)
5. [Módulo Usuarios — `/api/users`](#5-módulo-usuarios--apiusers)
6. [Módulo Dominios — `/api/domains`](#6-módulo-dominios--apidomains)
7. [Módulo Solicitudes de Propósito — `/api/purpose-requests`](#7-módulo-solicitudes-de-propósito--apipurpose-requests)
8. [Módulo Auditoría — `/api/audit`](#8-módulo-auditoría--apiaudit)
9. [Módulo Documentos de Privacidad — `/api/privacy-documents`](#9-módulo-documentos-de-privacidad--apiprivacy-documents)
10. [Módulo Notificaciones — `/api/notifications`](#10-módulo-notificaciones--apinotifications)
11. [Tabla resumen de todos los casos](#11-tabla-resumen-de-todos-los-casos)
12. [Bugs encontrados y corregidos durante las pruebas](#12-bugs-encontrados-y-corregidos-durante-las-pruebas)

---

## 1. Lo más importante antes de empezar

### El sistema tiene DOS lugares donde existe un usuario — pero solo hay que crear en UNO

Keycloak y la base de datos local trabajan juntos. El backend se encarga de mantenerlos sincronizados automáticamente.

```
┌─────────────────────────────────┐     ┌──────────────────────────────────┐
│           KEYCLOAK              │     │         BASE DE DATOS LOCAL       │
│      (http://localhost:8180)    │     │         (PostgreSQL :5433)        │
│                                 │     │                                  │
│  • Guarda la CONTRASEÑA         │     │  • Guarda el ROL de negocio      │
│  • Emite el JWT (token)         │     │  • Guarda los DOMINIOS asignados │
│  • Verifica credenciales        │     │  • Registra en AUDITORÍA         │
│  • Asigna roles al token        │     │  • Relaciona con purpose-requests│
└─────────────────────────────────┘     └──────────────────────────────────┘
          ▲                                           ▲
          └──────── POST /api/users ─────────────────┘
                  El backend crea en AMBOS a la vez
```

**¿Qué hace `POST /api/users`?**

Cuando el ADMIN crea un usuario desde la UI, el backend:

1. Crea el usuario en Keycloak (contraseña + realm role)
2. Guarda el registro en la BD local con el ID que Keycloak devolvió
3. Si algo falla en el paso 2, elimina automáticamente el usuario de Keycloak para no dejar inconsistencias

El ADMIN solo necesita llamar a un endpoint — el sistema hace todo el resto.

---

### Códigos HTTP que retorna el backend

| Código | Cuándo ocurre                                                        |
| ------ | -------------------------------------------------------------------- |
| `200`  | Operación exitosa                                                    |
| `201`  | Recurso creado correctamente                                         |
| `400`  | Datos inválidos o regla de negocio violada (ver mensaje de error)    |
| `401`  | Token ausente, expirado o con firma inválida                         |
| `403`  | Cuenta bloqueada/desactivada **o** rol insuficiente para el endpoint |
| `404`  | Recurso no encontrado                                                |
| `409`  | Conflicto de estado (ej: ya bloqueado, ya activo, ya revisado)       |

---

## 2. Cómo obtener un token en Postman

El backend **no tiene endpoint de login propio**. El token siempre viene de Keycloak.

### Paso 1 — Configurar el request en Postman

- **Method:** `POST`
- **URL:** `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
- **Pestaña Body** → seleccionar **x-www-form-urlencoded**
- Agregar estos campos:

| Key          | Value              |
| ------------ | ------------------ |
| `grant_type` | `password`         |
| `client_id`  | `leydata-frontend` |
| `username`   | `admin@leydata.cl` |
| `password`   | `Admin1234!`       |

### Paso 2 — Verificar que Postman envíe el Content-Type correcto

> ⚠️ **Problema frecuente:** el error `"Missing form parameter: grant_type"` ocurre cuando el Content-Type no llega bien a Keycloak.
>
> **Solución:**s
>
> 1. Ir a la pestaña **Headers** en Postman
> 2. Buscar la fila `Content-Type: application/x-www-form-urlencoded`
> 3. Asegurarse de que esa fila tenga el **checkbox marcado** (✅)
> 4. Si hay una fila `Content-Type: application/json` marcada, **desmarcarla**

**Si el problema persiste — alternativa con raw:**

- Body → **raw** → tipo **Text**
- En Headers agregar: `Content-Type: application/x-www-form-urlencoded`
- Pegar en el body:
  ```
  grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!
  ```

### Paso 3 — Usar el token en los requests al backend

Copiar el `access_token` de la respuesta y en cada request al backend:

- Pestaña **Authorization** → Type: **Bearer Token** → pegar el `access_token`

### El token dura 5 minutos

Para renovarlo sin pedir contraseña de nuevo:

- **Method:** `POST`
- **URL:** `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
- **Body x-www-form-urlencoded:**

| Key             | Value                                         |
| --------------- | --------------------------------------------- |
| `grant_type`    | `refresh_token`                               |
| `client_id`     | `leydata-frontend`                            |
| `refresh_token` | `<el refresh_token de la respuesta anterior>` |

---

## 3. Credenciales del entorno de pruebas

El admin ya existe en Keycloak (creado manualmente, ver Guía de Instalación sección 6.4) y en la BD local (creado por el seeder automáticamente al levantar el backend). Los demás usuarios se crean con un solo request al backend — no hay que ir a Keycloak.

| Rol            | Email              | Contraseña   | Estado                                     |
| -------------- | ------------------ | ------------ | ------------------------------------------ |
| `ADMIN`        | `admin@leydata.cl` | `Admin1234!` | ✅ Listo — seeder + Keycloak manual        |
| `DPO`          | `dpo@leydata.cl`   | `Test1234!`  | 🔧 Crear con `POST /api/users`             |
| `JEFE_DOMINIO` | `jefe@leydata.cl`  | `Test1234!`  | 🔧 Crear con `POST /api/users`             |
| `JEFE_DOMINIO` | `jefe2@leydata.cl` | `Test1234!`  | 🔧 Crear con `POST /api/users` (opcional)  |

> **Nota Keycloak 26:** Al crear usuarios con `POST /api/users`, el campo `name` debe contener nombre y apellido (ej: `"Juan Jefe"`). Si solo se envía una palabra, el backend la usa como `firstName` y `lastName` simultáneamente para cumplir con el perfil de usuario de KC 26.

### Cómo crear el usuario DPO de prueba

Un solo request con token de admin — el backend crea en Keycloak y en la BD al mismo tiempo:

```
POST http://localhost:8080/api/users
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "email": "dpo@leydata.cl",
  "name": "DPO Pruebas",
  "roleCode": "DPO",
  "domainIds": [],
  "password": "Test1234!"
}
```

Respuesta esperada: `201 Created` — el usuario ya puede hacer login con esas credenciales.

Repetir el mismo proceso para `JEFE_DOMINIO` cambiando el email, name y roleCode.

---

## 4. Roles y qué puede hacer cada uno

| Rol            | Endpoints accesibles                                                                                   | Lo que NO puede hacer                         |
| -------------- | ------------------------------------------------------------------------------------------------------ | --------------------------------------------- |
| `ADMIN`        | `/api/users/**` · `/api/domains/**` · `/api/audit/**`                                                  | No puede editarse/bloquearse a sí mismo. No gestiona documentos de privacidad |
| `DPO`          | `/api/purpose-requests/**` (revisar) · `/api/privacy-documents/**` (todo el workflow)                  | No puede crear solicitudes de propósito ni gestionar usuarios/dominios |
| `JEFE_DOMINIO` | `/api/purpose-requests` (crear y ver las propias)                                                      | No puede ver las de otros dominios. No gestiona documentos de privacidad |
| Cualquier autenticado | `/api/privacy-documents` (GET) · `/api/notifications/**`                                        | Solo ve sus propias notificaciones |

---

## 5. Módulo Usuarios — `/api/users`

> Todos los endpoints de este módulo requieren rol **ADMIN**.

---

### `POST /api/users` — Crear usuario

**¿Qué hace?**
Crea el usuario en **Keycloak y en la BD local en una sola operación**. Después de este request el usuario ya puede hacer login con las credenciales enviadas. No hay que ir a Keycloak a hacer nada.

**Body:**

```json
{
  "email": "string (obligatorio, debe ser único)",
  "name": "string (obligatorio)",
  "roleCode": "ADMIN | DPO | JEFE_DOMINIO (obligatorio)",
  "domainIds": ["uuid"],
  "password": "string (obligatorio — contraseña inicial del usuario)"
}
```

> `domainIds` solo aplica si `roleCode = "JEFE_DOMINIO"`. Para cualquier otro rol enviar `[]`.
> `password` solo va a Keycloak — nunca se persiste en la BD del sistema.

---

#### ✅ TC-USER-01 — Crear usuario DPO exitosamente

**Prerrequisito:** El usuario `dpo@leydata.cl` no existe en el sistema.
Token: ADMIN

```json
POST /api/users

{
  "email": "dpo@leydata.cl",
  "name": "María DPO",
  "roleCode": "DPO",
  "domainIds": [],
  "password": "Test1234!"
}
```

**Respuesta esperada:** `201 Created`

```json
{
  "status": "success",
  "message": "Usuario creado correctamente en el sistema y en autenticación.",
  "userId": "<uuid>"
}
```

---

#### ✅ TC-USER-02 — Crear JEFE_DOMINIO con dominio asignado

**Prerrequisito:** Tener el UUID de un dominio activo (obtenerlo con `GET /api/domains/all`).
Token: ADMIN

```json
POST /api/users

{
  "email": "jefe@leydata.cl",
  "name": "Juan Jefe",
  "roleCode": "JEFE_DOMINIO",
  "domainIds": ["<uuid_del_dominio_activo>"],
  "password": "Test1234!"
}
```

**Respuesta esperada:** `201 Created`

> El nombre `"Juan Jefe"` se divide en `firstName="Juan"` y `lastName="Jefe"` en Keycloak. Keycloak 26 requiere `lastName` no vacío para que el login funcione.

---

#### ✅ TC-USER-23 — Crear JEFE_DOMINIO sin dominio asignado inicialmente

Token: ADMIN

```json
POST /api/users

{
  "email": "jefe2@leydata.cl",
  "name": "Pedro Jefe",
  "roleCode": "JEFE_DOMINIO",
  "domainIds": [],
  "password": "Test1234!"
}
```

**Respuesta esperada:** `201 Created` — se puede asignar el dominio después con `PUT /api/users/{id}`.

---

#### ❌ TC-USER-03 — Email que ya existe en el sistema

```json
POST /api/users

{
  "email": "admin@leydata.cl",
  "name": "Duplicado",
  "roleCode": "DPO",
  "domainIds": [],
  "password": "Test1234!"
}
```

**Respuesta esperada:** `409 Conflict`

```json
{ "error": "Ya existe un usuario con el email: admin@leydata.cl" }
```

---

#### ❌ TC-USER-04 — Rol que no existe

```json
POST /api/users

{
  "email": "nuevo@leydata.cl",
  "name": "Test",
  "roleCode": "SUPERUSUARIO",
  "domainIds": [],
  "password": "Test1234!"
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "Rol no encontrado: SUPERUSUARIO" }
```

---

#### ❌ TC-USER-05 — Sin token (no autenticado)

```
POST /api/users
(sin header Authorization)
```

**Respuesta esperada:** `401 Unauthorized`

---

#### ❌ TC-USER-06 — Token de rol insuficiente (DPO intenta crear usuario)

```
POST /api/users
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `403 Forbidden`

---

### `GET /api/users` — Listar todos los usuarios

**¿Qué devuelve?** Lista de todos los usuarios del sistema incluyendo activos e inactivos.

---

#### ✅ TC-USER-07 — Listar usuarios como ADMIN

```
GET /api/users
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "status": "success",
  "users": [
    {
      "id": "<uuid>",
      "email": "admin@leydata.cl",
      "name": "Admin LeyData",
      "active": true,
      "blocked": false,
      "roles": ["ADMIN"],
      "domains": []
    }
  ]
}
```

---

#### ❌ TC-USER-08 — Sin token

```
GET /api/users
```

**Respuesta esperada:** `401 Unauthorized`

---

### `GET /api/users/{userId}` — Obtener un usuario por su ID

**¿Qué necesito?** El UUID del usuario (obtenible desde `GET /api/users`).

---

#### ✅ TC-USER-09 — Obtener usuario existente

```
GET /api/users/<uuid_real_de_un_usuario>
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK` con los datos del usuario.

---

#### ❌ TC-USER-10 — UUID que no existe en el sistema

```
GET /api/users/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `404 Not Found`

```json
{ "error": "Usuario no encontrado: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }
```

---

### `PUT /api/users/{userId}` — Editar datos de un usuario

**Reglas importantes:**

- Un ADMIN **no puede editarse a sí mismo** desde este endpoint → `400`
- No se puede editar un usuario que está bloqueado (`blocked = true`) → `409`
- Si se envían `roleCodes`, **reemplaza completamente** los roles que tenía. No acumula.
- Si el nuevo rol no incluye `JEFE_DOMINIO`, el usuario pierde todos sus dominios automáticamente.

---

#### ✅ TC-USER-11 — Editar nombre de un usuario

**Prerrequisito:** Tener el UUID de un usuario que no sea el admin autenticado ni esté bloqueado.

```json
PUT /api/users/<uuid_usuario>
Authorization: Bearer <admin_token>

{
  "name": "Nombre Editado",
  "roleCodes": ["DPO"],
  "domainIds": []
}
```

**Respuesta esperada:** `200 OK` con los datos actualizados.

---

#### ❌ TC-USER-12 — ADMIN intentando editarse a sí mismo

**¿Cómo obtener el UUID del admin autenticado?** Hacer `GET /api/users` y buscar el email `admin@leydata.cl`.

```json
PUT /api/users/<uuid_del_admin_autenticado>
Authorization: Bearer <admin_token>

{
  "name": "Nuevo nombre"
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "El ADMIN no puede modificar su propio perfil desde este endpoint" }
```

---

#### ❌ TC-USER-13 — Intentar editar un usuario bloqueado

**Prerrequisito:** Primero ejecutar TC-USER-14 para tener un usuario bloqueado.

```json
PUT /api/users/<uuid_de_usuario_bloqueado>
Authorization: Bearer <admin_token>

{
  "name": "Intento edición"
}
```

**Respuesta esperada:** `409 Conflict`

---

### `POST /api/users/{userId}/block` — Bloquear un usuario

> ⚠️ **Esta acción es irreversible desde la API.** Una vez bloqueado, el usuario no puede reactivarse. Mostrar confirmación en el frontend antes de ejecutar.

**¿Qué hace el bloqueo?**

- Pone `blocked = true` y `active = false`
- El usuario ya no puede usar el sistema aunque tenga un token válido de Keycloak
- No afecta al usuario en Keycloak (hay que desactivarlo ahí también si se quiere impedir el login)

---

#### ✅ TC-USER-14 — Bloquear un usuario activo

**Prerrequisito:** Tener el UUID de un usuario activo (no el admin).
**Body:** ninguno (se envía sin body)

```
POST /api/users/<uuid_usuario>/block
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "status": "success",
  "message": "Usuario bloqueado permanentemente",
  "user": {
    "active": false,
    "blocked": true,
    ...
  }
}
```

---

#### ❌ TC-USER-15 — Intentar bloquear un usuario que ya está bloqueado

```
POST /api/users/<uuid_ya_bloqueado>/block
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

---

#### ❌ TC-USER-16 — Admin intentando bloquearse a sí mismo

```
POST /api/users/<uuid_del_admin_autenticado>/block
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "El ADMIN no puede bloquearse a sí mismo" }
```

---

### `POST /api/users/{userId}/deactivate` — Desactivar un usuario temporalmente

**Diferencia con bloquear:** La desactivación es **temporal y reversible**. El usuario puede reactivarse después. El bloqueo es permanente.

---

#### ✅ TC-USER-17 — Desactivar un usuario activo

```
POST /api/users/<uuid_usuario_activo>/deactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "status": "success",
  "message": "Usuario desactivado correctamente",
  "user": {
    "active": false,
    "blocked": false,
    ...
  }
}
```

---

#### ❌ TC-USER-18 — Desactivar un usuario que ya está inactivo

```
POST /api/users/<uuid_usuario_inactivo>/deactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

---

#### ❌ TC-USER-19 — Usuario desactivado intenta usar el sistema

> **Escenario:** El usuario ya tiene un token de Keycloak válido (obtenido antes de ser desactivado) e intenta hacer una operación.

**Prerrequisito:** Ejecutar TC-USER-17 primero. El usuario tiene un token vigente.

```
GET /api/users
Authorization: Bearer <token_del_usuario_recien_desactivado>
```

**Respuesta esperada:** `403 Forbidden`

```
Tu cuenta ha sido desactivada. Contacta al administrador.
```

> **Nota:** El token de Keycloak sigue siendo válido criptográficamente, pero el backend rechaza al usuario porque está inactivo en la BD local. Por eso el 403 es el código correcto aquí, no 401.

---

### `POST /api/users/{userId}/reactivate` — Reactivar un usuario desactivado

---

#### ✅ TC-USER-20 — Reactivar un usuario desactivado

**Prerrequisito:** Tener un usuario con `active = false` y `blocked = false` (ejecutar TC-USER-17 antes).

```
POST /api/users/<uuid_usuario_inactivo>/reactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "status": "success",
  "message": "Usuario reactivado correctamente",
  "user": {
    "active": true,
    "blocked": false,
    ...
  }
}
```

---

#### ❌ TC-USER-21 — Reactivar un usuario que ya está activo

```
POST /api/users/<uuid_usuario_activo>/reactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

---

#### ❌ TC-USER-22 — Intentar reactivar un usuario bloqueado permanentemente

**Prerrequisito:** Tener un usuario bloqueado (ejecutar TC-USER-14 antes).

```
POST /api/users/<uuid_usuario_bloqueado>/reactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

```json
{ "error": "No se puede reactivar un usuario bloqueado permanentemente" }
```

---

### Casos de sincronización Keycloak ↔ BD local

#### ✅ TC-USER-24 — keycloakId vinculado desde la creación

**¿Qué prueba?** Que el usuario creado con `POST /api/users` tiene el `keycloak_id` en la BD desde el inicio — no hace falta un primer login.

**Prerrequisito:** Ejecutar TC-USER-01.

**Cómo probar:**

1. Obtener un token de Keycloak con las credenciales de `dpo@leydata.cl`
2. Hacer cualquier request con ese token al backend
   Verificar en BD:

```sql
SELECT keycloak_id FROM users WHERE email = 'dpo@leydata.cl';
```

**Resultado esperado:** El campo `keycloak_id` tiene un UUID — no es NULL.

---

#### ✅ TC-USER-25 — El sistema sigue funcionando aunque el email cambie en Keycloak

**¿Qué prueba?** Que si un admin cambia el email de un usuario en Keycloak, el sistema lo sigue reconociendo por su ID interno (no por email).

**Prerrequisito:** TC-USER-24 completado (el `keycloak_id` ya está guardado en la BD).

**Pasos:**

1. En Keycloak Admin → Users → seleccionar `dpo@leydata.cl` → cambiar email a `dpo-nuevo@leydata.cl`
2. Obtener un nuevo token con las nuevas credenciales
3. Hacer un request al backend con ese token

**Resultado esperado:** El backend lo reconoce correctamente — acceso normal sin errores.

---

## 6. Módulo Dominios — `/api/domains`

> Todos los endpoints requieren rol **ADMIN**.

Los dominios son las unidades organizacionales de la empresa (ej: Recursos Humanos, TI, Legal). Son soft-deleted: nunca se eliminan de la base de datos, solo se desactivan.

---

### `POST /api/domains` — Crear un dominio

**Body:**

```json
{
  "code": "string (único — ej: RRHH)",
  "name": "string",
  "description": "string (opcional)",
  "jefeId": "uuid (opcional — UUID del usuario con rol JEFE_DOMINIO)"
}
```

---

#### ✅ TC-DOM-01 — Crear dominio sin jefe asignado

```json
POST /api/domains
Authorization: Bearer <admin_token>

{
  "code": "RRHH",
  "name": "Recursos Humanos",
  "description": "Departamento de gestión de personas",
  "jefeId": null
}
```

**Respuesta esperada:** `201 Created`

```json
{
  "status": "success",
  "domainId": "<uuid>"
}
```

---

#### ✅ TC-DOM-02 — Crear dominio con JEFE_DOMINIO asignado

**Prerrequisito:** Tener el UUID de un usuario con rol `JEFE_DOMINIO`.

```json
POST /api/domains
Authorization: Bearer <admin_token>

{
  "code": "TI",
  "name": "Tecnología de la Información",
  "jefeId": "<uuid_usuario_jefe_dominio>"
}
```

**Respuesta esperada:** `201 Created`

---

#### ❌ TC-DOM-03 — Código de dominio que ya existe

```json
POST /api/domains
Authorization: Bearer <admin_token>

{
  "code": "RRHH",
  "name": "Otro nombre para RRHH"
}
```

**Respuesta esperada:** `400 Bad Request`

---

#### ❌ TC-DOM-04 — Sin token

```
POST /api/domains
(sin Authorization)
```

**Respuesta esperada:** `401 Unauthorized`

---

#### ❌ TC-DOM-05 — Token de DPO intenta crear dominio

```
POST /api/domains
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `403 Forbidden`

---

### `GET /api/domains/all` — Listar todos los dominios

Devuelve todos los dominios incluyendo los desactivados.

---

#### ✅ TC-DOM-06 — Listar dominios como ADMIN

```
GET /api/domains/all
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK` — lista con todos los dominios y su estado (`active: true/false`).

---

### `POST /api/domains/{domainId}/deactivate` — Desactivar un dominio

Deshabilita el dominio. Las solicitudes de propósito asociadas siguen existiendo pero no se pueden crear nuevas para ese dominio.

---

#### ✅ TC-DOM-07 — Desactivar dominio activo

```
POST /api/domains/<uuid_dominio_activo>/deactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

---

#### ❌ TC-DOM-08 — UUID de dominio que no existe

```
POST /api/domains/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/deactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `404 Not Found`

---

#### ❌ TC-DOM-09 — Dominio que ya está desactivado

```
POST /api/domains/<uuid_dominio_inactivo>/deactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

---

### `POST /api/domains/{domainId}/reactivate` — Reactivar dominio

---

#### ✅ TC-DOM-10 — Reactivar dominio inactivo

```
POST /api/domains/<uuid_dominio_inactivo>/reactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

---

#### ❌ TC-DOM-11 — Dominio que ya está activo

```
POST /api/domains/<uuid_dominio_activo>/reactivate
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `409 Conflict`

---

## 7. Módulo Solicitudes de Propósito — `/api/purpose-requests`

> **Flujo del workflow:** JEFE_DOMINIO crea una solicitud → queda en estado `PENDING` → DPO la revisa y la aprueba o rechaza → el estado es final, no puede volver a `PENDING`.

Este módulo implementa el principio de **minimización de datos** y **base legal explícita** de la Ley 21.719. Cada solicitud debe justificar qué datos se procesan y por qué.

---

### `POST /api/purpose-requests` — Crear solicitud

> Acceso: `JEFE_DOMINIO` únicamente

**Body:**

```json
{
  "title": "string",
  "justification": "string (base legal — ej: Art. 13 Ley 21.719)",
  "requestedData": "string (qué datos se van a procesar)",
  "domainId": "uuid (debe ser un dominio que le pertenezca al JEFE_DOMINIO autenticado)"
}
```

---

#### ✅ TC-PUR-01 — Crear solicitud exitosamente

**Prerrequisito:** El JEFE_DOMINIO autenticado tiene al menos un dominio asignado.

```json
POST /api/purpose-requests
Authorization: Bearer <jefe_dominio_token>

{
  "title": "Procesamiento de datos para nómina",
  "justification": "Necesario para cumplir obligaciones laborales según Ley 21.719 Art. 13",
  "requestedData": "RUT, nombre completo, cuenta bancaria",
  "domainId": "<uuid_dominio_propio>"
}
```

**Respuesta esperada:** `201 Created`

```json
{
  "status": "success",
  "message": "Solicitud enviada al DPO correctamente",
  "request": {
    "id": "<uuid>",
    "title": "Procesamiento de datos para nómina",
    "status": "PENDING",
    ...
  }
}
```

---

#### ❌ TC-PUR-02 — Crear solicitud para un dominio de otro jefe

```json
POST /api/purpose-requests
Authorization: Bearer <jefe_dominio_token>

{
  "title": "Solicitud inválida",
  "justification": "...",
  "requestedData": "...",
  "domainId": "<uuid_dominio_que_no_es_del_jefe_autenticado>"
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "No puedes crear solicitudes para un dominio que no te pertenece" }
```

---

#### ❌ TC-PUR-03 — Crear solicitud para un dominio desactivado

```json
POST /api/purpose-requests
Authorization: Bearer <jefe_dominio_token>

{
  "title": "Solicitud",
  "justification": "...",
  "requestedData": "...",
  "domainId": "<uuid_dominio_inactivo>"
}
```

**Respuesta esperada:** `409 Conflict`

---

#### ❌ TC-PUR-04 — Rol incorrecto (DPO intenta crear solicitud)

```
POST /api/purpose-requests
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `403 Forbidden`

---

### `GET /api/purpose-requests/my` — Ver mis propias solicitudes

> Acceso: `JEFE_DOMINIO` únicamente
> Devuelve solo las solicitudes creadas por el JEFE_DOMINIO autenticado.

---

#### ✅ TC-PUR-05 — Listar solicitudes propias

```
GET /api/purpose-requests/my
Authorization: Bearer <jefe_dominio_token>
```

**Respuesta esperada:** `200 OK` — lista de solicitudes de ese jefe (todos los estados).

---

#### ❌ TC-PUR-06 — ADMIN intenta usar este endpoint

```
GET /api/purpose-requests/my
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `403 Forbidden`

---

### `GET /api/purpose-requests/pending` — Ver solicitudes pendientes

> Acceso: `DPO` únicamente
> Devuelve solo las solicitudes en estado `PENDING`.

---

#### ✅ TC-PUR-07 — Listar solicitudes pendientes como DPO

```
GET /api/purpose-requests/pending
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` — solo solicitudes con `status: "PENDING"`.

---

#### ❌ TC-PUR-08 — JEFE_DOMINIO intenta ver pendientes

```
GET /api/purpose-requests/pending
Authorization: Bearer <jefe_dominio_token>
```

**Respuesta esperada:** `403 Forbidden`

---

### `GET /api/purpose-requests` — Ver todas las solicitudes

> Acceso: `DPO` únicamente
> Devuelve todas las solicitudes de todos los dominios y todos los estados.

---

#### ✅ TC-PUR-09 — Historial completo como DPO

```
GET /api/purpose-requests
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` — solicitudes en `PENDING`, `APPROVED` y `REJECTED`.

---

### `PATCH /api/purpose-requests/{requestId}/review` — Revisar una solicitud

> Acceso: `DPO` únicamente

**Reglas importantes:**

- Solo acepta `APPROVED` o `REJECTED` como valor del campo `status`
- Si se rechaza (`REJECTED`), el campo `reviewNotes` es **obligatorio** — lo exige la Ley 21.719 Art. 14
- Una solicitud ya revisada no puede volver a revisarse — su estado es final

**Body:**

```json
{
  "status": "APPROVED | REJECTED",
  "reviewNotes": "string (obligatorio si status = REJECTED)"
}
```

---

#### ✅ TC-PUR-10 — Aprobar una solicitud

**Prerrequisito:** Tener el UUID de una solicitud en estado `PENDING`.

```json
PATCH /api/purpose-requests/<uuid_solicitud_pendiente>/review
Authorization: Bearer <dpo_token>

{
  "status": "APPROVED",
  "reviewNotes": "Solicitud conforme con Ley 21.719"
}
```

**Respuesta esperada:** `200 OK`

```json
{
  "status": "success",
  "message": "Solicitud revisada correctamente",
  "request": {
    "status": "APPROVED",
    "reviewNotes": "Solicitud conforme con Ley 21.719",
    ...
  }
}
```

---

#### ✅ TC-PUR-11 — Rechazar una solicitud con notas

```json
PATCH /api/purpose-requests/<uuid_solicitud_pendiente>/review
Authorization: Bearer <dpo_token>

{
  "status": "REJECTED",
  "reviewNotes": "La base legal indicada no aplica según Art. 16 Ley 21.719. Se debe reformular."
}
```

**Respuesta esperada:** `200 OK`

---

#### ❌ TC-PUR-12 — Rechazar sin notas de justificación

```json
PATCH /api/purpose-requests/<uuid_solicitud>/review
Authorization: Bearer <dpo_token>

{
  "status": "REJECTED",
  "reviewNotes": ""
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{
  "error": "El DPO debe justificar el rechazo en las notas de revisión (Ley 21.719)"
}
```

---

#### ❌ TC-PUR-13 — Intentar revisar una solicitud que ya fue procesada

**Prerrequisito:** Ejecutar TC-PUR-10 o TC-PUR-11 primero.

```json
PATCH /api/purpose-requests/<uuid_ya_aprobada>/review
Authorization: Bearer <dpo_token>

{
  "status": "APPROVED",
  "reviewNotes": ""
}
```

**Respuesta esperada:** `409 Conflict`

```json
{ "error": "La solicitud ya fue revisada. Estado actual: APPROVED" }
```

---

#### ❌ TC-PUR-14 — Estado con valor inválido

```json
PATCH /api/purpose-requests/<uuid>/review
Authorization: Bearer <dpo_token>

{
  "status": "PENDIENTE",
  "reviewNotes": ""
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "Estado inválido. Use APPROVED o REJECTED" }
```

---

## 8. Módulo Auditoría — `/api/audit`

> Todos los endpoints requieren rol **ADMIN**.

El sistema registra automáticamente cada operación importante en un log de auditoría. Los logs son **inmutables** — no pueden modificarse ni eliminarse (protegidos por triggers en PostgreSQL). Cada log tiene un hash SHA-256 que encadena con el anterior, formando una cadena verificable.

**¿Qué se registra automáticamente?** Cada vez que el ADMIN o DPO realiza una acción sobre usuarios, dominios o solicitudes, el sistema crea un log sin que haya que hacer nada extra.

---

### `GET /api/audit/logs` — Consultar logs de auditoría

**Parámetros opcionales:**

| Parámetro    | Qué filtra                              | Ejemplo de valor                       |
| ------------ | --------------------------------------- | -------------------------------------- |
| `action`     | Tipo de acción realizada                | `BLOQUEAR_USUARIO`                     |
| `table`      | Tabla afectada                          | `users`, `domains`, `purpose_requests` |
| `actorEmail` | Email del usuario que ejecutó la acción | `admin@leydata.cl`                     |
| `page`       | Número de página (empieza en 0)         | `0`                                    |
| `size`       | Cantidad de resultados por página       | `20`                                   |

**Acciones registradas:**

| Acción                | Tabla              | Quién la genera |
| --------------------- | ------------------ | --------------- |
| `CREAR_USUARIO`       | `users`            | ADMIN           |
| `ACTUALIZAR_USUARIO`  | `users`            | ADMIN           |
| `DESACTIVAR_USUARIO`  | `users`            | ADMIN           |
| `REACTIVAR_USUARIO`   | `users`            | ADMIN           |
| `BLOQUEAR_USUARIO`    | `users`            | ADMIN           |
| `CREAR_DOMINIO`       | `domains`          | ADMIN           |
| `DESACTIVAR_DOMINIO`  | `domains`          | ADMIN           |
| `REACTIVAR_DOMINIO`   | `domains`          | ADMIN           |
| `SOLICITAR_PROPOSITO`   | `purpose_requests`  | JEFE_DOMINIO    |
| `APROBAR_SOLICITUD`     | `purpose_requests`  | DPO             |
| `RECHAZAR_SOLICITUD`    | `purpose_requests`  | DPO             |
| `CREAR_DOCUMENTO`       | `privacy_documents` | DPO             |
| `EDITAR_DOCUMENTO`      | `privacy_documents` | DPO             |
| `DESACTIVAR_DOCUMENTO`  | `privacy_documents` | DPO             |
| `VINCULAR_PROPOSITO`    | `privacy_documents` | DPO             |
| `DESVINCULAR_PROPOSITO` | `privacy_documents` | DPO             |
| `ENVIAR_A_REVISION`     | `privacy_documents` | DPO             |
| `REENVIAR_A_REVISION`   | `privacy_documents` | DPO             |
| `APROBAR_DOCUMENTO`     | `privacy_documents` | DPO             |
| `RECHAZAR_DOCUMENTO`    | `privacy_documents` | DPO             |
| `PUBLICAR_DOCUMENTO`    | `privacy_documents` | DPO             |
| `ARCHIVAR_DOCUMENTO`    | `privacy_documents` | DPO             |

> **Auditoría obligatoria (Ley 21.719):** Para el módulo de documentos de privacidad, el log de auditoría se persiste en la **misma transacción** que la operación de negocio (`Propagation.REQUIRED`). Si el log falla → toda la operación hace rollback. Sin log no hay operación.

---

#### ✅ TC-AUDIT-01 — Obtener todos los logs paginados

```
GET /api/audit/logs?page=0&size=10
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "logs": [
    {
      "id": "<uuid>",
      "action": "CREAR_USUARIO",
      "tableName": "users",
      "actorEmail": "admin@leydata.cl",
      "actorRole": "ADMIN",
      "oldData": null,
      "newData": "{\"email\":\"dpo@leydata.cl\",...}",
      "ipAddress": "127.0.0.1",
      "logHash": "<sha256_hex>",
      "createdAt": "2026-06-08T10:00:00"
    }
  ],
  "total": 5,
  "page": 0,
  "totalPages": 1
}
```

---

#### ✅ TC-AUDIT-02 — Filtrar por tipo de acción

```
GET /api/audit/logs?action=BLOQUEAR_USUARIO
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK` — solo logs de bloqueos de usuario.

---

#### ✅ TC-AUDIT-03 — Filtrar por tabla

```
GET /api/audit/logs?table=domains
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK` — solo logs relacionados con dominios.

---

#### ✅ TC-AUDIT-04 — Filtrar por el email del actor

```
GET /api/audit/logs?actorEmail=admin@leydata.cl
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK` — solo acciones del admin.

---

#### ✅ TC-AUDIT-05 — Filtrar por email que no tiene logs

```
GET /api/audit/logs?actorEmail=noexiste@leydata.cl
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{ "logs": [], "total": 0 }
```

---

#### ✅ TC-AUDIT-09 — Verificar que una acción quedó registrada

**Propósito:** Confirmar que el log se creó correctamente después de ejecutar otra acción.

**Prerrequisito:** Ejecutar TC-USER-14 (bloquear usuario).

```
GET /api/audit/logs?action=BLOQUEAR_USUARIO
Authorization: Bearer <admin_token>
```

**El log debe tener:**

```json
{
  "action": "BLOQUEAR_USUARIO",
  "tableName": "users",
  "actorRole": "ADMIN",
  "oldData": "{\"active\":true,\"blocked\":false}",
  "newData": "{\"active\":false,\"blocked\":true}",
  "logHash": "<sha256_hex>"
}
```

---

#### ❌ TC-AUDIT-06 — DPO intenta ver los logs

```
GET /api/audit/logs
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `403 Forbidden`

---

#### ❌ TC-AUDIT-07 — Sin token

```
GET /api/audit/logs
```

**Respuesta esperada:** `401 Unauthorized`

---

### `GET /api/audit/logs/verify` — Verificar integridad de la cadena de auditoría

Verifica que ningún log fue manipulado revisando la cadena de hashes SHA-256.

Cada registro firma su contenido (incluyendo el hash del log anterior) con SHA-256. Si algún registro fue alterado o eliminado, la cadena se rompe y el endpoint retorna `valid: false`.

> **Importante:** Los logs generados antes de la corrección del bug de precisión de timestamp (nanosegundos vs microsegundos) tendrán hashes inválidos permanentemente. Los logs generados con la versión actual del código se verifican correctamente.

---

#### ✅ TC-AUDIT-08 — Verificar cadena íntegra

```
GET /api/audit/logs/verify
Authorization: Bearer <admin_token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "valid": true,
  "message": "Cadena de auditoría íntegra. Ningún registro ha sido alterado."
}
```

**Si `valid: false` y no hubo manipulación:** Los logs existentes tienen hashes inválidos por la diferencia de precisión de timestamp (bug pre-corrección). Limpiar la tabla con `TRUNCATE system_audit_log` y ejecutar nuevas acciones para poblarla con logs válidos.

---

## 9. Módulo Documentos de Privacidad — `/api/privacy-documents`

> **Acceso por rol:**
> - Solo `DPO`: todo el workflow legal — crear, editar, desactivar, vincular propósitos, submit, resubmit, aprobar, rechazar, publicar, archivar
> - `ADMIN`: gestiona el sistema (usuarios, dominios) pero **no interviene en documentos de privacidad**
> - Cualquier autenticado: GET (listar, obtener, descargar PDF, verificar integridad)
>
> **Nota:** el PDF está abierto a todos los autenticados de forma temporal. Cuando se implemente el rol `TITULAR` (titulares de datos), el acceso a `/pdf` y `/active` se restringirá a ese rol.

> **Workflow de estados:**
> ```
> DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
>                  ↘ REJECTED → IN_REVIEW (resubmit)
> ```

> **Categorías disponibles (`DocumentCategory`):** `POLITICA_PRIVACIDAD`, `AVISO_COOKIES`, `DATOS_SENSIBLES`, `MARKETING_DIRECTO`, `MENORES_EDAD`, `TRANSFERENCIA_TERCEROS`

---

### `POST /api/privacy-documents` — Crear documento

El documento se crea en estado `DRAFT`. El `createdBy` se extrae automáticamente del JWT — no va en el body.

**Body:**

```json
{
  "category": "POLITICA_PRIVACIDAD | AVISO_COOKIES | ...",
  "name": "string",
  "content": "string (puede estar vacío en DRAFT)",
  "templateId": "uuid (opcional en DRAFT, obligatorio antes de enviar a revisión)"
}
```

> El documento **no se vincula directamente a un dominio**. La relación con dominios es indirecta: a través de las finalidades (`Purposes`) que se vinculan al documento. Un documento puede acreditar finalidades de múltiples dominios.

---

#### ✅ TC-DOC-01 — Crear documento en DRAFT exitosamente

**Prerrequisito:** Ninguno. Solo se requiere token DPO.
Token: DPO.

```json
POST /api/privacy-documents
Authorization: Bearer <dpo_token>

{
  "category": "POLITICA_PRIVACIDAD",
  "name": "Política de Privacidad RRHH v1",
  "content": "Contenido borrador inicial..."
}
```

**Respuesta esperada:** `201 Created`

```json
{
  "id": "<uuid>",
  "documentFamilyId": "<mismo-uuid-que-id>",
  "category": "POLITICA_PRIVACIDAD",
  "status": "DRAFT",
  "version": 1,
  "name": "Política de Privacidad RRHH v1",
  "content": "Contenido borrador inicial...",
  "isActive": true,
  "purposeIds": [],
  "createdAt": "2026-06-16T..."
}
```

**Verificar:** `documentFamilyId` debe ser igual a `id` (auto-asignado al crear).

---

#### ❌ TC-DOC-02 — Crear sin nombre (campo obligatorio)

```json
POST /api/privacy-documents
Authorization: Bearer <dpo_token>

{
  "category": "POLITICA_PRIVACIDAD"
}
```

**Respuesta esperada:** `400 Bad Request`

```json
{ "error": "El nombre es obligatorio" }
```

---

### `GET /api/privacy-documents/{id}` — Obtener documento por ID

#### ✅ TC-DOC-03 — Obtener documento existente

```
GET /api/privacy-documents/<uuid_documento>
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` con los datos completos del documento.

---

#### ❌ TC-DOC-04 — UUID de documento inexistente

```
GET /api/privacy-documents/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
Authorization: Bearer <token>
```

**Respuesta esperada:** `404 Not Found`

```json
{ "error": "Documento no encontrado: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" }
```

---

### `GET /api/privacy-documents` — Listar documentos

Todos los parámetros son opcionales. Sin parámetros devuelve todos los documentos.

#### ✅ TC-DOC-05 — Listar con filtro por categoría y estado

```
GET /api/privacy-documents?category=POLITICA_PRIVACIDAD&status=DRAFT
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` — lista de documentos que cumplen ambos filtros.

---

### `PATCH /api/privacy-documents/{id}` — Editar documento

Solo funciona si el documento está en estado `DRAFT`.

**Body (todos los campos son opcionales):**

```json
{
  "name": "string",
  "content": "string",
  "templateId": "uuid"
}
```

---

#### ✅ TC-DOC-06 — Editar nombre y contenido en DRAFT

```json
PATCH /api/privacy-documents/<uuid_documento_draft>
Authorization: Bearer <token>

{
  "name": "Política de Privacidad RRHH v1 (corregida)",
  "content": "Contenido actualizado con las observaciones del equipo legal."
}
```

**Respuesta esperada:** `200 OK` con el documento actualizado.

**Verificar:** el campo `version` permanece en `1` — editar un DRAFT **no** incrementa la versión. La versión sube únicamente al crear una nueva versión vía `POST /{id}/new-version`.

---

#### ❌ TC-DOC-07 — Intentar editar un documento en IN_REVIEW

**Prerrequisito:** Ejecutar TC-DOC-13 primero para tener un documento en `IN_REVIEW`.

```json
PATCH /api/privacy-documents/<uuid_documento_in_review>
Authorization: Bearer <token>

{
  "name": "Intento de edición"
}
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "La operación requiere estado DRAFT (actual: IN_REVIEW)" }
```

---

### `POST /api/privacy-documents/{id}/deactivate` — Desactivar documento

Marca el documento como inactivo (`isActive = false`). **No lo borra de la base de datos** — queda disponible para auditoría y se puede recuperar por ID.

**Regla de negocio:** El documento no puede tener finalidades activas (`is_active = true` en `document_purposes`). Si las tiene → `422`. No hay restricción de estado: un documento en cualquier estado puede desactivarse siempre que no tenga finalidades activas. Para desactivar un documento con finalidades, primero se deben desvincular con `DELETE /{id}/purposes/{purposeId}`.

> **Por qué no hay DELETE físico:** Los documentos de privacidad son registros con trazabilidad legal (Ley 21.719). Conservar el registro asegura que toda acción quede auditada.

#### ✅ TC-DOC-08 — Desactivar documento sin finalidades activas

**Prerrequisito:** Documento sin finalidades activas (recién creado, o con todas las finalidades desvinculadas).

```
POST /api/privacy-documents/<uuid_documento_sin_finalidades>/deactivate
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` — el documento con `"isActive": false`. Ya no aparece en `GET /api/privacy-documents` (el listado filtra solo activos), pero sigue accesible por su ID.

---

#### ❌ TC-DOC-09 — Desactivar documento con finalidades activas

**Prerrequisito:** Ejecutar TC-DOC-10 primero para tener un documento con al menos una finalidad activa.

```
POST /api/privacy-documents/<uuid_documento_con_finalidades>/deactivate
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "No se puede desactivar el documento: tiene finalidades activas asociadas. Desvinculá todas las finalidades primero." }
```

---

### `POST /api/privacy-documents/{id}/purposes/{purposeId}` — Vincular propósito

Solo funciona en `DRAFT`. El propósito debe estar en estado `APPROVED`. No hay restricción de dominio — un documento puede acreditar finalidades de múltiples dominios.

---

#### ✅ TC-DOC-10 — Vincular propósito aprobado

**Prerrequisito:** Tener un propósito con `status = APPROVED`. No hay restricción de dominio — un documento puede acreditar propósitos de cualquier dominio.

```
POST /api/privacy-documents/<uuid_doc>/purposes/<uuid_proposito_aprobado>
Authorization: Bearer <token>
```

**Respuesta esperada:** `204 No Content`

---

#### ❌ TC-DOC-11 — Vincular propósito que no está aprobado (ej: PENDING)

**Prerrequisito:** Tener el UUID de una solicitud de propósito en estado `PENDING` (no aprobada aún).

```
POST /api/privacy-documents/<uuid_doc>/purposes/<uuid_proposito_pending>
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "El propósito <uuid> no está aprobado" }
```

---

#### ❌ TC-DOC-12 — Vincular el mismo propósito dos veces

**Prerrequisito:** Ejecutar TC-DOC-10 primero.

```
POST /api/privacy-documents/<uuid_doc>/purposes/<uuid_proposito_ya_vinculado>
Authorization: Bearer <token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "El propósito ya está vinculado a este documento" }
```

---

### Workflow de estados

#### ✅ TC-DOC-13 — Enviar a revisión (DRAFT → IN_REVIEW)

**Prerrequisito:** El documento debe tener contenido, templateId y al menos un propósito vinculado.

```
POST /api/privacy-documents/<uuid_doc>/submit
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` con `status: "IN_REVIEW"`.

---

#### ❌ TC-DOC-14 — Enviar sin contenido

```json
POST /api/privacy-documents/<uuid_doc_sin_contenido>/submit
Authorization: Bearer <token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "El contenido no puede estar vacío antes de enviar a revisión" }
```

---

#### ❌ TC-DOC-15 — Enviar sin template asignado

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "Debe asignar una Template antes de enviar a revisión" }
```

---

#### ❌ TC-DOC-16 — Enviar sin propósitos vinculados

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "El documento debe tener al menos un propósito vinculado" }
```

---

#### ✅ TC-DOC-17 — Aprobar documento (IN_REVIEW → APPROVED)

**Prerrequisito:** Ejecutar TC-DOC-13 para tener un documento en `IN_REVIEW`.
Token: DPO (aplicado con `@PreAuthorize("hasRole('DPO')")`).

```
POST /api/privacy-documents/<uuid_doc_in_review>/approve
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` con `status: "APPROVED"` y `approvedBy: "<uuid_del_dpo>"`.

---

#### ✅ TC-DOC-18 — Rechazar documento con motivo (IN_REVIEW → REJECTED)

```json
POST /api/privacy-documents/<uuid_doc_in_review>/reject
Authorization: Bearer <dpo_token>

{
  "reason": "El contenido de la sección 3 no cumple con el Art. 14 de la Ley 21.719. Reformular."
}
```

**Respuesta esperada:** `200 OK` con `status: "REJECTED"` y `rejectionReason` poblado.

---

#### ✅ TC-DOC-19 — Reenviar tras rechazo (REJECTED → IN_REVIEW)

**Prerrequisito:** Tener un documento con `status = REJECTED`. Ejecutar TC-DOC-18 primero.

```
POST /api/privacy-documents/<uuid_doc_rejected>/resubmit
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` con `status: "IN_REVIEW"` y `rejectionReason: null`.

---

#### ✅ TC-DOC-20 — Publicar documento (APPROVED → PUBLISHED)

**Prerrequisito:** Documento en `APPROVED`. Ejecutar TC-DOC-17 primero.
Token: DPO.

**¿Qué hace este endpoint?** Genera el PDF en memoria, calcula su SHA-256, y marca el documento como `PUBLISHED`.

```
POST /api/privacy-documents/<uuid_doc_approved>/publish
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` con `status: "PUBLISHED"`, `hashSha256` poblado, y `publishAt` con la fecha de publicación.

**Nota:** publicar **no archiva** automáticamente versiones anteriores publicadas de la misma categoría. Múltiples versiones pueden coexistir en estado `PUBLISHED` — esto es intencional para que los consentimientos ya otorgados sigan referenciando el documento con el que el titular consintió.

---

#### ❌ TC-DOC-21 — Transición inválida (ej: publicar un DRAFT directamente)

```
POST /api/privacy-documents/<uuid_doc_draft>/publish
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `409 Conflict`

```json
{ "error": "Transición inválida: DRAFT → PUBLISHED" }
```

---

#### ✅ TC-DOC-22 — Archivar documento publicado (PUBLISHED → ARCHIVED)

**Prerrequisito:** Documento en `PUBLISHED`. Ejecutar TC-DOC-20 primero.

```
POST /api/privacy-documents/<uuid_doc_published>/archive
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `200 OK` con `status: "ARCHIVED"`.

---

### Utilidades

#### ✅ TC-DOC-23 — Descargar PDF del documento publicado

**Prerrequisito:** Documento en `PUBLISHED` o `ARCHIVED`.

```
GET /api/privacy-documents/<uuid_doc_published>/pdf
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` con `Content-Type: application/pdf` y el binario del PDF como body. El header incluye `Content-Disposition: attachment; filename="privacy-document-<uuid>.pdf"`.

---

#### ❌ TC-DOC-24 — Descargar PDF de un documento que no fue publicado

```
GET /api/privacy-documents/<uuid_doc_draft>/pdf
Authorization: Bearer <token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "error": "El documento no tiene PDF generado. Debe estar en estado PUBLISHED." }
```

---

#### ✅ TC-DOC-25 — Verificar integridad SHA-256 del PDF

**Prerrequisito:** Documento en `PUBLISHED`.

```
GET /api/privacy-documents/<uuid_doc_published>/verify
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK`

```json
{
  "documentId": "<uuid>",
  "version": 1,
  "hashMatch": true,
  "storedHash": "<sha256_hex>",
  "computedHash": "<sha256_hex>",
  "message": "Integridad verificada: el PDF almacenado coincide con el hash registrado"
}
```

---

#### ✅ TC-DOC-26 — Obtener documento publicado activo por categoría

```
GET /api/privacy-documents/active?category=POLITICA_PRIVACIDAD
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` con el documento en estado `PUBLISHED` para esa categoría (versión canónica: mayor número de versión publicado).

---

#### ❌ TC-DOC-27 — No existe documento publicado para esa categoría

```
GET /api/privacy-documents/active?category=MENORES_EDAD
Authorization: Bearer <token>
```

**Respuesta esperada:** `404 Not Found`

---

### Auditoría de documentos de privacidad

> Cada operación sobre un documento genera un log en `system_audit_log`. El log y la operación son atómicos: si uno falla, el otro también.

---

#### ✅ TC-DOC-28 — Crear documento genera log CREAR_DOCUMENTO

**Prerrequisito:** Ninguno.

```
POST /api/privacy-documents
Authorization: Bearer <dpo_token>
```

Luego:

```
GET /api/audit/logs?tableName=privacy_documents&action=CREAR_DOCUMENTO
Authorization: Bearer <admin_token>
```

**Resultado esperado:** Existe al menos 1 log con `action=CREAR_DOCUMENTO`, `actorRole=DPO` y `recordId` igual al `id` del documento creado.

---

#### ✅ TC-DOC-29 — Desactivar documento genera log DESACTIVAR_DOCUMENTO

**Prerrequisito:** Documento en `DRAFT` (ejecutar TC-DOC-01).

```
POST /api/privacy-documents/{id}/deactivate
Authorization: Bearer <dpo_token>
```

Luego verificar en audit:

```
GET /api/audit/logs?tableName=privacy_documents&action=DESACTIVAR_DOCUMENTO
Authorization: Bearer <admin_token>
```

**Resultado esperado:** Log con `action=DESACTIVAR_DOCUMENTO`, `newData.isActive=false`.

---

#### ✅ TC-DOC-30 — Publicar documento genera log PUBLICAR_DOCUMENTO

**Prerrequisito:** Documento en `APPROVED` (ejecutar flujo hasta TC-DOC-17).

```
POST /api/privacy-documents/{id}/publish
Authorization: Bearer <dpo_token>
```

**Resultado esperado:** Log con `action=PUBLICAR_DOCUMENTO` y `newData.status=PUBLISHED`.

---

#### ✅ TC-DOC-31 — Aprobar documento genera log APROBAR_DOCUMENTO

**Prerrequisito:** Documento en `IN_REVIEW`.

```
POST /api/privacy-documents/{id}/approve
Authorization: Bearer <dpo_token>
```

**Resultado esperado:** Log con `action=APROBAR_DOCUMENTO` y `actorRole=DPO`.

---

#### ✅ TC-DOC-32 — Rechazar documento genera log RECHAZAR_DOCUMENTO

**Prerrequisito:** Documento en `IN_REVIEW`.

```
POST /api/privacy-documents/{id}/reject
Authorization: Bearer <dpo_token>
Body: { "reason": "Falta información legal" }
```

**Resultado esperado:** Log con `action=RECHAZAR_DOCUMENTO` y `newData.rejectionReason` no nulo.

---

### Familia de documentos (versionado)

#### ✅ TC-DOC-33 — Crear nueva versión de un documento publicado

**Prerrequisito:** Documento en `PUBLISHED` (ejecutar flujo hasta TC-DOC-20).
Token: DPO.

```
POST /api/privacy-documents/<uuid_doc_published>/new-version
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `201 Created`

```json
{
  "id": "<nuevo-uuid>",
  "documentFamilyId": "<uuid-del-doc-origen>",
  "status": "DRAFT",
  "version": 2,
  "name": "Política de Privacidad RRHH v1",
  "content": "..."
}
```

**Verificar:**
- `documentFamilyId` es igual al `id` del documento origen
- `version` es el máximo de la familia + 1
- El documento origen sigue en estado `PUBLISHED` (no se modifica)

---

#### ❌ TC-DOC-34 — Bloquear segunda nueva versión cuando ya existe un DRAFT en la familia

**Prerrequisito:** Ejecutar TC-DOC-33 primero (ya hay un DRAFT v2 en la familia).
Token: DPO.

```
POST /api/privacy-documents/<uuid_doc_published>/new-version
Authorization: Bearer <dpo_token>
```

**Respuesta esperada:** `422 Unprocessable Entity`

```json
{ "message": "Ya existe un borrador activo en esta familia de documentos. Publicá o desactivá el DRAFT existente antes de crear una nueva versión." }
```

---

#### ✅ TC-DOC-35 — Listar todas las versiones de una familia

**Prerrequisito:** Tener al menos 2 versiones en una familia (TC-DOC-33 ejecutado).
Token: Cualquier autenticado.

```
GET /api/privacy-documents/family/<familyId>
Authorization: Bearer <token>
```

**Respuesta esperada:** `200 OK` — array ordenado por versión descendente:

```json
[
  { "id": "...", "documentFamilyId": "<familyId>", "version": 2, "status": "DRAFT", ... },
  { "id": "...", "documentFamilyId": "<familyId>", "version": 1, "status": "PUBLISHED", ... }
]
```

> El `familyId` es el `documentFamilyId` de cualquier documento de la familia (siempre igual al `id` del primer documento creado).

---

## 10. Módulo Notificaciones — `/api/notifications`

> **Acceso:** Cualquier usuario autenticado — cada uno ve y gestiona únicamente sus propias notificaciones.
> **Triggers de notificación:**
> - DPO aprueba o rechaza una solicitud de propósito → notificación al JEFE_DOMINIO que la creó
> - DPO publica un documento de privacidad → notificación a todos los JEFE_DOMINIO activos del dominio

---

### `GET /api/notifications` — Mis notificaciones

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

---

#### ✅ TC-NOTIF-01 — Listar notificaciones cuando no hay ninguna

**Pre-condición:** Usuario sin notificaciones (recién creado o BD limpia).
**Request:** `GET /api/notifications` con token válido.
**Resultado esperado:** `200 OK` — Array vacío `[]`.

---

#### ✅ TC-NOTIF-02 — Listar notificaciones con items

**Pre-condición:** DPO aprueba o rechaza una solicitud del JEFE_DOMINIO.
**Request:** `GET /api/notifications` con token del JEFE_DOMINIO.
**Resultado esperado:** `200 OK` — Array con al menos 1 notificación. El campo `read` es `false`.

---

### `GET /api/notifications/unread-count` — Conteo de no leídas

Retorna el número de notificaciones no leídas del usuario autenticado.

**Respuesta esperada (200):**

```json
{ "count": 2 }
```

---

#### ✅ TC-NOTIF-03 — Conteo no leídas: usuario sin notificaciones

**Request:** `GET /api/notifications/unread-count` con token válido (usuario sin notificaciones).
**Resultado esperado:** `200 OK` — `{"count": 0}`.

---

#### ✅ TC-NOTIF-04 — Conteo no leídas: después de que llegan notificaciones

**Pre-condición:** JEFE_DOMINIO tiene 2 notificaciones sin leer.
**Request:** `GET /api/notifications/unread-count` con token del JEFE_DOMINIO.
**Resultado esperado:** `200 OK` — `{"count": 2}`.

---

### `PATCH /api/notifications/{id}/read` — Marcar una como leída

Marca una notificación específica como leída. La operación es idempotente si ya estaba leída.

**Resultado esperado:** `204 No Content`.

---

#### ✅ TC-NOTIF-05 — Marcar notificación propia como leída

**Pre-condición:** JEFE_DOMINIO tiene una notificación con `read: false`.
**Request:** `PATCH /api/notifications/{id}/read` con token del dueño de la notificación.
**Resultado esperado:** `200 OK` con el objeto notificación actualizado (`read: true`). Al volver a `GET /api/notifications`, el campo `read` del item pasa a `true`.

---

#### ❌ TC-NOTIF-06 — Marcar notificación de otro usuario (400)

**Pre-condición:** Se intenta marcar una notificación que pertenece a un usuario diferente.
**Request:** `PATCH /api/notifications/{id-ajeno}/read` con token de un usuario distinto al destinatario.
**Resultado esperado:** `400 Bad Request` — `"La notificación no pertenece a este usuario"`.
**Regla de negocio:** El sistema verifica que `notification.recipientId == currentUserId` antes de persistir el cambio.

---

### `PATCH /api/notifications/read-all` — Marcar todas como leídas

Marca todas las notificaciones del usuario autenticado como leídas en una sola operación (UPDATE en BD).

**Resultado esperado:** `204 No Content`.

---

#### ✅ TC-NOTIF-07 — Marcar todas como leídas

**Pre-condición:** JEFE_DOMINIO tiene múltiples notificaciones sin leer.
**Request:** `PATCH /api/notifications/read-all` con token del usuario.
**Resultado esperado:** `204 No Content`. Llamar `GET /api/notifications/unread-count` retorna `{"count": 0}`.

---

#### ✅ TC-NOTIF-08 — Integración: notificación generada al aprobar solicitud

**Pre-condición:** JEFE_DOMINIO crea una solicitud de propósito (`POST /api/purpose-requests`). DPO la aprueba con `PATCH /api/purpose-requests/{id}/review` (status: `APPROVED`).
**Verificación:** Llamar `GET /api/notifications` con token del JEFE_DOMINIO.
**Resultado esperado:** La lista incluye una notificación de tipo `PURPOSE_APPROVED` referenciando el ID de la solicitud. Si `MAIL_ENABLED=true`, se envía adicionalmente un email al correo del JEFE_DOMINIO.

---

## 11. Tabla resumen de todos los casos

### Documentos de Privacidad

| ID          | Endpoint                          | Escenario                          | Resultado |
| ----------- | --------------------------------- | ---------------------------------- | --------- |
| TC-USER-01  | POST /api/users                   | Crear DPO                          | ✅ 201    |
| TC-USER-02  | POST /api/users                   | Crear JEFE_DOMINIO con dominio     | ✅ 201    |
| TC-USER-23  | POST /api/users                   | Crear JEFE sin dominio inicial     | ✅ 201    |
| TC-USER-03  | POST /api/users                   | Email duplicado                    | ❌ 409    |
| TC-USER-04  | POST /api/users                   | Rol inválido                       | ❌ 400    |
| TC-USER-05  | POST /api/users                   | Sin token                          | ❌ 401    |
| TC-USER-06  | POST /api/users                   | Token DPO (rol insuficiente)       | ❌ 403    |
| TC-USER-07  | GET /api/users                    | Listar como ADMIN                  | ✅ 200    |
| TC-USER-08  | GET /api/users                    | Sin token                          | ❌ 401    |
| TC-USER-09  | GET /api/users/{id}               | Usuario existente                  | ✅ 200    |
| TC-USER-10  | GET /api/users/{id}               | UUID inexistente                   | ❌ 404    |
| TC-USER-11  | PUT /api/users/{id}               | Editar nombre                      | ✅ 200    |
| TC-USER-12  | PUT /api/users/{id}               | ADMIN editándose a sí mismo        | ❌ 400    |
| TC-USER-13  | PUT /api/users/{id}               | Usuario bloqueado                  | ❌ 409    |
| TC-USER-14  | POST /api/users/{id}/block        | Bloquear usuario activo            | ✅ 200    |
| TC-USER-15  | POST /api/users/{id}/block        | Ya estaba bloqueado                | ❌ 409    |
| TC-USER-16  | POST /api/users/{id}/block        | ADMIN bloqueándose a sí mismo      | ❌ 400    |
| TC-USER-17  | POST /api/users/{id}/deactivate   | Desactivar usuario activo          | ✅ 200    |
| TC-USER-18  | POST /api/users/{id}/deactivate   | Ya estaba inactivo                 | ❌ 409    |
| TC-USER-19  | GET /api/users                    | Usuario desactivado usa el sistema | ❌ 403    |
| TC-USER-20  | POST /api/users/{id}/reactivate   | Reactivar usuario inactivo         | ✅ 200    |
| TC-USER-21  | POST /api/users/{id}/reactivate   | Ya estaba activo                   | ❌ 409    |
| TC-USER-22  | POST /api/users/{id}/reactivate   | Usuario bloqueado permanente       | ❌ 409    |
| TC-USER-24  | POST /api/users                   | keycloakId vinculado en creación   | ✅ —      |
| TC-USER-25  | Email cambiado en Keycloak        | Resiliencia por keycloakId         | ✅ —      |
| TC-DOM-01   | POST /api/domains                 | Crear sin jefe                     | ✅ 201    |
| TC-DOM-02   | POST /api/domains                 | Crear con JEFE_DOMINIO             | ✅ 201    |
| TC-DOM-03   | POST /api/domains                 | Código duplicado                   | ❌ 400    |
| TC-DOM-04   | POST /api/domains                 | Sin token                          | ❌ 401    |
| TC-DOM-05   | POST /api/domains                 | Token DPO                          | ❌ 403    |
| TC-DOM-06   | GET /api/domains/all              | Listar como ADMIN                  | ✅ 200    |
| TC-DOM-07   | POST /api/domains/{id}/deactivate | Desactivar activo                  | ✅ 200    |
| TC-DOM-08   | POST /api/domains/{id}/deactivate | UUID inexistente                   | ❌ 404    |
| TC-DOM-09   | POST /api/domains/{id}/deactivate | Ya estaba inactivo                 | ❌ 409    |
| TC-DOM-10   | POST /api/domains/{id}/reactivate | Reactivar inactivo                 | ✅ 200    |
| TC-DOM-11   | POST /api/domains/{id}/reactivate | Ya estaba activo                   | ❌ 409    |
| TC-PUR-01   | POST /api/purpose-requests        | Crear solicitud                    | ✅ 201    |
| TC-PUR-02   | POST /api/purpose-requests        | Dominio ajeno                      | ❌ 400    |
| TC-PUR-03   | POST /api/purpose-requests        | Dominio inactivo                   | ❌ 409    |
| TC-PUR-04   | POST /api/purpose-requests        | Token DPO                          | ❌ 403    |
| TC-PUR-05   | GET /api/purpose-requests/my      | Mis solicitudes                    | ✅ 200    |
| TC-PUR-06   | GET /api/purpose-requests/my      | Token ADMIN                        | ❌ 403    |
| TC-PUR-07   | GET /api/purpose-requests/pending | Pendientes como DPO                | ✅ 200    |
| TC-PUR-08   | GET /api/purpose-requests/pending | Token JEFE_DOMINIO                 | ❌ 403    |
| TC-PUR-09   | GET /api/purpose-requests         | Historial completo (DPO)           | ✅ 200    |
| TC-PUR-10   | PATCH .../review                  | Aprobar solicitud                  | ✅ 200    |
| TC-PUR-11   | PATCH .../review                  | Rechazar con notas                 | ✅ 200    |
| TC-PUR-12   | PATCH .../review                  | Rechazar sin notas                 | ❌ 400    |
| TC-PUR-13   | PATCH .../review                  | Solicitud ya revisada              | ❌ 409    |
| TC-PUR-14   | PATCH .../review                  | Estado inválido                    | ❌ 400    |
| TC-AUDIT-01 | GET /api/audit/logs               | Todos los logs paginados           | ✅ 200    |
| TC-AUDIT-02 | GET /api/audit/logs?action=       | Filtrar por acción                 | ✅ 200    |
| TC-AUDIT-03 | GET /api/audit/logs?table=        | Filtrar por tabla                  | ✅ 200    |
| TC-AUDIT-04 | GET /api/audit/logs?actorEmail=   | Filtrar por actor                  | ✅ 200    |
| TC-AUDIT-05 | GET /api/audit/logs?actorEmail=   | Actor sin logs                     | ✅ 200    |
| TC-AUDIT-09 | GET /api/audit/logs               | Verificar log generado             | ✅ 200    |
| TC-AUDIT-06 | GET /api/audit/logs               | Token DPO                          | ❌ 403    |
| TC-AUDIT-07 | GET /api/audit/logs               | Sin token                          | ❌ 401    |
| TC-AUDIT-08 | GET /api/audit/logs/verify        | Verificar integridad               | ✅ 200    |

| TC-DOC-01   | POST /api/privacy-documents              | Crear documento en DRAFT                  | ✅ 201    |
| TC-DOC-02   | POST /api/privacy-documents              | Sin nombre (campo obligatorio)            | ❌ 400    |
| TC-DOC-03   | GET /api/privacy-documents/{id}          | Obtener documento existente               | ✅ 200    |
| TC-DOC-04   | GET /api/privacy-documents/{id}          | UUID inexistente                          | ❌ 404    |
| TC-DOC-05   | GET /api/privacy-documents               | Listar con filtros                        | ✅ 200    |
| TC-DOC-06   | PATCH /api/privacy-documents/{id}        | Editar en DRAFT                           | ✅ 200    |
| TC-DOC-07   | PATCH /api/privacy-documents/{id}        | Editar fuera de DRAFT                     | ❌ 422    |
| TC-DOC-08   | POST /api/privacy-documents/{id}/deactivate | Desactivar sin finalidades activas     | ✅ 200    |
| TC-DOC-09   | POST /api/privacy-documents/{id}/deactivate | Desactivar con finalidades activas     | ❌ 422    |
| TC-DOC-10   | POST .../purposes/{purposeId}            | Vincular propósito aprobado               | ✅ 204    |
| TC-DOC-11   | POST .../purposes/{purposeId}            | Propósito no aprobado (PENDING)           | ❌ 422    |
| TC-DOC-12   | POST .../purposes/{purposeId}            | Propósito ya vinculado                    | ❌ 422    |
| TC-DOC-13   | POST .../submit                          | DRAFT → IN_REVIEW                         | ✅ 200    |
| TC-DOC-14   | POST .../submit                          | Sin contenido                             | ❌ 422    |
| TC-DOC-15   | POST .../submit                          | Sin template                              | ❌ 422    |
| TC-DOC-16   | POST .../submit                          | Sin propósitos vinculados                 | ❌ 422    |
| TC-DOC-17   | POST .../approve                         | IN_REVIEW → APPROVED                      | ✅ 200    |
| TC-DOC-18   | POST .../reject                          | IN_REVIEW → REJECTED con motivo           | ✅ 200    |
| TC-DOC-19   | POST .../resubmit                        | REJECTED → IN_REVIEW                      | ✅ 200    |
| TC-DOC-20   | POST .../publish                         | APPROVED → PUBLISHED (genera PDF)         | ✅ 200    |
| TC-DOC-21   | POST .../publish                         | Transición inválida (DRAFT → PUBLISHED)   | ❌ 409    |
| TC-DOC-22   | POST .../archive                         | PUBLISHED → ARCHIVED                      | ✅ 200    |
| TC-DOC-23   | GET .../pdf                              | Descargar PDF publicado                   | ✅ 200    |
| TC-DOC-24   | GET .../pdf                              | Documento sin PDF                         | ❌ 422    |
| TC-DOC-25   | GET .../verify                           | Integridad SHA-256 válida                 | ✅ 200    |
| TC-DOC-26   | GET /active?category=                    | Documento publicado activo (canónico)     | ✅ 200    |
| TC-DOC-27   | GET /active?category=                    | Sin documento publicado                   | ❌ 404    |
| TC-DOC-28   | POST /api/privacy-documents              | Audit log CREAR_DOCUMENTO generado        | ✅ —      |
| TC-DOC-29   | POST .../{id}/deactivate                 | Audit log DESACTIVAR_DOCUMENTO generado   | ✅ —      |
| TC-DOC-30   | POST .../{id}/publish                    | Audit log PUBLICAR_DOCUMENTO generado     | ✅ —      |
| TC-DOC-31   | POST .../{id}/approve                    | Audit log APROBAR_DOCUMENTO generado      | ✅ —      |
| TC-DOC-32   | POST .../{id}/reject                     | Audit log RECHAZAR_DOCUMENTO generado     | ✅ —      |
| TC-DOC-33   | POST .../{id}/new-version                | Crear v2 DRAFT desde PUBLISHED            | ✅ 201    |
| TC-DOC-34   | POST .../{id}/new-version                | Bloquear cuando DRAFT ya existe en familia | ❌ 422   |
| TC-DOC-35   | GET /family/{familyId}                   | Listar versiones activas de la familia    | ✅ 200    |

| TC-NOTIF-01 | GET /api/notifications                   | Lista vacía                               | ✅ 200    |
| TC-NOTIF-02 | GET /api/notifications                   | Con notificaciones                        | ✅ 200    |
| TC-NOTIF-03 | GET /api/notifications/unread-count      | Sin notificaciones                        | ✅ 200    |
| TC-NOTIF-04 | GET /api/notifications/unread-count      | Con no leídas                             | ✅ 200    |
| TC-NOTIF-05 | PATCH /api/notifications/{id}/read       | Marcar propia como leída                  | ✅ 200    |
| TC-NOTIF-06 | PATCH /api/notifications/{id}/read       | Marcar la de otro usuario                 | ❌ 400    |
| TC-NOTIF-07 | PATCH /api/notifications/read-all        | Marcar todas como leídas                  | ✅ 204    |
| TC-NOTIF-08 | Integración DPO → JEFE_DOMINIO           | Notificación generada al aprobar          | ✅ —      |

**Total: 102 casos — 61 positivos ✅ · 41 negativos ❌**

---

## 12. Bugs encontrados y corregidos durante las pruebas

Esta sección documenta los bugs descubiertos al ejecutar el plan de pruebas completo por primera vez sobre el sistema real, y las correcciones aplicadas. Sirve como referencia para entender por qué ciertos diseños son como son.

---

### BUG-01 — Logs de auditoría no se guardaban (EntityExistsException)

**Síntoma:** Ninguna acción generaba logs en `system_audit_log`. El endpoint `GET /api/audit/logs` siempre devolvía `"logs": []`. No había error visible porque `tryLog()` capturaba la excepción en silencio.

**Causa raíz:** `SystemAuditLog` tenía `@GeneratedValue(strategy = UUID)` pero el código asignaba el UUID manualmente antes de persistir (necesario para incluirlo en el hash SHA-256). Hibernate 7 trata una entidad con `@GeneratedValue` y un ID ya seteado como "detached" (no nueva) → llama `merge()` en lugar de `persist()` → falla con `EntityExistsException`.

**Corrección aplicada:**
- Eliminado `@GeneratedValue` de `SystemAuditLog`
- La entidad ahora implementa `Persistable<UUID>` con `isNew()` retornando siempre `true`
- Esto fuerza a Spring Data JPA a llamar `persist()` (INSERT) en lugar de `merge()` (UPDATE) cuando el ID está pre-seteado
- Archivos modificados: `entity/SystemAuditLog.java`

---

### BUG-02 — Keycloak 26 bloquea el login con "Account is not fully set up"

**Síntoma:** Usuarios creados con `POST /api/users` se creaban correctamente (HTTP 201, registrados en KC y BD), pero al intentar hacer login con sus credenciales, Keycloak retornaba `"error": "account_not_fully_set_up"` con HTTP 400.

**Causa raíz:** `KeycloakAdminService.createUser()` enviaba `lastName: ""` (string vacío). Keycloak 26 configuró por defecto el User Profile del realm exigiendo `lastName` no vacío para el rol "user". String vacío pasa la validación de "campo presente" pero falla la validación de "campo no vacío".

**Corrección aplicada:**
- El campo `name` del request se divide en `firstName` (primera palabra) y `lastName` (resto)
- Si `name` tiene una sola palabra, se usa para ambos campos
- Ejemplo: `"Pedro Jefe"` → `firstName="Pedro"`, `lastName="Jefe"`
- Ejemplo: `"Ana"` → `firstName="Ana"`, `lastName="Ana"`
- Archivos modificados: `security/KeycloakAdminService.java`

**Usuarios afectados en el entorno de pruebas:** `jefe@leydata.cl`, `jefe2@leydata.cl`, `dpo2@leydata.cl` tuvieron que ser corregidos manualmente vía Keycloak Admin API actualizando su `lastName` a un valor no vacío.

---

### BUG-03 — `GET /api/audit/logs/verify` siempre retornaba `valid: false`

**Síntoma:** Después de corregir BUG-01 y generar logs nuevos, la verificación de integridad siempre fallaba aunque los registros no hubieran sido manipulados.

**Causa raíz:** Java 21 en Linux usa `clock_gettime(CLOCK_REALTIME)` que retorna nanosegundos (9 decimales). `LocalDateTime.now().toString()` producía strings como `"2026-06-09T00:12:45.868287456"`. PostgreSQL `timestamp(6)` trunca a microsegundos (6 decimales) al almacenar. Al recargar el registro, `createdAt.toString()` = `"2026-06-09T00:12:45.868287"`. Los últimos 3 dígitos difieren → SHA-256 diferente → `valid: false`.

**Corrección aplicada:**
- `LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);` en `AuditService.log()`
- El timestamp se trunca a microsegundos antes de computar el hash, igualando la precisión de lo que almacena PostgreSQL
- Archivos modificados: `audit/AuditService.java`

**Efecto colateral:** Los logs generados antes de esta corrección tienen hashes inválidos permanentemente y no pueden corregirse. Se requiere limpiar la tabla para restablecer una cadena válida.

---

### BUG-04 — `actor_role` en logs de auditoría mostraba `"offline_access"` u otro rol técnico

**Síntoma:** Al consultar `GET /api/audit/logs`, el campo `actor_role` de los registros mostraba `"offline_access"` o `"uma_authorization"` en vez de `"ADMIN"` o `"DPO"`.

**Causa raíz:** `getActorRole()` tomaba la primera authority con prefijo `ROLE_` del JWT. Keycloak incluye en `realm_access.roles` los roles técnicos antes que los de negocio: `["offline_access", "ADMIN", "uma_authorization", "default-roles-leydata"]`. El primer rol mapeado como `ROLE_offline_access` → el código tomaba `"offline_access"`.

**Corrección aplicada:**
- Se extrajo el método `getActorRole()` a `shared/SecurityContextHelper.java`, donde se define `BUSINESS_ROLES = Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER", "TITULAR")`
- Se filtra con `.filter(BUSINESS_ROLES::contains)` antes del `findFirst()`
- Los tres servicios ahora delegan en `SecurityContextHelper` — la lógica existe una sola vez
- Archivos modificados: `shared/SecurityContextHelper.java`, `user/application/service/UserService.java`, `orgdomain/application/service/DomainService.java`, `purpose/application/service/PurposeRequestService.java`

---

### BUG-05 — `tryLog()` tragaba excepciones sin registrarlas en el log

**Síntoma:** No se sabía que BUG-01 existía porque `tryLog()` capturaba la excepción y continuaba como si nada. No había ningún rastro del error en los logs.

**Causa raíz:** El bloque catch solo tenía `log.error(message)` sin pasar la excepción como argumento, por lo que el stack trace no se imprimía.

**Corrección aplicada:**
- Cambiado a `log.error("...", e)` (la excepción como último argumento de SLF4J imprime el stack trace completo)
- Archivos modificados: `audit/AuditService.java`

---

### Deuda técnica conocida (pendiente de corrección)

Estos ítems fueron identificados durante las pruebas pero no corregidos para no extender el alcance:

| # | Descripción | Impacto | Archivo |
|---|-------------|---------|---------|
| DT-02 | `@PreAuthorize("hasRole('DPO')")` en endpoints de revisión de solicitudes debería ser `hasAnyRole('DPO', 'ADMIN')` (el check real está en el servicio, no en el controlador) | Inconsistencia entre la anotación y la implementación real; ADMIN puede acceder pero la anotación no lo refleja | `purpose/web/PurposeRequestController.java` |
| DT-03 | La tabla `role` en la BD no tiene los roles `USER` y `TITULAR` sembrados por defecto | Si se intenta crear un usuario con esos roles, falla con "Rol no encontrado" | `init-db/` scripts · `seeder/DataSeeder.java` |
| DT-04 | Self-injection de `AuditService` con `@Autowired @Lazy` es necesaria para que `@Transactional(REQUIRES_NEW)` funcione correctamente (evitar bypass del proxy AOP por `this.log()`) | Si se remueve sin entender el motivo, los logs dejarán de ser transacciones independientes | `audit/application/service/AuditService.java` |
