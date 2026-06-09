# Guía de Instalación y Configuración — Ley Data

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)  
**Stack:** Java 21 · Spring Boot 4.0.6 · Hibernate 7.2 · PostgreSQL 16 · Keycloak 26 · Docker

---

## Índice

1. [Requisitos previos por sistema operativo](#1-requisitos-previos-por-sistema-operativo)
2. [Windows con WSL (recomendado)](#2-windows-con-wsl-recomendado)
3. [Windows nativo (PowerShell)](#3-windows-nativo-powershell)
4. [macOS](#4-macos)
5. [Levantar la infraestructura (Docker)](#5-levantar-la-infraestructura-docker)
6. [Configurar Keycloak](#6-configurar-keycloak) _(incluye cuenta de servicio y User Profile KC26)_
7. [Levantar el backend](#7-levantar-el-backend)
8. [Verificar que todo funciona](#8-verificar-que-todo-funciona)
9. [Comandos útiles del día a día](#9-comandos-útiles-del-día-a-día)
10. [Solución de problemas frecuentes](#10-solución-de-problemas-frecuentes)

---

## 1. Requisitos previos por sistema operativo

### Windows con WSL

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Windows | 10 (build 19041) o superior | `winver` |
| WSL 2 | — | `wsl --version` |
| Ubuntu (WSL) | 20.04 o superior | `lsb_release -a` |
| Docker Desktop | 4.x | Abrir la app |
| Java 21 (en Ubuntu) | 21 | `java -version` |
| Git | cualquiera | `git --version` |

### Windows nativo (PowerShell)

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Windows | 10 o superior | `winver` |
| Docker Desktop | 4.x | Abrir la app |
| Java 21 (JDK) | 21 | `java -version` |
| Git | cualquiera | `git --version` |

### macOS

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| macOS | 12 Monterey o superior | Sobre este Mac |
| Docker Desktop | 4.x | Abrir la app |
| Java 21 (JDK) | 21 | `java -version` |
| Git | cualquiera | `git --version` |
| Homebrew (recomendado) | — | `brew --version` |

---

## 2. Windows con WSL (recomendado)

### 2.1 Instalar WSL 2 y Ubuntu

Abrir **PowerShell como administrador** y ejecutar:

```powershell
wsl --install
```

Esto instala WSL 2 y Ubuntu automáticamente. Reiniciar el equipo cuando lo pida.

Al reiniciar, Ubuntu abrirá una terminal y pedirá crear un usuario UNIX (puede ser cualquier nombre).

Verificar que WSL 2 esté activo:
```powershell
wsl --list --verbose
# Debe mostrar VERSION 2
```

### 2.2 Instalar Java 21 en Ubuntu (WSL)

Abrir la terminal de Ubuntu y ejecutar:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk

# Verificar
java -version
# Debe mostrar: openjdk version "21..."
```

### 2.3 Instalar Docker Desktop

1. Descargar desde [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
2. Instalar y abrir Docker Desktop
3. En Settings → **General** → activar **"Use the WSL 2 based engine"**
4. En Settings → **Resources → WSL Integration** → activar la integración con Ubuntu
5. Aplicar y reiniciar Docker Desktop

Verificar desde Ubuntu:
```bash
docker --version
docker-compose --version
```

### 2.4 Clonar el repositorio

```bash
# Dentro de Ubuntu WSL (no en /mnt/c — usar el filesystem de Linux)
cd ~
git clone <url-del-repositorio> leydata
cd leydata
```

### 2.5 Crear el archivo .env

```bash
cd ~/leydata
cat > .env << 'EOF'
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
EOF
```

---

## 3. Windows nativo (PowerShell)

### 3.1 Instalar Java 21

1. Descargar el JDK 21 desde [https://adoptium.net](https://adoptium.net) → versión **Temurin 21 LTS**
2. Ejecutar el instalador `.msi`
3. Verificar en PowerShell:
   ```powershell
   java -version
   # Debe mostrar: openjdk version "21..."
   ```

Si Java no se reconoce, agregar manualmente al PATH:
```powershell
# Ejemplo (ajustar la ruta según donde se instaló)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot", "Machine")
[System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";%JAVA_HOME%\bin", "Machine")
```
Cerrar y reabrir PowerShell.

### 3.2 Instalar Docker Desktop

1. Descargar desde [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
2. Instalar — **no** es necesario activar la integración con WSL si no se usa
3. Abrir Docker Desktop y esperar a que el motor esté verde

### 3.3 Clonar el repositorio

```powershell
cd C:\proyectos   # o la carpeta que prefieras
git clone <url-del-repositorio> leydata
cd leydata
```

### 3.4 Crear el archivo .env

```powershell
@"
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
"@ | Out-File -FilePath .env -Encoding utf8
```

---

## 4. macOS

### 4.1 Instalar Java 21

**Con Homebrew (recomendado):**
```bash
brew install --cask temurin@21

# Verificar
java -version
```

**Sin Homebrew:**
1. Descargar desde [https://adoptium.net](https://adoptium.net) → Temurin 21 LTS → `.pkg` para macOS
2. Ejecutar el instalador

Si hay múltiples versiones de Java, asegurarse de usar la 21:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# Agregar esta línea a ~/.zshrc para que persista
```

### 4.2 Instalar Docker Desktop

1. Descargar desde [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
   - Elegir el instalador correcto: **Apple Silicon (M1/M2/M3)** o **Intel**
2. Arrastrar Docker al directorio de Aplicaciones
3. Abrir Docker Desktop y esperar a que el motor esté verde

### 4.3 Clonar el repositorio

```bash
cd ~/proyectos   # o la carpeta que prefieras
git clone <url-del-repositorio> leydata
cd leydata
```

### 4.4 Crear el archivo .env

```bash
cat > .env << 'EOF'
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
EOF
```

---

## 5. Levantar la infraestructura (Docker)

Este paso es **igual en los tres sistemas operativos**, ejecutando desde la raíz del proyecto (`leydata/`).

### Windows WSL — desde Ubuntu:
```bash
cd ~/leydata
docker-compose up -d
```

### Windows PowerShell — nativo:
```powershell
cd C:\proyectos\leydata
docker-compose up -d
```

### macOS:
```bash
cd ~/proyectos/leydata
docker-compose up -d
```

### Verificar que los contenedores estén corriendo

```bash
docker ps
```

Resultado esperado:

```
NAMES                        STATUS    PORTS
leydata-consent-db           Up        0.0.0.0:5433->5432/tcp
leydata-consent-keycloak-db  Up        5432/tcp
leydata-consent-keycloak     Up        0.0.0.0:8180->8080/tcp
```

> La primera vez que se levanta `leydata-keycloak` puede tardar entre 30 y 60 segundos en estar disponible. Esperar antes de continuar.

---

## 6. Configurar Keycloak

> **Si el entorno ya fue configurado anteriormente** (volumen `keycloak_data` existente), omitir este paso — la configuración persiste entre reinicios.

Abrir en el navegador: **http://localhost:8180**  
Usuario: `admin` / Contraseña: `admin`

### 6.1 Crear el Realm

1. Click en el selector de realm (esquina superior izquierda, dice "Keycloak")
2. "Create realm"
3. Realm name: `leydata`
4. Enabled: **ON**
5. Click "Create"

### 6.2 Crear los Realm Roles

Menú izquierdo → **Realm roles** → **Create role**

Crear los siguientes tres roles (uno por uno):

| Nombre del rol | Descripción |
|---|---|
| `ADMIN` | Administrador del sistema |
| `DPO` | Data Protection Officer |
| `JEFE_DOMINIO` | Jefe de dominio organizacional |

### 6.3 Crear el Cliente

Menú izquierdo → **Clients** → **Create client**

| Campo | Valor |
|---|---|
| Client type | `OpenID Connect` |
| Client ID | `leydata-frontend` |
| Client authentication | **OFF** (tipo public) |
| Standard flow | **ON** |
| Direct access grants | **ON** (necesario para pruebas con Postman/curl) |
| Valid redirect URIs | `http://localhost:5173/*` y `http://localhost:3000/*` |
| Web origins | `http://localhost:5173` y `http://localhost:3000` |

Click "Save".

### 6.4 Crear el usuario administrador inicial

Menú izquierdo → **Users** → **Create new user**

| Campo | Valor |
|---|---|
| Username | `admin` |
| Email | `admin@leydata.cl` |
| First name | `Administrador` |
| Last name | `Leydata` |
| Email verified | **ON** |

Click "Create", luego:

- Pestaña **Credentials** → "Set password"
  - Password: `Admin1234!`
  - Temporary: **OFF**
  - Click "Save password"

- Pestaña **Role mapping** → "Assign role"
  - Seleccionar `ADMIN` → "Assign"

> ⚠️ **Keycloak 26 — ambos campos son obligatorios:** En KC 26 el perfil de usuario del realm exige que `First name` y `Last name` sean no vacíos. Si alguno queda en blanco, el usuario recibe el error `"Account is not fully set up"` al hacer login aunque las credenciales sean correctas. Siempre completar ambos campos.

### 6.5 Crear usuarios operadores (DPO, JEFE_DOMINIO)

> **No crear usuarios DPO o JEFE_DOMINIO directamente en Keycloak.** El backend tiene un endpoint `POST /api/users` que los crea simultáneamente en Keycloak y en la BD local con un solo request. Creando directamente en Keycloak el usuario no quedaría registrado en la BD local y el sistema lo rechazaría.

Con el backend corriendo, usar el token del admin para crear operadores:

```bash
# Obtener token de admin primero
TOKEN=$(curl -s -X POST http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Crear DPO
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"dpo@leydata.cl","name":"María DPO","roleCode":"DPO","domainIds":[],"password":"Test1234!"}'
```

> **Formato del campo `name`:** Enviar nombre y apellido separados por espacio (ej: `"Pedro García"`). El backend separa el string en `firstName` y `lastName` para Keycloak. Si se envía una sola palabra, KC la usa como `lastName` también.

### 6.6 Crear la cuenta de servicio `leydata-backend`

El backend necesita una cuenta de servicio en Keycloak para crear y gestionar usuarios en KC desde la API. Sin esta cuenta, `POST /api/users` fallará.

Menú izquierdo → **Clients** → **Create client**

**Paso 1 — Datos generales:**

| Campo | Valor |
|---|---|
| Client type | `OpenID Connect` |
| Client ID | `leydata-backend` |

Click "Next".

**Paso 2 — Capability config:**

| Campo | Valor |
|---|---|
| Client authentication | **ON** (tipo confidential) |
| Authorization | OFF |
| Standard flow | **OFF** |
| Direct access grants | **OFF** |
| Service account roles | **ON** |

Click "Next" → "Save".

**Paso 3 — Copiar el Client Secret:**

Pestaña **Credentials** → copiar el valor del campo **Client secret**.

Este valor es la variable de entorno `KC_BACKEND_SECRET` que se pasa al backend al arrancarlo.

**Paso 4 — Asignar roles de administración:**

Pestaña **Service account roles** → "Assign role" → cambiar el filtro a **"Filter by clients"** → buscar `realm-management` → seleccionar:

- `manage-users`
- `view-realm`

Click "Assign".

Sin estos roles, el backend no podrá crear ni leer usuarios en Keycloak y `POST /api/users` retornará error 500.

---

## 7. Levantar el backend

### Windows WSL — desde Ubuntu:

```bash
cd ~/leydata/backend
chmod +x mvnw   # solo la primera vez
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<client_secret_de_leydata-backend> ./mvnw spring-boot:run
```

### Windows PowerShell — nativo:

```powershell
cd C:\proyectos\leydata\backend
$env:DB_USER="admin"; $env:DB_PASS="admin"; $env:DB_NAME="leydata_db"; $env:KC_BACKEND_SECRET="<client_secret_de_leydata-backend>"
.\mvnw.cmd spring-boot:run
```

### macOS:

```bash
cd ~/proyectos/leydata/backend
chmod +x mvnw   # solo la primera vez
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<client_secret_de_leydata-backend> ./mvnw spring-boot:run
```

> `KC_BACKEND_SECRET` es el Client Secret del cliente `leydata-backend` configurado en Keycloak (sección 6.6). Sin esta variable, el backend arranca pero `POST /api/users` falla al intentar crear usuarios en Keycloak.

### Usando VS Code (cualquier sistema)

Instalar la extensión **Spring Boot Extension Pack**.  
Ir a la pestaña **Spring Boot Dashboard** → click derecho en el proyecto → **Run** o **Debug**.  
Las variables de entorno se pueden configurar en `.vscode/launch.json`:

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot - backend",
      "request": "launch",
      "mainClass": "com.leydata.backend.BackendApplication",
      "env": {
        "DB_USER": "admin",
        "DB_PASS": "admin",
        "DB_NAME": "leydata_db",
        "KC_BACKEND_SECRET": "<client_secret_de_leydata-backend>"
      }
    }
  ]
}
```

### Señal de inicio exitoso

```
Started BackendApplication in X.XXX seconds
```

> **Requisito:** Keycloak debe estar corriendo antes de iniciar el backend. Al arrancar, Spring descarga automáticamente la configuración del realm desde `http://localhost:8180/realms/leydata/.well-known/openid-configuration`.

---

## 8. Verificar que todo funciona

### 8.1 Obtener un token de prueba

**curl (WSL / macOS / Git Bash):**
```bash
curl -X POST http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=leydata-frontend" \
  -d "username=admin@leydata.cl" \
  -d "password=Admin1234!"
```

**PowerShell nativo:**
```powershell
$body = "grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!"
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8180/realms/leydata/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body $body
```

**Postman:**
- Method: `POST`
- URL: `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
- Body → **x-www-form-urlencoded**:

| Key | Value |
|---|---|
| `grant_type` | `password` |
| `client_id` | `leydata-frontend` |
| `username` | `admin@leydata.cl` |
| `password` | `Admin1234!` |

> ⚠️ **Problema frecuente en Postman — leer antes de probar:**
>
> Al usar `x-www-form-urlencoded` en la pestaña Body, Postman genera automáticamente el header `Content-Type: application/x-www-form-urlencoded`. Para que funcione:
>
> 1. Ir a la pestaña **Headers**
> 2. Verificar que la fila `Content-Type: application/x-www-form-urlencoded` esté **marcada** (checkbox ✅)
> 3. Si hay un `Content-Type: application/json`, **desmarcar o eliminar esa fila**
>
> Si el error persiste, usar el método alternativo raw (ver sección de troubleshooting).

Respuesta exitosa:
```json
{
  "access_token": "eyJhbGci...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```

### 8.2 Probar un endpoint protegido

Copiar el `access_token` y usarlo como Bearer:

```bash
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer eyJhbGci..."
```

Resultado esperado: `200 OK` con lista de usuarios.

---

### 8.3 Swagger UI — documentación interactiva del API

Con el backend corriendo, abrir en el navegador:

```
http://localhost:8080/swagger-ui.html
```

Muestra todos los endpoints con sus parámetros, bodies y respuestas. Para probar endpoints desde el navegador:

1. Click en **Authorize** (candado 🔒)
2. Escribir `Bearer <access_token>` en el campo Value
3. Click **Authorize**
4. Ahora se pueden ejecutar requests directamente desde la UI

**Descargar el spec OpenAPI** (útil para importar en Postman o generar clientes):

```
http://localhost:8080/v3/api-docs
```

En Postman: **Import** → pegar esa URL → genera la colección completa automáticamente.

---

## 9. Comandos útiles del día a día

### Levantar todo el entorno

```bash
# WSL / macOS
cd ~/leydata
docker-compose up -d
cd backend && DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<client_secret_de_leydata-backend> ./mvnw spring-boot:run
```

### Apagar los contenedores (sin perder datos)

```bash
docker-compose stop
```

### Apagar y eliminar contenedores (sin perder datos de BD)

```bash
docker-compose down
# Los datos persisten en ./postgres_data y el volumen keycloak_data
```

### Reset completo (BORRA TODOS LOS DATOS)

```bash
docker-compose down -v        # elimina volúmenes
rm -rf ./postgres_data        # elimina datos de BD
docker-compose up -d          # vuelve a crear todo desde cero
```

> Después de un reset completo hay que volver a configurar Keycloak (sección 6).

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

### Correr tests

```bash
cd backend
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db ./mvnw test
```

---

### ⚠️ Migración — actualizar nombres de contenedores (leer si ya tenías el entorno levantado)

A partir de este commit los contenedores Docker se renombraron para evitar conflictos con otros proyectos del equipo:

| Nombre anterior | Nombre actual |
|---|---|
| `leydata-keycloak` | `leydata-consent-keycloak` |
| `leydata-db` | `leydata-consent-db` |
| `keycloak-db` | `leydata-consent-keycloak-db` |

**El orden importa.** Si ya tenías los contenedores corriendo con los nombres anteriores, seguir estos pasos:

```bash
# 1. Bajar los contenedores ANTES de hacer git pull
#    (mientras el compose aún conoce los nombres viejos, los encuentra bien)
docker-compose down

# 2. Traer los cambios del repositorio
git pull

# 3. Levantar con los nuevos nombres
docker-compose up -d
```

> `docker-compose down` sin `-v` **conserva los volúmenes** — la configuración de Keycloak y la BD no se pierden. Solo se recrea el contenedor con el nuevo nombre.

**Si ya hiciste `git pull` antes de bajar los contenedores**, elimina los viejos a mano:

```bash
docker stop leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker rm   leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker-compose up -d
```

---

## 10. Solución de problemas frecuentes

### ❌ `docker-compose: command not found` (WSL)

```bash
sudo apt install -y docker-compose-plugin
# O usar: docker compose up -d  (con espacio, sin guion)
```

### ❌ Backend no inicia — `Connection refused` a Keycloak

Keycloak todavía está inicializando. Esperar 30-60 segundos y reintentar. Verificar con:
```bash
curl -s http://localhost:8180/realms/master
# Si responde JSON, Keycloak está listo
```

### ❌ Backend no inicia — `Unable to connect to database`

La BD no está corriendo o las credenciales son incorrectas.
```bash
docker ps | grep leydata-db   # verificar que esté Up
psql -U admin -d leydata_db -h localhost -p 5433   # conectar directo
```

### ❌ `403 Forbidden` al llamar un endpoint con token válido

El usuario tiene token de Keycloak pero no tiene registro en la BD local, o está bloqueado/desactivado. Verificar:
1. Que exista un registro en `users` con el mismo email
2. Que `active = true` y `blocked = false`

### ❌ `"Missing form parameter: grant_type"` en Postman

El error ocurre cuando Postman no envía el `Content-Type` correcto. Hay dos causas posibles:

**Causa 1 — Header incorrecto activo:** hay un `Content-Type: application/json` marcado en la pestaña Headers. Desmarcarlo o eliminarlo.

**Causa 2 — Header correcto desmarcado:** al usar `x-www-form-urlencoded` en Body, Postman genera automáticamente la fila `Content-Type: application/x-www-form-urlencoded` en Headers, pero puede estar con el **checkbox desmarcado** (desactivado). Ir a Headers y activar esa fila.

**Alternativa que siempre funciona (método raw):**
- Body → **raw** → tipo **Text**
- En Headers agregar manualmente: `Content-Type: application/x-www-form-urlencoded`
- Contenido del body:
  ```
  grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!
  ```

### ❌ `Port 8080 already in use`

Otro proceso usa el puerto 8080. Identificarlo y cerrarlo:
```bash
# WSL / macOS
lsof -i :8080
kill -9 <PID>

# PowerShell
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### ❌ `Port 5433 already in use`

Hay otra instancia de PostgreSQL corriendo en ese puerto. Detenerla o cambiar el puerto en `docker-compose.yml` (ej: `"5434:5432"`).

### ❌ Docker Desktop no detecta WSL 2

En Docker Desktop → Settings → Resources → WSL Integration → activar Ubuntu → Apply & Restart.

### ❌ `JAVA_HOME` no encontrado (Windows)

```powershell
# Verificar dónde está instalado Java
where java

# Setear JAVA_HOME (ajustar ruta)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot", "Machine")
```
Cerrar y reabrir PowerShell.

### ❌ `POST /api/users` retorna 500 — "Unable to create user in Keycloak"

El backend no puede conectarse a Keycloak con permisos de administración. Causas posibles:

1. **`KC_BACKEND_SECRET` no configurado o incorrecto** — verificar que la variable de entorno tiene el Client Secret correcto del cliente `leydata-backend` (ver sección 6.6).
2. **La cuenta de servicio `leydata-backend` no tiene los roles necesarios** — ir a Keycloak → Clients → leydata-backend → Service account roles → verificar que estén asignados `manage-users` y `view-realm` del cliente `realm-management`.
3. **El cliente `leydata-backend` no existe** — ejecutar la sección 6.6 completa.

### ❌ `"Account is not fully set up"` — el usuario puede obtener token pero no puede usar el sistema

Error de Keycloak 26. El perfil del usuario tiene el campo `Last name` vacío. KC 26 lo requiere no vacío.

**Solución:**

1. Ir a Keycloak Admin → Users → seleccionar el usuario afectado → pestaña Details
2. Completar el campo **Last name** con cualquier valor no vacío → Save

**Para evitarlo en el futuro:**
- Siempre completar `First name` y `Last name` al crear usuarios en KC manualmente (sección 6.4)
- Al crear usuarios vía `POST /api/users`, el backend divide el campo `name` en firstName y lastName automáticamente. Enviar nombre con al menos una palabra — el backend usa la misma palabra para ambos si solo hay una.

### ❌ `GET /api/audit/logs/verify` retorna `"valid": false` sin que haya manipulación

Causa: Los logs de auditoría fueron creados con una versión anterior del código que no truncaba la precisión del timestamp a microsegundos. Java 21 en Linux genera timestamps con nanosegundos (9 decimales) pero PostgreSQL `timestamp(6)` trunca a microsegundos (6 decimales), causando que el hash guardado no coincida con el hash recalculado.

**Este bug está corregido en la versión actual del código.** Los logs generados después de la corrección se verifican correctamente. Los logs antiguos (generados antes del fix) tienen hashes inválidos permanentemente — no pueden corregirse.

Para limpiar logs viejos inválidos y empezar desde cero:
```sql
-- ⚠️ Esto elimina TODO el historial de auditoría
-- Requiere desactivar temporalmente los triggers de protección
TRUNCATE system_audit_log;
```

### ❌ El campo `actor_role` en los logs de auditoría muestra `"offline_access"` u otro rol técnico de Keycloak

Causa: El JWT de Keycloak incluye roles técnicos (`offline_access`, `uma_authorization`, `default-roles-leydata`) antes de los roles de negocio. Una versión anterior del código tomaba el primer rol sin filtrar.

**Este bug está corregido en la versión actual del código.** El backend filtra y toma el primer rol de negocio (`ADMIN`, `DPO`, `JEFE_DOMINIO`, `USER`, `TITULAR`).
