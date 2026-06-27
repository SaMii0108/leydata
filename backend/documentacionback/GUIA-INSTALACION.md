# Guía de Instalación — Ley Data

**Stack:** Java 21 · Spring Boot 3.x · PostgreSQL 15 · Keycloak 26 · Redis 7 · Docker  
**Repositorio:** https://github.com/SaMii0108/leydata  
**Arquitectura:** Modular DDD — cada módulo tiene capas `web/`, `application/`, `infrastructure/`, `domain/` — ver [ESTRUCTURA-PROYECTO.md](ESTRUCTURA-PROYECTO.md)

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Clonar el repositorio](#2-clonar-el-repositorio)
3. [Crear el archivo .env](#3-crear-el-archivo-env)
4. [Infraestructura Docker](#4-infraestructura-docker)
5. [Configurar Keycloak](#5-configurar-keycloak)
6. [Levantar el backend](#6-levantar-el-backend)
7. [Verificar que todo funciona](#7-verificar-que-todo-funciona)
8. [Actualizar el proyecto](#8-actualizar-el-proyecto)
9. [Comandos del día a día](#9-comandos-del-día-a-día)
10. [Problemas frecuentes](#10-problemas-frecuentes)

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

## 3. Crear el archivo .env

El proyecto centraliza todas las credenciales en un archivo `.env` en la raíz del proyecto. Este archivo cumple dos funciones:

- **Docker Compose** lo lee automáticamente al ejecutar `docker-compose up -d` para configurar los contenedores de PostgreSQL.
- **El backend** lo usa como referencia de las variables de entorno necesarias para arrancar.

El archivo no está en el repositorio (está en `.gitignore`). Hay que crearlo a partir del ejemplo incluido:

**WSL / macOS / Linux:**
```bash
cp .env.example .env
```

**Windows PowerShell:**
```powershell
Copy-Item .env.example .env
```

El archivo `.env` resultante tiene este contenido:

```
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
KC_BACKEND_SECRET=
```

Las primeras tres variables son fijas para el entorno de desarrollo local y no necesitan cambiarse. `KC_BACKEND_SECRET` se rellena en el paso 5, después de configurar Keycloak. Dejarla vacía por ahora.

---

## 4. Infraestructura Docker

Desde la raíz del proyecto:

```bash
docker-compose up -d
```

Docker Compose lee el `.env` automáticamente para las credenciales de la base de datos. No es necesario pasar variables adicionales a este comando.

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
leydata-redis                 Up        0.0.0.0:6379->6379/tcp
```

La primera vez que inicia `leydata-consent-keycloak` puede tardar entre 30 y 60 segundos. Esperar antes de continuar.

---

## 5. Configurar Keycloak

### ¿Cuándo hay que ejecutar este script?

| Situación | ¿Ejecutar el script? |
|---|---|
| Primera instalación (clone nuevo) | **Sí** |
| Después de `docker-compose down -v` (reset total) | **Sí** |
| Después de borrar el volumen `keycloak_data` manualmente | **Sí** |
| `docker-compose stop` + `docker-compose up -d` (parada normal) | No |
| Reiniciar la PC y volver a levantar contenedores | No |
| `git pull` + reiniciar el backend | No |
| Reiniciar solo el backend | No |

El script es idempotente: si el realm y los clientes ya existen, los omite sin fallar. Se puede ejecutar más de una vez sin problema. Lo importante es que después de ejecutarlo siempre hay que **actualizar `KC_BACKEND_SECRET` en el `.env`** con el valor que imprime.

### Requisito previo

Los contenedores del paso 4 deben estar corriendo. El script espera Keycloak en `http://localhost:8180` con credenciales `admin / admin`. Si Keycloak todavía está iniciando, el script espera automáticamente hasta 90 segundos antes de fallar.

### Ejecutar el script

Usar el script correspondiente al sistema operativo:

**WSL / macOS / Linux:**
```bash
bash scripts/setup-keycloak.sh
```

**Windows PowerShell (sin WSL):**
```powershell
.\scripts\setup-keycloak.ps1
```

### Qué hace el script paso a paso

**1. Crea el realm `leydata`**  
Unidad de configuración en Keycloak. Todos los usuarios, roles y clientes del proyecto viven dentro de este realm. Token de acceso configurado con expiración de 5 minutos.

**2. Crea los cinco roles del sistema**

| Rol | Uso |
|---|---|
| `ADMIN` | Gestión de usuarios, dominios y auditoría |
| `DPO` | Revisión de solicitudes de propósito y gestión de documentos |
| `JEFE_DOMINIO` | Creación de solicitudes de propósito para sus dominios asignados |
| `USER` | Operador interno con acceso de solo lectura |
| `TITULAR` | Titular de datos personales (acceso al portal de consentimiento) |

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
   KC_BACKEND_SECRET=<valor generado>

 Arrancar el backend con:
   export $(cat .env | xargs) && cd backend && ./mvnw spring-boot:run
======================================================
```

### Actualizar el .env con el secret

Copiar el valor de `KC_BACKEND_SECRET` que imprime el script y pegarlo en el `.env`:

```
KC_BACKEND_SECRET=<valor copiado aquí>
```

Este valor cambia cada vez que se recrea el cliente `leydata-backend` (es decir, después de cada reset completo). Si el backend arranca con un secret incorrecto, `POST /api/users` retornará 500.

---

## 6. Levantar el backend

### WSL / macOS / Linux

Cargar el `.env` y levantar el backend en un solo comando:

```bash
cd backend
chmod +x mvnw   # solo la primera vez
export $(cat ../.env | xargs) && ./mvnw spring-boot:run
```

O pasar las variables directamente copiándolas desde el `.env`:

```bash
DB_USER=<DB_USER del .env> DB_PASS=<DB_PASS del .env> DB_NAME=<DB_NAME del .env> KC_BACKEND_SECRET=<KC_BACKEND_SECRET del .env> ./mvnw spring-boot:run
```

### Windows PowerShell

```powershell
# Leer el .env y setear las variables
Get-Content ..\.env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}

cd backend
.\mvnw.cmd spring-boot:run
```

O setear manualmente:

```powershell
$env:DB_USER="admin"
$env:DB_PASS="admin"
$env:DB_NAME="leydata_db"
$env:KC_BACKEND_SECRET="<secret>"
cd backend
.\mvnw.cmd spring-boot:run
```

### VS Code

El repositorio ya incluye `.vscode/launch.json` con la configuración `BackendApplication`. Esta configuración tiene `"envFile": "${workspaceFolder}/.env"`, lo que significa que VS Code **lee el `.env` automáticamente** cada vez que se arranca el backend.

No hay nada que configurar. Solo asegurarse de que el `.env` tenga el `KC_BACKEND_SECRET` correcto y luego arrancar con `F5` o desde el menú **Run > Start Debugging > BackendApplication**.

Si por algún motivo el `.vscode/launch.json` no existe, crearlo en la raíz del proyecto con este contenido exacto:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Current File",
            "request": "launch",
            "mainClass": "${file}"
        },
        {
            "type": "java",
            "name": "BackendApplication",
            "request": "launch",
            "mainClass": "com.leydata.backend.BackendApplication",
            "projectName": "backend",
            "envFile": "${workspaceFolder}/.env"
        }
    ]
}
```

La clave es `"envFile": "${workspaceFolder}/.env"` en la configuración `BackendApplication`. Sin esa línea, VS Code no carga las variables y el backend arranca sin `KC_BACKEND_SECRET`, causando que `POST /api/users` falle con 500.

El backend está listo cuando aparece en consola:

```
Started BackendApplication in X.XXX seconds
```

> Para entender cómo está organizado el código fuente del backend, ver [ESTRUCTURA-PROYECTO.md](ESTRUCTURA-PROYECTO.md).

### Qué hace el backend al iniciar

Al arrancar, el backend ejecuta automáticamente `CatalogSeeder`, que siembra las bases de licitud de la Ley 21.719 y las categorías de datos predefinidas si las tablas están vacías. Es idempotente: si el catálogo ya existe, no hace nada.

**No se siembran roles en la BD local.** En el modelo Keycloak-first, los roles viven en el realm de Keycloak y se crean con el script del paso 5 (`setup-keycloak.sh`). No existe ninguna tabla `roles` ni `users_roles` en PostgreSQL.

**No se crea ningún usuario local.** Los usuarios de la BD local se crean exclusivamente vía `POST /api/users` (que los registra en Keycloak y en la BD simultáneamente). El único usuario que existe después de un reset limpio es `admin@leydata.cl` en Keycloak — creado por el script del paso 5.

`TestDataSeeder` (datos de prueba) solo se activa con el perfil Spring `test-data` y no debe usarse en producción.

---

## 7. Verificar que todo funciona

### Obtener un token de acceso

```bash
curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=leydata-frontend" \
  --data-urlencode "username=admin@leydata.cl" \
  --data-urlencode "password=Admin1234!"
```

Resultado esperado: JSON con `access_token`. El token comienza con `eyJ`.

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

Para autenticarse en Swagger UI: click en el botón **Authorize**, seleccionar **oauth2**, ingresar las credenciales del operador y click en **Authorize**. El cliente `leydata-frontend` ya viene preconfigurado.

### Importar colección a Postman o Bruno

| Formato | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

En Postman / Bruno: **Import → Link** y pegar la URL. La colección incluye el esquema OAuth2 preconfigurado apuntando a Keycloak. Al importar, configurar la autenticación OAuth 2.0:

- Grant type: **Password Credentials**
- Token URL: `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
- Client ID: `leydata-frontend`
- Client Secret: *(dejar vacío)*
- Username / Password: las del operador a probar

---

## 8. Actualizar el proyecto

Cuando alguien del equipo hace `git pull` para traer cambios nuevos, es necesario limpiar el directorio `target/` antes de volver a levantar el backend. El `target/` contiene las clases Java compiladas de la versión anterior. Si no se limpia, Maven puede levantar el backend con código viejo mezclado con código nuevo, causando errores difíciles de diagnosticar.

### Cuándo limpiar obligatoriamente

Limpiar siempre que el pull incluya cambios en:

- `pom.xml` — dependencias nuevas o versiones actualizadas
- Cualquier archivo `.java` — clases modificadas, agregadas o eliminadas
- `application.properties` — cambios de configuración

En la práctica, lo más seguro es limpiar siempre después de un `git pull`.

### Cómo actualizar correctamente

```bash
# 1. Traer los cambios
git pull

# 2. Limpiar el compilado anterior y levantar desde cero
cd backend
export $(cat ../.env | xargs) && ./mvnw clean spring-boot:run
```

El flag `clean` elimina la carpeta `target/` completa antes de compilar. Tarda un poco más en la primera compilación después del pull, pero garantiza que no hay clases obsoletas.

### Si solo quieres compilar sin levantar

```bash
cd backend
./mvnw clean package -DskipTests
```

Genera el JAR en `target/`. Útil para verificar que el código compila antes de levantar.

### Señales de que olvidaste limpiar

- El backend arranca pero se comporta diferente a lo que el código indica
- Errores como `NoSuchMethodError`, `ClassNotFoundException` o `IncompatibleClassChangeError`
- Cambios en el código que no tienen efecto al correr el backend

En cualquiera de esos casos, detener el backend, ejecutar `./mvnw clean spring-boot:run` y volver a probar.

---

## 9. Comandos del día a día

### Levantar el entorno completo

```bash
# Desde la raíz del proyecto
docker-compose up -d
cd backend && export $(cat ../.env | xargs) && ./mvnw spring-boot:run
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
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

Después del reset, actualizar `KC_BACKEND_SECRET` en el `.env` con el nuevo valor que imprime el script.

### Ver logs de un contenedor

```bash
docker logs leydata-consent-keycloak --tail 50
docker logs leydata-consent-db --tail 50
docker logs leydata-redis --tail 50
```

### Verificar Redis

```bash
# Conectarse a Redis y probar
docker exec -it leydata-redis redis-cli ping
# Resultado esperado: PONG

# Ver keys almacenadas (en desarrollo)
docker exec -it leydata-redis redis-cli keys "*"
```

### Compilar sin levantar

```bash
cd backend
./mvnw clean package -DskipTests
```

---

## 10. Problemas frecuentes

---

### El backend no inicia — `Unable to obtain configuration from issuer`

El realm `leydata` no existe en Keycloak. Ocurre después de un reset de volúmenes o en una instalación nueva sin haber ejecutado el script de configuración.

Solución: ejecutar el paso 5.

```bash
bash scripts/setup-keycloak.sh
```

---

### `POST /api/users` retorna 500 — `Invalid client or Invalid client credentials`

El `KC_BACKEND_SECRET` que usa el backend no coincide con el que tiene Keycloak. Ocurre típicamente después de un reset de contenedores, cuando el script genera un nuevo secret y el `.env` todavía tiene el valor anterior.

**El secret NO cambia en reinicios normales.** Solo cambia cuando se hace `docker-compose down -v` o se borra el volumen `keycloak_data`. `GET /api/users` y otros endpoints siguen funcionando porque solo validan el JWT del usuario — no necesitan el service account.

Solución:

```bash
# 1. Obtener el secret actual
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows

# 2. Copiar el KC_BACKEND_SECRET que imprime y pegarlo en el .env

# 3. Reiniciar el backend (VS Code: F5, o desde terminal con export $(cat ../.env | xargs))
```

Verificar también en Keycloak Admin (http://localhost:8180) > Clients > leydata-backend > Service account roles. Deben aparecer `manage-users` y `view-realm` del cliente `realm-management`. Si no están, ejecutar el script de configuración nuevamente.

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

### La BD local tiene usuarios de pruebas anteriores

`docker-compose down` **sin `-v` no borra los datos**. Los volúmenes de PostgreSQL persisten aunque el contenedor se elimine. Los usuarios que ves en la tabla `users` son registros reales creados vía `POST /api/users` durante sesiones de prueba anteriores — no desaparecen con un simple reinicio.

Esto es el comportamiento esperado. Si necesitas partir de una BD vacía (por ejemplo, para probar el flujo completo desde cero):

```bash
docker-compose down -v
docker-compose up -d
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

Después del reset, la BD local estará vacía (solo se siembran catálogos fijos al arrancar el backend, no roles ni usuarios). Los usuarios de prueba previos desaparecerán. Actualizar `KC_BACKEND_SECRET` en el `.env` con el nuevo valor del script.

---

### Redis no responde — `Connection refused` al iniciar el backend

El backend intenta conectarse a Redis en `localhost:6379` al arrancar. Si Redis no está corriendo, el backend falla.

Verificar que el contenedor esté activo:
```bash
docker ps | grep leydata-redis
```

Si no aparece, levantarlo:
```bash
docker-compose up -d redis
```

Si el puerto 6379 ya está en uso por otro proceso Redis local, detenerlo primero:
```bash
# Linux / WSL
sudo systemctl stop redis
# o
sudo service redis stop
```

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
