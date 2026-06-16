# Flujo de Autenticación y Gestión de Sesión — Ley Data

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)  
**Fecha:** 2026-06-15  

---

## Índice

1. [Visión general](#1-visión-general)
2. [Portales y sus mecanismos de auth](#2-portales-y-sus-mecanismos-de-auth)
3. [Flujo del portal operativo (Keycloak)](#3-flujo-del-portal-operativo-keycloak)
4. [Flujo del portal titular (mock)](#4-flujo-del-portal-titular-mock)
5. [AuthContext — API y ciclo de vida](#5-authcontext--api-y-ciclo-de-vida)
6. [JWT de Keycloak — estructura y parseo](#6-jwt-de-keycloak--estructura-y-parseo)
7. [Auto-refresh del access token](#7-auto-refresh-del-access-token)
8. [Rutas protegidas — ProtectedRoute](#8-rutas-protegidas--protectedroute)
9. [Sistema de permisos — usePermissions](#9-sistema-de-permisos--usepermissions)
10. [Roles y acceso por pantalla](#10-roles-y-acceso-por-pantalla)
11. [Manejo de errores de autenticación](#11-manejo-de-errores-de-autenticación)
12. [Estado de la sesión](#12-estado-de-la-sesión)

---

## 1. Visión general

El sistema de autenticación de Ley Data tiene dos mecanismos diferentes que coexisten:

| | Portal Operativo | Portal Titular |
|---|---|---|
| **Ruta de login** | `/login` | `/titular/login` |
| **Mecanismo** | Keycloak 26 (real) | Mock en memoria |
| **Roles** | ADMIN, DPO, JEFE_DOMINIO | TITULAR |
| **Token** | JWT firmado por Keycloak | No aplica |
| **Expiración** | access: 5 min / refresh: 30 min | Dura la sesión del browser |
| **Estado** | Integrado | Pendiente integración |

---

## 2. Portales y sus mecanismos de auth

```
                   USUARIO
                      │
          ┌───────────┴───────────┐
          │                       │
   Es operador                Es titular
   (ADMIN/DPO/JEFE)            de datos
          │                       │
     /login                 /titular/login
          │                       │
     LoginPage              TitularLoginPage
          │                       │
     Keycloak               findUser() en
     real auth              MOCK_USERS array
          │                       │
     login(user, tokens)    login(user)
          │                       │
     AuthContext            AuthContext
     (user + token)         (user solo)
          │                       │
     DashboardLayout        TitularLayout
```

---

## 3. Flujo del portal operativo (Keycloak)

### Diagrama de secuencia

```
Usuario          LoginPage              Keycloak                 Backend API
   │                 │                     │                          │
   │  ingresa        │                     │                          │
   │  email+pass     │                     │                          │
   │────────────────►│                     │                          │
   │                 │  POST /token        │                          │
   │                 │  grant_type=password│                          │
   │                 │  client_id=leydata-frontend                    │
   │                 │  username=email     │                          │
   │                 │  password=pass      │                          │
   │                 │────────────────────►│                          │
   │                 │                     │ valida credenciales      │
   │                 │                     │ en su base de datos      │
   │                 │    200 OK           │                          │
   │                 │◄────────────────────│                          │
   │                 │  { access_token,    │                          │
   │                 │    refresh_token,   │                          │
   │                 │    expires_in: 300 }│                          │
   │                 │                     │                          │
   │                 │ parseKeycloakToken()│                          │
   │                 │ extrae user del JWT │                          │
   │                 │                     │                          │
   │                 │ login(user, tokens) │                          │
   │                 │ → AuthContext       │                          │
   │                 │                     │                          │
   │ redirige a /    │                     │                          │
   │◄────────────────│                     │                          │
   │                 │                     │                          │
   │ (más tarde, en cualquier request al backend)                     │
   │                 │                     │  GET /api/users          │
   │                 │                     │  Authorization: Bearer   │
   │                 │                     │  <access_token>          │
   │                 │                     │─────────────────────────►│
   │                 │                     │                          │ valida JWT
   │                 │                     │                          │ extrae rol
   │                 │                     │         200 OK           │
   │                 │                     │◄─────────────────────────│
```

### Endpoint de login

```
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=leydata-frontend
username=<email>
password=<contraseña>
```

### Credenciales de prueba (entorno de desarrollo)

| Rol | Email | Contraseña |
|---|---|---|
| ADMIN | `admin@leydata.cl` | `Admin1234!` |

Los usuarios DPO y JEFE_DOMINIO se crean desde `POST /api/users` una vez autenticado como ADMIN. Ver `GUIA-CONSUMO-API.md` del backend.

### Respuesta exitosa de Keycloak

```json
{
  "access_token":  "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in":    300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_expires_in": 1800,
  "token_type":    "Bearer",
  "scope":         "profile email"
}
```

---

## 4. Flujo del portal titular (mock)

El portal titular usa autenticación mock mientras el backend de titulares no está integrado.

```
Usuario          TitularLoginPage           MOCK_USERS array
   │                    │                          │
   │  ingresa           │                          │
   │  email+pass        │                          │
   │───────────────────►│                          │
   │                    │  findUser(email, pass)   │
   │                    │─────────────────────────►│
   │                    │  busca en array en memoria
   │                    │◄─────────────────────────│
   │                    │  AppUser | undefined      │
   │                    │                          │
   │                    │  si no encontrado:        │
   │                    │  devuelve error           │
   │                    │                          │
   │                    │  si encontrado y TITULAR: │
   │                    │  login(user)              │
   │                    │  → AuthContext            │
   │                    │                          │
   │ redirige a         │                          │
   │ /titular/mis-consentimientos                  │
   │◄───────────────────│                          │
```

**Usuarios mock disponibles:**

| Nombre | Email | Contraseña |
|---|---|---|
| María José Fuentes | `mjfuentes@email.com` | `ley2024` |
| Carlos Andrés Pérez | `caperez@email.com` | `ley2024` |
| Valentina Rojas | `vrojas@email.com` | `ley2024` |

---

## 5. AuthContext — API y ciclo de vida

### Ubicación

`src/features/auth/AuthContext.tsx`

### Interfaz pública

```typescript
interface AuthContextValue {
  user:        AppUser | null;
  accessToken: string | null;
  login:  (user: AppUser, tokens?: { access: string; refresh: string }) => void;
  logout: () => void;
}
```

### Uso desde componentes

```tsx
import { useAuth } from '../features/auth/AuthContext';

const MiComponente = () => {
  const { user, accessToken, login, logout } = useAuth();

  // Acceder al usuario actual
  console.log(user?.name);  // "Ana Torres"
  console.log(user?.role);  // "ADMIN"
  console.log(user?.email); // "admin@leydata.cl"

  // Usar el token en una llamada al backend
  const res = await fetch('/api/users', {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
};
```

### Ciclo de vida de la sesión

```
App arranca
    │
    ▼
AuthProvider monta
user = null, accessToken = null
    │
    ▼ (usuario hace login)
    │
    ├── Login operativo:
    │   login(user, { access: '...', refresh: '...' })
    │   → user = AppUser desde JWT
    │   → accessToken = JWT
    │   → refreshToken (interno, no expuesto)
    │   → setInterval cada 4.5 min → auto-refresh
    │
    └── Login titular (mock):
        login(user)
        → user = AppUser del array MOCK_USERS
        → accessToken = null (no hay token)
        → sin auto-refresh
    │
    ▼ (usuario usa la app)
    │
    │   Rutas protegidas verifican user !== null y role
    │   Componentes leen user con useAuth()
    │
    ▼ (usuario cierra sesión o token expirado sin refresh)
    │
logout()
user = null, accessToken = null
    │
    ▼
App.tsx → ProtectedRoute → redirige a /login
```

### Tipo AppUser

```typescript
// src/features/auth/mockUsers.ts
export type Role = 'ADMIN' | 'DPO' | 'JEFE_DOMINIO' | 'TITULAR';

export interface AppUser {
  id:       string;
  name:     string;
  email:    string;
  password?: string;   // solo para usuarios mock (titulares)
  role:     Role;
  area:     string | null;  // área funcional (JEFE_DOMINIO) o null
}
```

---

## 6. JWT de Keycloak — estructura y parseo

### Estructura del payload del JWT

```json
{
  "sub":                "uuid-del-usuario",
  "name":               "Ana Torres",
  "preferred_username": "admin@leydata.cl",
  "email":              "admin@leydata.cl",
  "realm_access": {
    "roles": [
      "ADMIN",
      "offline_access",
      "uma_authorization",
      "default-roles-leydata"
    ]
  },
  "exp": 1749000000,
  "iat": 1748999700
}
```

### Función de parseo

```typescript
// src/features/auth/AuthContext.tsx
const BUSINESS_ROLES: Role[] = ['ADMIN', 'DPO', 'JEFE_DOMINIO', 'TITULAR'];

export function parseKeycloakToken(accessToken: string): AppUser | null {
  try {
    const payload = JSON.parse(atob(accessToken.split('.')[1]));
    const roles   = payload.realm_access?.roles ?? [];

    // Filtrar solo roles de negocio (ignorar roles técnicos de Keycloak)
    const role = roles.find((r) => BUSINESS_ROLES.includes(r)) as Role | undefined;
    if (!role) return null;

    return {
      id:    payload.sub,
      name:  payload.name ?? payload.preferred_username ?? 'Usuario',
      email: payload.email ?? '',
      role,
      area:  null,  // no viene en el JWT, se obtiene del backend cuando esté disponible
    };
  } catch {
    return null;
  }
}
```

**Roles técnicos que se ignoran:** `offline_access`, `uma_authorization`, `default-roles-leydata`. Solo se considera el primer rol de negocio encontrado.

---

## 7. Auto-refresh del access token

El access token de Keycloak expira a los **5 minutos**. Para que el usuario no sea expulsado de la sesión durante el uso normal de la app, el frontend renueva el token automáticamente cada **4 minutos y 30 segundos** usando el refresh token.

### Implementación

```typescript
// src/features/auth/AuthContext.tsx
const REFRESH_MS = 4.5 * 60 * 1000; // 4 min 30 s

useEffect(() => {
  if (!refreshToken) return;  // solo activo si hay refresh token (login Keycloak)

  const refresh = async () => {
    const params = new URLSearchParams({
      grant_type:    'refresh_token',
      client_id:     'leydata-frontend',
      refresh_token: refreshToken,
    });

    const res = await fetch(`${KEYCLOAK_URL}/protocol/openid-connect/token`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body:    params.toString(),
    });

    if (!res.ok) {
      logout();  // refresh token expirado (30 min) → sesión cerrada
      return;
    }

    const data = await res.json();
    setAccessToken(data.access_token);
    setRefreshToken(data.refresh_token);
  };

  const id = setInterval(refresh, REFRESH_MS);
  return () => clearInterval(id);   // limpieza al desmontar o al cambiar refreshToken
}, [refreshToken, logout]);
```

### Ciclo de vida del refresh

```
Login exitoso
    │
    ▼
setInterval cada 4.5 min
    │
    ▼
¿refresh_token válido?
    │
    ├── Sí → Keycloak devuelve nuevo access_token y refresh_token
    │         → actualiza estado → usuario sigue sin interrupciones
    │
    └── No (expiró los 30 min o Keycloak caído)
          → logout() → usuario redirigido a /login
```

**El refresh token expira a los 30 minutos.** Si el usuario no interactúa con la app durante ese período, su sesión expira y debe volver a hacer login.

---

## 8. Rutas protegidas — ProtectedRoute

### Ubicación

`src/features/shared/ProtectedRoute.tsx`

### Props

```typescript
interface Props {
  roles?:    Role[];     // roles permitidos; si se omite, solo requiere estar autenticado
  fallback?: string;     // ruta de redirección si el rol no tiene acceso
                         // default: '/consentimientos'
  children:  ReactNode;
}
```

### Lógica de decisión

```
ProtectedRoute renderiza
    │
    ├── ¿user === null?
    │   Sí → <Navigate to="/login" replace />
    │
    ├── ¿roles[] definido Y user.role no está en roles[]?
    │   Sí → <Navigate to={fallback} replace />
    │
    └── user existe y tiene rol permitido
        → renderiza children
```

### Ejemplo de uso

```tsx
// Ruta solo para ADMIN
<ProtectedRoute roles={['ADMIN']}>
  <UsuariosPage />
</ProtectedRoute>

// Ruta para DPO y JEFE_DOMINIO, con fallback al dashboard
<ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']} fallback="/">
  <ConsentimientosPage />
</ProtectedRoute>
```

---

## 9. Sistema de permisos — usePermissions

### Ubicación

`src/features/auth/usePermissions.ts`

### Permisos disponibles

```typescript
const {
  canCreate,         // puede crear consentimientos → DPO
  canViewAll,        // puede ver todos los consentimientos → DPO
  canViewDetail,     // puede ver detalle de un consentimiento → DPO
  canRevoke,         // puede revocar consentimientos → TITULAR
  canManageUsers,    // puede gestionar usuarios → ADMIN
  canViewMetrics,    // puede ver el dashboard → ADMIN, DPO
  canViewAudit,      // puede ver auditoría → ADMIN, DPO
  canViewCompliance, // puede ver cumplimiento legal → ADMIN, DPO
  canViewTemplates,  // puede gestionar plantillas → DPO
  canUpdateProfile,  // puede editar su perfil → todos los roles autenticados
  isTitular,         // es titular de datos → TITULAR
  userArea,          // área del usuario (JEFE_DOMINIO) o null
  role,              // rol actual del usuario o null si no autenticado
} = usePermissions();
```

### Uso típico

```tsx
import { usePermissions } from '../features/auth/usePermissions';

const ConsentimientosPage = () => {
  const { canCreate, canViewAll, userArea } = usePermissions();

  // JEFE_DOMINIO solo ve consentimientos de su área
  // DPO ve todos
  const registros = canViewAll
    ? consentRecords
    : consentRecords.filter(r => r.area === userArea);

  return (
    <>
      {canCreate && <Button onClick={handleNuevo}>Nuevo</Button>}
      <TablaConsentimientos data={registros} />
    </>
  );
};
```

---

## 10. Roles y acceso por pantalla

| Pantalla | ADMIN | DPO | JEFE_DOMINIO | TITULAR |
|---|:---:|:---:|:---:|:---:|
| Dashboard (métricas) | ✅ | ✅ | — | — |
| Consentimientos | — | ✅ | ✅ (solo su área) | — |
| Nuevo consentimiento | — | ✅ | — | — |
| Usuarios | ✅ | — | — | — |
| Auditoría | ✅ | ✅ | — | — |
| Cumplimiento legal | ✅ | ✅ | — | — |
| Plantillas | — | ✅ | — | — |
| Mi Perfil | ✅ | ✅ | ✅ | ✅ |
| Portal titular | — | — | — | ✅ |

**Nota:** Los roles que no tienen acceso a una ruta son redirigidos automáticamente por `ProtectedRoute` al `fallback` configurado o a `/consentimientos`.

---

## 11. Manejo de errores de autenticación

### Errores en el login

| Situación | Código HTTP | Mensaje mostrado |
|---|---|---|
| Email o contraseña incorrectos | 401 | "Correo o contraseña incorrectos." |
| Token sin rol de negocio reconocido | — | "No se pudo identificar el rol del usuario." |
| Usuario con rol TITULAR intenta login operativo | — | "Los titulares deben ingresar desde el portal de titulares." |
| Keycloak no disponible / error de red | — | "Error de conexión. Verifica que el servidor esté disponible." |

### Errores durante el uso de la app

| Situación | Comportamiento |
|---|---|
| Access token expirado, refresh falla | `logout()` → redirige a `/login` |
| Refresh token expirado (30 min inactivo) | `logout()` → redirige a `/login` |
| Usuario accede a ruta sin permisos | `ProtectedRoute` redirige a `fallback` |
| Backend responde 401 | Pendiente de implementar interceptor |
| Backend responde 403 (cuenta bloqueada) | Pendiente de implementar |

### Distinción entre 403 por rol y 403 por cuenta bloqueada

Según la documentación del backend, el body del 403 es texto plano diferente en cada caso:

```javascript
if (status === 403) {
  const body = await response.text();
  if (body.includes('bloqueada')) {
    // cuenta bloqueada permanentemente → logout + mensaje
  } else if (body.includes('desactivada')) {
    // cuenta desactivada temporalmente → logout + mensaje
  } else {
    // sin permiso para esa acción específica → toast informativo
  }
}
```

Esta lógica está pendiente de implementación en el cliente HTTP del frontend.

---

## 12. Estado de la sesión

La sesión se almacena **únicamente en memoria** (React state). Esto significa:

- **Ventaja:** No hay datos sensibles en `localStorage` ni `sessionStorage`.
- **Desventaja:** Al refrescar el browser la sesión se pierde y el usuario debe volver a hacer login.

Este comportamiento es intencional para la fase actual de desarrollo. Si en el futuro se requiere persistencia de sesión, se puede implementar guardando el `access_token` en `sessionStorage` e hidratando el `AuthContext` en el montaje del `AuthProvider`, validando que el token no haya expirado comparando el campo `exp` del JWT con `Date.now()`.

### Flujo al refrescar el browser (comportamiento actual)

```
Usuario refresca F5
    │
    ▼
React remonta desde cero
AuthContext → user = null, accessToken = null
    │
    ▼
ProtectedRoute → user === null → redirige a /login
    │
    ▼
Usuario debe hacer login nuevamente
```
