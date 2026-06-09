# Guía de Instalación — Ley Data

**Stack:** Java 21 · Spring Boot 4.0.6 · PostgreSQL 15 · Keycloak 26 · Docker  
**Repositorio:** https://github.com/SaMii0108/leydata

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Clonar el repositorio](#2-clonar-el-repositorio)
3. [Infraestructura Docker](#3-infraestructura-docker)
4. [Configurar Keycloak](#4-configurar-keycloak)
5. [Levantar el backend](#5-levantar-el-backend)
6. [Verificar que todo funciona](#6-verificar-que-todo-funciona)
7. [Comandos del día a día](#7-comandos-del-día-a-día)
8. [Problemas frecuentes](#8-problemas-frecuentes)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Docker Desktop | 4.x | `docker --version` |
| Java JDK | 21 | `java -version` |
| Git | cualquiera | `git --version` |

**Windows:** usar WSL 2 con Ubuntu. En Docker Desktop activar Settings > Resources > WSL Integration > Ubuntu.

**macOS / Linux:** Docker Desktop o Docker Engine instalado directamente.

---

## 2. Clonar el repositorio

```bash
git clone https://github.com/SaMii0108/leydata.git
cd leydata
```

---

## 3. Infraestructura Docker

Desde la raíz del proyecto:

```bash
docker-compose up -d
```

Verificar que los tres contenedores estén corriendo:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Resultado esperado:

```
NAMES                         STATUS    PORTS
leydata-consent-db            Up        0.0.0.0:5433->5432/tcp
leydata-consent-keycloak-db   Up        5432/tcp
leydata-consent-keycloak      Up        0.0.0.0:8180->8080/tcp
```

La primera vez que inicia `leydata-consent-keycloak` puede tardar entre 30 y 60 segundos. Esperar antes de continuar.

---

## 4. Configurar Keycloak

Este paso solo es necesario la primera vez, o después de un reset completo de volúmenes. El script es idempotente: si algo ya existe lo omite sin fallar, por lo que puede ejecutarse más de una vez sin problema.

### Requisito previo

Los contenedores del paso 3 deben estar corriendo. El script espera Keycloak en `http://localhost:8180` con credenciales `admin / admin`. Si Keycloak todavía está iniciando, el script espera automáticamente hasta 90 segundos antes de fallar.

### Ejecutar el script

```bash
bash scripts/setup-keycloak.sh
```

### Qué hace el script paso a paso

**1. Crea el realm `leydata`**  
Unidad de configuración en Keycloak. Todos los usuarios, roles y clientes del proyecto viven dentro de este realm. Token de acceso configurado con expiración de 5 minutos.

**2. Crea los tres roles del sistema**

| Rol | Uso |
|---|---|
| `ADMIN` | Gestión de usuarios, dominios y auditoría |
| `DPO` | Revisión de solicitudes de propósito |
| `JEFE_DOMINIO` | Creación de solicitudes de propósito |

**3. Crea el cliente `leydata-frontend`**  
Cliente público (sin secret). Es el que usa el frontend para autenticar usuarios. Permite el flujo `password` (necesario para pruebas con Postman o curl) y el flujo estándar de redirección.

**4. Crea el cliente `leydata-backend`**  
Cliente confidencial con service account habilitado. El backend lo usa para llamar a la API de administración de Keycloak cuando un admin crea un usuario nuevo vía `POST /api/users`. Sin este cliente, la creación de usuarios falla.

**5. Asigna permisos al service account de `leydata-backend`**  
Le otorga los roles `manage-users` y `view-realm` del cliente interno `realm-management`. Estos permisos son los que le permiten al backend crear, leer y modificar usuarios en Keycloak.

**6. Crea el usuario administrador**  
Crea `admin@leydata.cl` con contraseña `Admin1234!` y le asigna el rol `ADMIN`. Este es el único usuario que se crea directamente en Keycloak. Los demás usuarios del sistema (DPO, JEFE_DOMINIO) se crean desde la API del backend con `POST /api/users`, que los registra simultáneamente en Keycloak y en la base de datos local.

### Salida esperada al finalizar

```
======================================================
 Keycloak configurado correctamente.

 Credenciales de acceso:
   Admin UI:  http://localhost:8180  (admin / admin)
   App admin: admin@leydata.cl / Admin1234!

 Variable de entorno para el backend:
   KC_BACKEND_SECRET=<valor>

 Arrancar el backend con:
   DB_USER=admin DB_PASS=admin DB_NAME=leydata_db \
   KC_BACKEND_SECRET=<valor> \
   ./mvnw spring-boot:run
======================================================
```

Copiar el valor de `KC_BACKEND_SECRET` antes de continuar. Este valor cambia cada vez que se recrea el cliente `leydata-backend` (es decir, después de cada reset completo).

---

## 5. Levantar el backend

Requiere las siguientes variables de entorno. Las tres primeras son fijas; `KC_BACKEND_SECRET` se obtiene en el paso anterior.

| Variable | Valor |
|---|---|
| `DB_USER` | `admin` |
| `DB_PASS` | `admin` |
| `DB_NAME` | `leydata_db` |
| `KC_BACKEND_SECRET` | valor del paso 4 |

### WSL / macOS / Linux

```bash
cd backend
chmod +x mvnw   # solo la primera vez
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<secret> ./mvnw spring-boot:run
```

### Windows PowerShell

```powershell
cd backend
$env:DB_USER="admin"
$env:DB_PASS="admin"
$env:DB_NAME="leydata_db"
$env:KC_BACKEND_SECRET="<secret>"
.\mvnw.cmd spring-boot:run
```

### VS Code

Crear o editar `.vscode/launch.json`:

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "Backend",
      "request": "launch",
      "mainClass": "com.leydata.backend.BackendApplication",
      "env": {
        "DB_USER": "admin",
        "DB_PASS": "admin",
        "DB_NAME": "leydata_db",
        "KC_BACKEND_SECRET": "<secret>"
      }
    }
  ]
}
```

El backend está listo cuando aparece en consola:

```
Started BackendApplication in X.XXX seconds
```

---

## 6. Verificar que todo funciona

### Obtener un token de acceso

```bash
curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=leydata-frontend" \
  --data-urlencode "username=admin@leydata.cl" \
  --data-urlencode "password=Admin1234!"
```

Resultado esperado: JSON con `access_token`. El token empieza con `eyJ`.

### Probar un endpoint protegido

```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer <access_token>"
```

Resultado esperado: `200 OK` con la lista de usuarios.

### Documentación interactiva

Con el backend corriendo, abrir en el navegador:

```
http://localhost:8080/swagger-ui.html
```

Para autenticarse en Swagger UI: click en el botón Authorize, ingresar `Bearer <access_token>` y confirmar.

---

## 7. Comandos del día a día

### Levantar el entorno completo

```bash
docker-compose up -d
cd backend && DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<secret> ./mvnw spring-boot:run
```

### Detener los contenedores sin perder datos

```bash
docker-compose stop
```

### Eliminar contenedores sin perder datos de base de datos

```bash
docker-compose down
```

### Reset completo — elimina todos los datos

```bash
docker-compose down -v
rm -rf ./postgres_data
docker-compose up -d
```

Después de un reset completo es necesario ejecutar nuevamente el paso 4 (configurar Keycloak).

### Ver logs de un contenedor

```bash
docker logs leydata-consent-keycloak --tail 50
docker logs leydata-consent-db --tail 50
```

### Compilar sin levantar

```bash
cd backend
./mvnw clean package -DskipTests
```

---

## 8. Problemas frecuentes

---

### El backend no inicia — `Unable to obtain configuration from issuer`

El realm `leydata` no existe en Keycloak. Ocurre después de un reset de volúmenes o en una instalación nueva sin haber ejecutado el script de configuración.

Solución: ejecutar el paso 4.

```bash
bash scripts/setup-keycloak.sh
```

---

### `POST /api/users` retorna 500

El backend no puede crear usuarios en Keycloak. Causas posibles:

- `KC_BACKEND_SECRET` incorrecto o no configurado.
- El service account de `leydata-backend` no tiene los roles necesarios.

Verificar en Keycloak Admin (http://localhost:8180) > Clients > leydata-backend > Service account roles. Deben aparecer `manage-users` y `view-realm` del cliente `realm-management`. Si no están, ejecutar el script de configuración nuevamente.

---

### Login devuelve `"Account is not fully set up"`

Keycloak 26 exige que el campo `Last name` del usuario no esté vacío. El usuario puede obtener el token pero Keycloak bloquea el acceso.

Solución manual: Keycloak Admin > Users > seleccionar el usuario > completar el campo Last name > Save.

Al crear usuarios con `POST /api/users`, el campo `name` del request debe incluir nombre y apellido separados por espacio (por ejemplo: `"María García"`). El backend divide el string y usa la primera palabra como firstName y el resto como lastName.

---

### Contenedor `leydata-consent-db` en bucle de reinicios

Mensaje de error en los logs:

```
FATAL: database files are incompatible with server
DETAIL: The data directory was initialized by PostgreSQL version 15,
        which is not compatible with this version 16.
```

El directorio `./postgres_data` fue creado con una versión de PostgreSQL diferente a la de la imagen en `docker-compose.yml`. No hacer upgrade de versión de imagen si ya existen datos.

Solución sin perder datos: revertir la imagen a la versión original (`postgres:15`).

Solución aceptando pérdida de datos:

```bash
docker-compose down -v
rm -rf ./postgres_data
docker-compose up -d
bash scripts/setup-keycloak.sh
```

---

### `GET /api/audit/logs/verify` retorna `valid: false` sin manipulación

Los logs de auditoría fueron generados con una versión anterior del código que no truncaba el timestamp a microsegundos. Java 21 en Linux genera timestamps con nanosegundos (9 decimales) pero PostgreSQL `timestamp(6)` almacena solo microsegundos (6 decimales), causando que el hash guardado no coincida con el recalculado.

El código actual ya aplica la corrección (`truncatedTo(ChronoUnit.MICROS)`). Los logs anteriores a la corrección tienen hashes inválidos de forma permanente.

Para restablecer una cadena válida, conectarse a la base de datos y ejecutar:

```sql
TRUNCATE system_audit_log;
```

Los nuevos logs generados a partir de ese momento se verificarán correctamente.

---

### Migración de nombres de contenedores

Los contenedores fueron renombrados para evitar conflictos entre proyectos:

| Nombre anterior | Nombre actual |
|---|---|
| `leydata-keycloak` | `leydata-consent-keycloak` |
| `leydata-db` | `leydata-consent-db` |
| `keycloak-db` | `leydata-consent-keycloak-db` |

Si el entorno local todavía tiene los contenedores con los nombres anteriores, seguir este orden:

```bash
# 1. Bajar contenedores antes de hacer git pull
docker-compose down

# 2. Traer los cambios
git pull

# 3. Levantar con los nombres nuevos
docker-compose up -d
```

Si ya se hizo `git pull` antes de bajar los contenedores:

```bash
docker stop leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker rm   leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker-compose up -d
```

`docker-compose down` sin `-v` conserva los volúmenes, por lo que los datos de la base de datos y la configuración de Keycloak no se pierden.

---

### Puerto 8080 o 5433 ya en uso

```bash
# WSL / macOS
lsof -i :8080
kill -9 <PID>

# PowerShell
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```
