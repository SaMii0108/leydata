# Módulo: Usuarios (`user/`)

**Paquete:** `com.leydata.backend.user`  
**Endpoints base:** `/api/users/**`  
**Acceso:** Solo `ADMIN`

---

## Responsabilidad

Gestión del ciclo de vida de usuarios internos del sistema (personal de la organización). El módulo es una capa delgada sobre la **API de administración de Keycloak** — la fuente de verdad para identidad, contraseñas y roles es Keycloak, no la base de datos local.

La tabla `users` local almacena únicamente el `keycloak_id` y metadatos de negocio (dominio asignado, estado activo). No guarda contraseñas, roles ni `blocked`.

---

## Modelo Keycloak-first

| Dato | Dónde vive |
|---|---|
| Email, nombre, contraseña | Keycloak |
| Roles (ADMIN, DPO, JEFE_DOMINIO, USER) | Keycloak (`realm_access.roles`) |
| UUID local, `createdAt`, `active` | BD local (`users`) |
| Dominios asignados | BD local (`user_domains` vía `userdomain/`) |
| Bloqueo permanente | BD local (`user_status` vía `userstatus/`) |

---

## Endpoints

### `POST /api/users` — Crear usuario
Crea el usuario simultáneamente en Keycloak y en la BD local. Si Keycloak falla, no se crea en BD. Si la BD falla después de Keycloak, se hace rollback eliminando el usuario de Keycloak (compensación manual en `KeycloakAdminService.deleteUser()`).

**Regla:** un `JEFE_DOMINIO` solo puede tener **un** dominio asignado a la vez. `domainIds` sigue siendo un array por compatibilidad con el contrato existente, pero `UserService` rechaza con 400 si se envían 2 o más. Asignar un nuevo dominio a un jefe que ya administra otro requiere primero liberarlo del anterior (`domainIds: []`) o reemplazar la asignación completa.

```json
// Request
{
  "email": "juan@empresa.cl",
  "name": "Juan Pérez",
  "role": "JEFE_DOMINIO",
  "domainIds": ["uuid-dominio-1"]
}

// Response 201
{
  "id": "uuid-local",
  "keycloakId": "uuid-keycloak",
  "email": "juan@empresa.cl",
  "name": "Juan Pérez",
  "role": "JEFE_DOMINIO",
  "active": true,
  "createdAt": "2026-06-29T10:00:00"
}
```

### `GET /api/users` — Listar usuarios
Combina datos de Keycloak (nombre, email, roles) con datos locales (dominios, estado). La lista viene de Keycloak y se enriquece con la BD local.

### `GET /api/users/{id}` — Obtener usuario por ID (UUID local)

### `PUT /api/users/{id}` — Actualizar usuario
Actualiza nombre y dominos en BD local + roles en Keycloak. El email no es modificable (es el username en Keycloak).

### `PATCH /api/users/{id}/block` — Bloquear usuario
1. Deshabilita el usuario en Keycloak (invalida login y refresh de tokens)
2. Escribe `blocked=true` en `user_status` con timestamp
3. Escribe `"true"` en Redis `user:{keycloakId}:blocked` (sin TTL — permanente hasta desbloqueo)

El `UserStatusFilter` rechaza con 403 cualquier request del usuario bloqueado aunque su JWT aún no haya expirado.

### `PATCH /api/users/{id}/unblock` — Desbloquear usuario
1. Habilita el usuario en Keycloak
2. Actualiza `user_status` → `blocked=false`
3. Elimina la key de Redis (el filtro volverá a consultar BD en el próximo request)

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `user/web/UserController.java` | Endpoints REST |
| `user/application/service/UserService.java` | Lógica: coordina BD local + Keycloak |
| `user/infrastructure/persistence/UsersRepository.java` | JPA sobre tabla `users` |
| `security/KeycloakAdminService.java` | Llamadas a la API admin de Keycloak |
| `security/UserStatusFilter.java` | Filtro de bloqueo (corre después del JWT) |
| `userdomain/` | Asociación usuario ↔ dominio |
| `userstatus/` | Flag de bloqueo con timestamp |

---

## Roles disponibles

| Rol | Descripción |
|---|---|
| `ADMIN` | Acceso total al sistema |
| `DPO` | Datos Protection Officer — aprueba solicitudes, gestiona documentos |
| `JEFE_DOMINIO` | Jefe de un dominio organizacional — crea solicitudes de propósito |
| `USER` | Usuario estándar (acceso limitado) |
| `TITULAR` | Titular de datos (ciudadano/cliente) — acceso mínimo |
