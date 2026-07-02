# Postman — Ley Data API

Colección Postman v2.1 con todos los endpoints del backend organizados por módulo.

---

## Archivos

| Archivo | Descripción |
|---|---|
| `leydata-api.postman_collection.json` | Colección completa con endpoints y script de auto-guardado de token |
| `leydata-local.postman_environment.json` | Variables de entorno para desarrollo local |

---

## Requisitos

- [Postman](https://www.postman.com/downloads/) desktop app instalado
- Docker Compose corriendo: `docker-compose up -d` (PostgreSQL en `:5433`, Keycloak en `:8180`)
- Backend corriendo en `:8080` (las variables salen del `.env` en la raíz del proyecto):
  ```bash
  cd backend
  export $(cat ../.env | xargs) && ./mvnw spring-boot:run
  ```

---

## Guía de configuración inicial

### Paso 1 — Importar la colección y el entorno

1. Abrir Postman
2. Clic en **Import** (botón arriba a la izquierda)
3. Seleccionar **ambos archivos JSON** de esta carpeta:
   - `leydata-api.postman_collection.json`
   - `leydata-local.postman_environment.json`
4. Postman los importa automáticamente: la colección aparece en el panel izquierdo bajo **Collections**, y el entorno queda disponible en **Environments**

---

### Paso 2 — Activar el entorno

En la **esquina superior derecha** de Postman hay un selector de entornos.

- Por defecto dice **"No Environment"** → las variables como `{{baseUrl}}` no se resuelven y las requests fallarán
- Cambiar a **"Ley Data — Local"**

---

### Paso 3 — Completar las credenciales

1. Ir a **Environments** en el panel izquierdo (ícono de engranaje o la sección Environments)
2. Seleccionar **"Ley Data — Local"**
3. Completar la columna **Current Value** (nunca la de Initial Value) para las siguientes variables:

| Variable | Current Value | Descripción |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Ya viene configurado — no cambiar |
| `keycloakUrl` | `http://localhost:8180` | Ya viene configurado — no cambiar |
| `realm` | `leydata` | Ya viene configurado — no cambiar |
| `email` | Tu email de Keycloak (ej: `admin@leydata.cl`) | Usuario en el realm `leydata` |
| `password` | Tu contraseña de Keycloak | Contraseña del usuario |
| `token` | Dejar vacío | Se llena automáticamente en el Paso 4 |

4. Clic en **Save** (ícono de disco o Ctrl+S)

> **Por qué Current Value y no Initial Value:**  
> El `Initial Value` se sincroniza con el workspace de Postman y puede subirse a la nube.  
> El `Current Value` es **local a tu máquina** y nunca se sincroniza ni se guarda en el repositorio.  
> El archivo `leydata-local.postman_environment.json` en el repo tiene todos los `value` vacíos intencionalmente.

---

### Paso 4 — Obtener el token de acceso

1. En el panel izquierdo, expandir la colección **"Ley Data API"**
2. Abrir la carpeta **Keycloak** → seleccionar **"Obtener Token"**
3. Clic en **Send**
4. La request hace POST a Keycloak con tus credenciales (`{{email}}` y `{{password}}`)
5. El script de test incluido en la request **guarda el token automáticamente** en `{{token}}`

No hay que copiar ni pegar nada — todos los demás endpoints ya tienen configurado `Authorization: Bearer {{token}}` y usarán el token guardado.

Para verificar que funcionó: en **Environments → Ley Data — Local**, la variable `token` ahora debe tener un valor largo en `Current Value`.

---

### Paso 5 — Renovar el token cuando expira

El `access_token` dura **5 minutos**. Cuando un endpoint devuelva `401 Unauthorized`:

**Opción A — Renovar con refresh_token** (sin volver a escribir credenciales):
1. Abrir **Keycloak → "Renovar Token"**
2. En el body del request hay un campo `refresh_token` — reemplazar el placeholder con el `refresh_token` de la respuesta del Paso 4  
   *(o guardar el `refresh_token` como variable de entorno para mayor comodidad)*
3. Clic en **Send** → el script actualiza `{{token}}` automáticamente

**Opción B — Volver a hacer login**:
1. Repetir el Paso 4 completo

---

## Diferencia entre `Obtener Token` y `Token Admin (Service Account)`

| | `Obtener Token` | `Token Admin (Service Account)` |
|---|---|---|
| Quién se autentica | Un usuario (persona) con email y contraseña | La aplicación backend con client_id y secret |
| Grant type | `password` | `client_credentials` |
| Para qué sirve | Operar la API del backend (`/api/*`) | Llamar a la Keycloak Admin API directamente |
| Guarda en | `{{token}}` automáticamente | Copiar manualmente donde se necesite |
| Cuándo usarlo | Siempre — para cualquier endpoint normal | Solo para gestión directa del realm en Keycloak |

En la práctica casi nunca necesitas el `Token Admin`. Solo si quieres inspeccionar usuarios directamente en Keycloak sin pasar por el backend (Listar Usuarios Realm, Ver Roles, Habilitar/Deshabilitar).

---

## Variables de entorno

| Variable | Valor por defecto | Tipo | Descripción |
|---|---|---|---|
| `baseUrl` | `http://localhost:8080` | pública | URL del backend Spring Boot |
| `keycloakUrl` | `http://localhost:8180` | pública | URL del servidor Keycloak |
| `realm` | `leydata` | pública | Realm de Keycloak |
| `email` | *(vacío — completar)* | secret | Email para autenticación |
| `password` | *(vacío — completar)* | secret | Contraseña para autenticación |
| `token` | *(vacío — se llena auto)* | secret | Bearer token JWT activo |

---

## Estructura de la colección

```
Ley Data API
├── Keycloak                              → Autenticación y gestión del realm
│   ├── Obtener Token                     → POST login → guarda {{token}} automáticamente
│   ├── Renovar Token                     → POST con refresh_token → actualiza {{token}}
│   ├── Token Admin (Service Account)     → Para llamadas a Keycloak Admin API
│   ├── Listar Usuarios del Realm         → GET usuarios en Keycloak [Token Admin]
│   ├── Ver Roles de un Usuario           → GET roles de un usuario [Token Admin]
│   └── Habilitar / Deshabilitar Usuario  → PUT toggle activo en Keycloak [Token Admin]
│
├── Usuarios                              → CRUD operadores del sistema [rol: ADMIN]
│   ├── Listar Usuarios
│   ├── Obtener Usuario por ID
│   ├── Crear Usuario
│   ├── Editar Usuario
│   ├── Bloquear Usuario
│   ├── Desactivar Usuario
│   └── Reactivar Usuario
│
├── Dominios                              → Áreas organizacionales [rol: ADMIN]
│   ├── Listar Dominios
│   ├── Crear Dominio
│   ├── Desactivar Dominio
│   └── Reactivar Dominio
│
├── Solicitudes de Finalidad              → Workflow de aprobación
│   ├── Crear Solicitud                   → [rol: JEFE_DOMINIO]
│   ├── Mis Solicitudes                   → [rol: JEFE_DOMINIO]
│   ├── Solicitudes Pendientes            → [rol: DPO / ADMIN]
│   ├── Todas las Solicitudes             → [rol: DPO / ADMIN]
│   └── Revisar Solicitud                 → Aprobar o rechazar [rol: DPO / ADMIN]
│
├── Finalidades                           → Definición formal de tratamiento [rol: DPO]
│   ├── Listar Finalidades
│   ├── Obtener Finalidad por ID
│   ├── Finalidades por Dominio
│   ├── Crear Finalidad
│   ├── Editar Finalidad
│   └── Desactivar Finalidad
│
├── Bases de Licitud                      → Catálogo Ley 21.719 [lectura libre]
│   ├── Listar Bases
│   ├── Base por Consentimiento
│   └── Base por ID
│
├── Categorías de Datos                   → Tipos de datos personales [rol: DPO]
│   ├── Listar Categorías
│   ├── Categorías Sensibles
│   ├── Crear Categoría Custom
│   ├── Editar Categoría
│   └── Desactivar Categoría
│
├── Retención                             → Vínculo finalidad ↔ categoría + plazos [rol: DPO]
│   ├── Listar Vínculos
│   ├── Vincular + Política de Retención
│   ├── Actualizar Retención
│   └── Desvincular
│
├── Documentos de Privacidad              → Workflow de documentos [rol: DPO]
│   ├── Listar Documentos
│   ├── Crear Documento (DRAFT)
│   ├── Obtener Documento
│   ├── Editar Documento (DRAFT)
│   ├── Vincular Finalidad
│   ├── Desvincular Finalidad
│   ├── Enviar a Revisión
│   ├── Aprobar
│   ├── Rechazar
│   ├── Publicar
│   ├── Archivar
│   ├── Nueva Versión
│   ├── Listar Familia
│   ├── Descargar PDF
│   ├── Verificar Integridad
│   └── Documento Activo por Categoría
│
├── Auditoría                             → Log inmutable SHA-256 [rol: ADMIN]
│   ├── Consultar Logs
│   └── Verificar Cadena
│
└── Notificaciones                        → In-app del usuario autenticado
    ├── Listar Notificaciones
    ├── Contador No Leídas
    ├── Marcar como Leída
    └── Marcar Todas como Leídas
```

---

## Notas

- Los endpoints que tienen `{id}` en la URL incluyen un placeholder — reemplazarlo con el UUID real antes de ejecutar, ya sea editando el path directamente o usando una variable de entorno.
- Las acciones de gestión del realm (Listar Usuarios, Ver Roles, Habilitar/Deshabilitar) requieren el **Token Admin** obtenido con `Token Admin (Service Account)`, no el token de usuario normal.
- La spec OpenAPI completa está en `http://localhost:8080/swagger-ui.html` con el backend corriendo. También se puede importar directamente en Postman desde `http://localhost:8080/v3/api-docs`.
