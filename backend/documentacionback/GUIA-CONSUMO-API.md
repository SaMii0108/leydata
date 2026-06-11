# Guía de Consumo de API — Equipo Frontend

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)
**Backend URL:** `http://localhost:8080`
**Keycloak URL:** `http://localhost:8180`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
**Fecha:** 2026-06-09

---

## Índice

1. [Cómo funciona la autenticación](#1-cómo-funciona-la-autenticación)
2. [Obtener y renovar el token](#2-obtener-y-renovar-el-token)
3. [Cómo enviar el token en cada request](#3-cómo-enviar-el-token-en-cada-request)
4. [Manejo de errores](#4-manejo-de-errores)
5. [Endpoints disponibles](#5-endpoints-disponibles)
   - [Usuarios](#51-usuarios--apiusers)
   - [Dominios](#52-dominios--apidomains)
   - [Solicitudes de Propósito](#53-solicitudes-de-propósito--apipurpose-requests)
   - [Auditoría](#54-auditoría--apiaudit)
6. [Roles y control de acceso por pantalla](#6-roles-y-control-de-acceso-por-pantalla)
7. [Notas de implementación](#7-notas-de-implementación)

---

## 1. Cómo funciona la autenticación

El sistema usa **Keycloak** como proveedor de identidad. El flujo es:

```
Usuario ingresa email + contraseña en el frontend
           │
           ▼
Frontend llama a Keycloak (no al backend)
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
           │
           ▼
Keycloak responde con un JWT (access_token)
           │
           ▼
Frontend incluye ese JWT en cada request al backend
Authorization: Bearer <access_token>
           │
           ▼
Backend valida el token, extrae el rol y autoriza o rechaza
```

**El backend no tiene endpoint de login.** No hay `POST /api/auth/login`. El token siempre viene de Keycloak.

---

## 2. Obtener y renovar el token

### Login

```
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
```

**Body (x-www-form-urlencoded):**

| Campo | Valor |
|-------|-------|
| `grant_type` | `password` |
| `client_id` | `leydata-frontend` |
| `username` | `<email del usuario>` |
| `password` | `<contraseña del usuario>` |

**Ejemplo en JavaScript / fetch:**

```javascript
async function login(email, password) {
  const params = new URLSearchParams({
    grant_type: 'password',
    client_id: 'leydata-frontend',
    username: email,
    password: password,
  });

  const response = await fetch(
    'http://localhost:8180/realms/leydata/protocol/openid-connect/token',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    }
  );

  if (!response.ok) {
    throw new Error('Credenciales incorrectas');
  }

  return await response.json();
  // Retorna: { access_token, refresh_token, expires_in: 300, ... }
}
```

**Respuesta exitosa:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "session_state": "...",
  "scope": "profile email"
}
```

**Guardar en el frontend:**
```javascript
localStorage.setItem('access_token', data.access_token);
localStorage.setItem('refresh_token', data.refresh_token);
// O usar sessionStorage / estado global (Zustand, Redux, Context)
```

---

### Renovar token sin pedir contraseña de nuevo

El `access_token` expira en **5 minutos**. Usar el `refresh_token` para obtener uno nuevo sin volver a pedir contraseña.

```javascript
async function refreshToken() {
  const storedRefreshToken = localStorage.getItem('refresh_token');

  const params = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: 'leydata-frontend',
    refresh_token: storedRefreshToken,
  });

  const response = await fetch(
    'http://localhost:8180/realms/leydata/protocol/openid-connect/token',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params.toString(),
    }
  );

  if (!response.ok) {
    // El refresh_token también expiró (dura 30 min) — redirigir al login
    redirectToLogin();
    return;
  }

  const data = await response.json();
  localStorage.setItem('access_token', data.access_token);
  localStorage.setItem('refresh_token', data.refresh_token);
  return data.access_token;
}
```

---

### Interceptor recomendado (Axios)

Configurar un interceptor para que los `401` renueven el token automáticamente:

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
});

// Agregar el token a cada request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Si el token expiró, renovar y reintentar
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const newToken = await refreshToken();
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      } catch {
        redirectToLogin();
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

---

## 3. Cómo enviar el token en cada request

Todos los endpoints del backend (excepto los de Keycloak) requieren el header:

```
Authorization: Bearer <access_token>
```

**Ejemplo con fetch:**
```javascript
const response = await fetch('http://localhost:8080/api/users', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('access_token')}`,
    'Content-Type': 'application/json',
  },
});
```

**Ejemplo con axios (usando el interceptor de arriba):**
```javascript
// El interceptor ya agrega el token — solo llamar normal
const response = await api.get('/api/users');
```

---

## 4. Manejo de errores

### Códigos HTTP posibles

| Código | Qué significa | Qué mostrar al usuario |
|--------|--------------|----------------------|
| `200` | Éxito | Resultado normal |
| `201` | Recurso creado | Confirmación de creación |
| `400` | Datos incorrectos o regla de negocio violada | Mensaje del campo `error` en el body |
| `401` | Token expirado o inválido | Redirigir al login |
| `403` | Sin permiso | Ver nota abajo — puede ser por rol o por cuenta |
| `404` | Recurso no encontrado | Mensaje de "no encontrado" |
| `409` | Conflicto de estado | Mensaje del campo `error` en el body |

### Cómo distinguir un 403 por rol vs 403 por cuenta bloqueada

El body del `403` es diferente en cada caso:

```javascript
if (error.response?.status === 403) {
  const body = error.response.data;

  if (typeof body === 'string' && body.includes('bloqueada')) {
    // Cuenta bloqueada permanentemente — cerrar sesión
    // Mensaje: "Tu cuenta ha sido bloqueada permanentemente. Contacta al administrador."
    logout();
    showModal('Tu cuenta ha sido bloqueada. Contacta al administrador.');
    
  } else if (typeof body === 'string' && body.includes('desactivada')) {
    // Cuenta desactivada temporalmente — cerrar sesión
    logout();
    showModal('Tu cuenta ha sido desactivada. Contacta al administrador.');
    
  } else {
    // No tiene permiso para esta acción específica
    showToast('No tienes permiso para realizar esta acción.');
  }
}
```

### Leer el mensaje de error del backend

El backend retorna los errores en este formato:
```json
{ "error": "Mensaje descriptivo del problema" }
```

```javascript
const handleApiError = (error) => {
  const message = error.response?.data?.error || 'Error inesperado. Intenta de nuevo.';
  showToast(message);
};
```

---

## 5. Endpoints disponibles

### 5.1 Usuarios — `/api/users`

> Todos requieren rol **ADMIN**

---

#### Listar todos los usuarios

```
GET /api/users
```

**Respuesta `200 OK`:**
```json
{
  "status": "success",
  "users": [
    {
      "id": "uuid",
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

#### Obtener un usuario por ID

```
GET /api/users/{userId}
```

**Respuesta `200 OK`:** Mismo objeto de usuario.
**Respuesta `404`:** `{ "error": "Usuario no encontrado: {uuid}" }`

---

#### Crear usuario

```
POST /api/users
Content-Type: application/json
```

**Body:**
```json
{
  "email": "nuevo@leydata.cl",
  "name": "Nombre Apellido",
  "roleCode": "DPO",
  "domainIds": [],
  "password": "Clave1234!"
}
```

> `roleCode` acepta: `ADMIN`, `DPO`, `JEFE_DOMINIO`
> `domainIds` solo aplica cuando `roleCode = "JEFE_DOMINIO"`. Para otros roles enviar `[]`.
> `password` es la contraseña inicial del usuario. Solo se envía a Keycloak — nunca se almacena en la BD.
> `name` debe contener nombre y apellido separados por espacio (ej: `"María García"`). El backend usa la primera palabra como `firstName` y el resto como `lastName` en Keycloak. Keycloak 26 requiere ambos campos no vacíos — si se envía una sola palabra, el backend la usa para ambos campos.

**Respuesta `201 Created`:** Objeto del usuario creado.
**Respuesta `400`:** Email duplicado, rol inválido o contraseña no enviada.

> El backend crea el usuario en Keycloak y en la BD en una sola operación. Después de este request el usuario ya puede hacer login.

---

#### Editar usuario

```
PUT /api/users/{userId}
Content-Type: application/json
```

**Body:**
```json
{
  "name": "Nuevo Nombre",
  "roleCodes": ["DPO"],
  "domainIds": []
}
```

> `roleCodes` **reemplaza** todos los roles actuales — no agrega, reemplaza.

**Respuesta `200 OK`:** Usuario actualizado.
**Respuesta `400`:** ADMIN intentando editarse a sí mismo.
**Respuesta `409`:** Usuario bloqueado.

---

#### Bloquear usuario (acción irreversible)

```
POST /api/users/{userId}/block
```

> ⚠️ El bloqueo **no puede deshacerse** desde el sistema. Mostrar confirmación antes de ejecutar.

**Sin body.**
**Respuesta `200 OK`:** Usuario con `blocked: true, active: false`.
**Respuesta `400`:** ADMIN intentando bloquearse a sí mismo.
**Respuesta `409`:** Ya estaba bloqueado.

---

#### Desactivar usuario (temporal)

```
POST /api/users/{userId}/deactivate
```

**Sin body.**
**Respuesta `200 OK`:** Usuario con `active: false, blocked: false`.
**Respuesta `409`:** Ya estaba inactivo.

---

#### Reactivar usuario

```
POST /api/users/{userId}/reactivate
```

**Sin body.**
**Respuesta `200 OK`:** Usuario con `active: true`.
**Respuesta `409`:** Ya estaba activo o está bloqueado permanentemente.

---

### 5.2 Dominios — `/api/domains`

> Todos requieren rol **ADMIN**

---

#### Listar todos los dominios

```
GET /api/domains/all
```

**Respuesta `200 OK`:**
```json
[
  {
    "id": "uuid",
    "code": "RRHH",
    "name": "Recursos Humanos",
    "description": "...",
    "active": true
  }
]
```

---

#### Crear dominio

```
POST /api/domains
Content-Type: application/json
```

**Body:**
```json
{
  "code": "RRHH",
  "name": "Recursos Humanos",
  "description": "Descripción opcional",
  "jefeId": null
}
```

> `code` debe ser único en el sistema.
> `jefeId` es opcional — UUID del usuario con rol `JEFE_DOMINIO`.

**Respuesta `201 Created`:** `{ "status": "success", "domainId": "uuid" }`
**Respuesta `400`:** Código duplicado.

---

#### Desactivar dominio

```
POST /api/domains/{domainId}/deactivate
```

**Sin body.**
**Respuesta `200 OK`.**
**Respuesta `404`:** Dominio no encontrado.
**Respuesta `409`:** Ya estaba desactivado.

---

#### Reactivar dominio

```
POST /api/domains/{domainId}/reactivate
```

**Sin body.**
**Respuesta `200 OK`.**
**Respuesta `409`:** Ya estaba activo.

---

### 5.3 Solicitudes de Propósito — `/api/purpose-requests`

---

#### Crear solicitud

> Acceso: `JEFE_DOMINIO`

```
POST /api/purpose-requests
Content-Type: application/json
```

**Body:**
```json
{
  "title": "Título de la solicitud",
  "justification": "Base legal — ej: Art. 13 Ley 21.719",
  "requestedData": "Qué datos se van a procesar",
  "domainId": "uuid-del-dominio"
}
```

**Respuesta `201 Created`:**
```json
{
  "request": {
    "id": "uuid",
    "title": "...",
    "status": "PENDING",
    "domainId": "uuid",
    "createdAt": "2026-06-08T10:00:00"
  }
}
```
**Respuesta `400`:** Dominio no pertenece al jefe autenticado.
**Respuesta `409`:** Dominio desactivado.

---

#### Ver mis solicitudes (las del jefe autenticado)

> Acceso: `JEFE_DOMINIO`

```
GET /api/purpose-requests/my
```

**Respuesta `200 OK`:** Lista de solicitudes del jefe autenticado (todos los estados).

---

#### Ver solicitudes pendientes

> Acceso: `DPO` o `ADMIN`

```
GET /api/purpose-requests/pending
```

**Respuesta `200 OK`:** Solo solicitudes con `status: "PENDING"`.

---

#### Ver todas las solicitudes

> Acceso: `DPO` o `ADMIN`

```
GET /api/purpose-requests
```

**Respuesta `200 OK`:** Todas las solicitudes (PENDING, APPROVED, REJECTED).

---

#### Revisar una solicitud (aprobar o rechazar)

> Acceso: `DPO` o `ADMIN`

```
PATCH /api/purpose-requests/{requestId}/review
Content-Type: application/json
```

**Body:**
```json
{
  "status": "APPROVED",
  "reviewNotes": "Comentario del DPO"
}
```

> `status` acepta solo: `"APPROVED"` o `"REJECTED"`
> `reviewNotes` es **obligatorio si `status = "REJECTED"`** (exigido por Ley 21.719 Art. 14). Validar en el frontend antes de enviar.

**Respuesta `200 OK`:** Solicitud actualizada.
**Respuesta `400`:** Rechazo sin notas o estado inválido.
**Respuesta `409`:** La solicitud ya fue revisada anteriormente.

---

### 5.4 Auditoría — `/api/audit`

> Todos requieren rol **ADMIN**

> **Integridad garantizada:** Cada log tiene un hash SHA-256 que encadena con el log anterior. La cadena es verificable con `GET /api/audit/logs/verify`. Los logs son inmutables en base de datos (protegidos por triggers de PostgreSQL).

---

#### Consultar logs de auditoría

```
GET /api/audit/logs?page=0&size=20&action=&table=&actorEmail=
```

**Parámetros opcionales:**

| Parámetro | Descripción |
|-----------|-------------|
| `page` | Número de página (default: 0) |
| `size` | Resultados por página (default: 20) |
| `action` | Filtrar por acción (ej: `BLOQUEAR_USUARIO`) |
| `table` | Filtrar por tabla (`users`, `domains`, `purpose_requests`) |
| `actorEmail` | Filtrar por quien ejecutó la acción |

**Respuesta `200 OK`:**
```json
{
  "logs": [
    {
      "id": "uuid",
      "action": "CREAR_USUARIO",
      "tableName": "users",
      "actorEmail": "admin@leydata.cl",
      "actorRole": "ADMIN",
      "oldData": null,
      "newData": "{\"email\":\"dpo@leydata.cl\",...}",
      "ipAddress": "127.0.0.1",
      "logHash": "sha256hex",
      "createdAt": "2026-06-08T10:00:00"
    }
  ],
  "total": 10,
  "page": 0,
  "totalPages": 1
}
```

---

#### Verificar integridad del log

```
GET /api/audit/logs/verify
```

**Respuesta `200 OK`:**
```json
{
  "valid": true,
  "message": "Cadena de auditoría íntegra. 10 registros verificados."
}
```

---

## 6. Roles y control de acceso por pantalla

Usar el contenido del JWT para saber qué pantallas mostrar. El rol está en el claim `realm_access.roles`.

**Cómo leer el rol desde el token:**

```javascript
function getRolesFromToken(token) {
  // El JWT tiene 3 partes separadas por punto
  const payload = JSON.parse(atob(token.split('.')[1]));
  return payload.realm_access?.roles || [];
}

const roles = getRolesFromToken(localStorage.getItem('access_token'));
const isAdmin = roles.includes('ADMIN');
const isDPO = roles.includes('DPO');
const isJefeDominio = roles.includes('JEFE_DOMINIO');
```

**Qué mostrar según el rol:**

| Pantalla / Acción | ADMIN | DPO | JEFE_DOMINIO |
|-------------------|-------|-----|--------------|
| Gestión de usuarios | ✅ | ❌ | ❌ |
| Gestión de dominios | ✅ | ❌ | ❌ |
| Ver logs de auditoría | ✅ | ❌ | ❌ |
| Crear solicitud de propósito | ❌ | ❌ | ✅ |
| Ver mis solicitudes | ❌ | ❌ | ✅ |
| Revisar solicitudes pendientes | ❌ | ✅ | ❌ |
| Ver historial de solicitudes | ❌ | ✅ | ❌ |

> **Nota:** Aunque el frontend oculte pantallas por rol, el backend también lo verifica. Si el frontend hace un request a un endpoint sin permiso, recibirá `403` igual.

---

## 7. Swagger UI — documentación interactiva

El backend expone automáticamente una interfaz para explorar y probar todos los endpoints sin necesidad de Postman.

### Cómo acceder

Con el backend corriendo: `http://localhost:8080/swagger-ui.html`

### Cómo autenticarse en Swagger UI

1. Obtener un token de Keycloak (igual que en Postman)
2. Click en el botón **Authorize** (candado 🔒) arriba a la derecha
3. En el campo **Value** escribir: `Bearer <token>`
4. Click **Authorize** → cerrar el modal
5. Todos los endpoints quedan autenticados para esa sesión

### Importar en Postman desde Swagger

En lugar de crear los requests a mano:
1. Postman → **Import**
2. Pegar la URL: `http://localhost:8080/v3/api-docs`
3. Postman genera toda la colección automáticamente con los bodies correctos

### Descargar el spec como archivo

```bash
curl http://localhost:8080/v3/api-docs -o leydata-api.json
```

---

## 8. Notas de implementación

### CORS

El backend acepta requests desde:
- `http://localhost:5173` (Vite por defecto)
- `http://localhost:3000` (CRA / otros)

Si el frontend corre en otro puerto, pedirle al equipo backend que agregue ese origen en `SecurityConfig.java`.

### El token dura 5 minutos

Implementar renovación automática con el `refresh_token` (dura 30 minutos). Ver la sección 2 para el código del interceptor de Axios.

### Estado de la cuenta del usuario

Un usuario puede tener token válido de Keycloak y aun así recibir `403` del backend si:
- `active = false` (fue desactivado por el admin)
- `blocked = true` (fue bloqueado permanentemente)

En ese caso el body del 403 es un texto plano con el motivo. Ver sección 4 para cómo distinguirlo.

### Bloqueo — acción irreversible

El endpoint `POST /api/users/{id}/block` es irreversible. Agregar en el frontend una confirmación explícita antes de ejecutarlo, por ejemplo:

```
"¿Estás seguro? Esta acción bloqueará permanentemente la cuenta 
de [nombre de usuario] y no podrá deshacerse."
```

### Campo reviewNotes en rechazo de solicitudes

Antes de llamar a `PATCH /api/purpose-requests/{id}/review` con `status = "REJECTED"`, validar en el frontend que `reviewNotes` no esté vacío. El backend lo rechazará con `400` si está vacío, pero es mejor darle feedback al DPO antes de que envíe.

### Paginación en auditoría

El endpoint `GET /api/audit/logs` retorna los campos `total`, `page` y `totalPages`. Úsarlos para implementar paginación en la tabla de auditoría.

### Roles en el JWT — filtrar solo los de negocio

El JWT de Keycloak incluye roles técnicos propios de Keycloak (`offline_access`, `uma_authorization`, `default-roles-leydata`) junto con los roles de negocio. Al leer el rol desde el frontend, filtrar solo los que corresponden al sistema:

```javascript
const BUSINESS_ROLES = ['ADMIN', 'DPO', 'JEFE_DOMINIO', 'USER', 'TITULAR'];

function getBusinessRole(token) {
  const payload = JSON.parse(atob(token.split('.')[1]));
  const roles = payload.realm_access?.roles || [];
  return roles.find(r => BUSINESS_ROLES.includes(r)) || null;
}
```

### Creación de usuarios — dos sistemas en sincronía

`POST /api/users` crea el usuario en Keycloak y en la BD local de forma atómica. Si Keycloak falla, el usuario no queda en la BD. Si la BD falla después de crearlo en KC, el backend elimina el usuario de Keycloak para no dejar inconsistencias.

Después de un `201 Created` el usuario puede hacer login inmediatamente sin ningún paso adicional.
