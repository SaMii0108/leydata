# Colección Bruno — Ley Data API

## Requisitos

- `docker-compose up -d` corriendo (Keycloak `:8180`, PostgreSQL `:5433`)
- Backend corriendo: `cd backend && export $(cat ../.env | xargs) && ./mvnw spring-boot:run`

---

## Setup inicial (una sola vez)

### 1. Abrir la colección

Bruno → **Open Collection** → seleccionar la carpeta **`bruno/`**.

### 2. Activar el environment

Selector esquina superior derecha → elegir **`local`**.

> El environment no se importa — Bruno lo detecta solo al abrir la carpeta `bruno/`.  
> Si dice "No Environment", las URLs no funcionan.

### 3. Completar las variables

Clic en el **ícono ✏️** junto al selector `local` → completar:

| Variable | Qué poner |
|---|---|
| `email` | `admin@leydata.cl` (o el usuario que tengas) |
| `password` | Tu contraseña de Keycloak |
| `kcBackendSecret` | El valor de `KC_BACKEND_SECRET` en el archivo `.env` de la raíz del proyecto |

Clic en **Save**.

### 4. Ejecutar login

Abrir **`Keycloak / 01 Obtener Token`** → clic en **▶ Run**.

El token se guarda automáticamente en `{{token}}`. Ya puedes usar cualquier endpoint del backend.

### 5. Ejecutar login admin (solo si vas a usar los endpoints 04, 05 o 06)

Abrir **`Keycloak / 03 Obtener Token Admin`** → clic en **▶ Run**.

El token se guarda automáticamente en `{{adminToken}}`.

---

## Diferencia entre `01 Obtener Token` y `03 Obtener Token Admin`

| | `01 Obtener Token` | `03 Obtener Token Admin` |
|---|---|---|
| Quién se autentica | Un usuario (persona) con email y contraseña | La aplicación backend con client_id y secret |
| Grant type | `password` | `client_credentials` |
| Para qué sirve | Operar la API del backend (`/api/*`) | Llamar a la Keycloak Admin API directamente |
| Guarda en | `{{token}}` | `{{adminToken}}` |
| Cuándo usarlo | Siempre — para cualquier endpoint normal | Solo para los endpoints `04`, `05` y `06` |

En la práctica casi nunca necesitas el `03`. Solo si quieres inspeccionar usuarios directamente en Keycloak sin pasar por el backend.

---

## Uso diario

- Los tokens duran **5 minutos**. Cuando un endpoint devuelva `401`, volver a ejecutar `01` (y `03` si usas admin).
- Todos los endpoints del backend ya tienen `{{token}}` configurado — no hay que tocar nada más.
- Los endpoints `04`, `05`, `06` de Keycloak ya tienen `{{adminToken}}` configurado.
- Los endpoints con `<keycloak_id_aqui>` o `<uuid-aqui>` en la URL: reemplazar ese texto con el UUID real.

---

## Estructura

```
Keycloak/
  01 Obtener Token            → login usuario  → guarda {{token}} automáticamente
  02 Renovar Token            → renovar con refresh_token sin volver a hacer login
  03 Obtener Token Admin      → login admin    → guarda {{adminToken}} automáticamente
  04 Listar Usuarios Realm    → lista usuarios en Keycloak
  05 Ver Roles de un Usuario  → roles de un usuario en Keycloak
  06 Habilitar/Deshabilitar   → activar o desactivar usuario en Keycloak

Usuarios/         → CRUD operadores          [rol: ADMIN]
Dominios/         → áreas organizacionales   [rol: ADMIN]
Solicitudes/      → workflow de aprobación   [rol: JEFE_DOMINIO / DPO]
Finalidades/      → tratamiento de datos     [rol: DPO]
Bases de Licitud/ → catálogo Ley 21.719      [lectura libre]
Categorias Datos/ → tipos de datos           [rol: DPO]
Retencion/        → finalidad ↔ categoría    [rol: DPO]
Documentos/       → documentos de privacidad [rol: DPO]
Auditoria/        → log inmutable            [rol: ADMIN]
Notificaciones/   → notificaciones in-app    [usuario autenticado]
```
