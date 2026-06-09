# Guía de Instalación — Ley Data

**Stack:** Java 21 · Spring Boot 4.0.6 · PostgreSQL 15 · Keycloak 26 · Docker  
**Repositorio:** https://github.com/SaMii0108/leydata

---

## Requisitos previos

| Herramienta | Versión | Verificar |
|---|---|---|
| Docker Desktop | 4.x | `docker --version` |
| Java 21 (JDK) | 21 | `java -version` |
| Git | cualquiera | `git --version` |

> En Windows usar **WSL 2 + Ubuntu**. Docker Desktop debe tener activada la integración con WSL (Settings → Resources → WSL Integration → Ubuntu ✅).

---

## 1. Clonar el repositorio

```bash
# WSL / macOS
cd ~
git clone https://github.com/SaMii0108/leydata.git
cd leydata
```

---

## 2. Levantar la infraestructura

```bash
cd ~/leydata
docker-compose up -d
```

Verificar que los tres contenedores estén `Up`:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

```
NAMES                         STATUS    PORTS
leydata-consent-db            Up        0.0.0.0:5433->5432/tcp
leydata-consent-keycloak-db   Up        5432/tcp
leydata-consent-keycloak      Up        0.0.0.0:8180->8080/tcp
```

> La primera vez que levanta `leydata-consent-keycloak` puede tardar 30-60 segundos. Esperar antes de continuar.

---

## 3. Configurar Keycloak (primera vez)

> Si el entorno ya fue configurado y el volumen `keycloak_data` existe, omitir este paso.

Ejecutar el script de configuración automática. Crea el realm, roles, clientes y usuario admin en un solo paso:

```bash
cd ~/leydata
bash scripts/setup-keycloak.sh
```

Al finalizar el script muestra el `KC_BACKEND_SECRET` generado. **Anotarlo** — se necesita en el paso 4.

### ¿No existe el script todavía? Ejecutar manualmente

```bash
# 1. Obtener token master admin
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "username=admin" \
  --data-urlencode "password=admin" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 2. Crear realm leydata
curl -s -o /dev/null -w "Realm: %{http_code}\n" -X POST "http://localhost:8180/admin/realms" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"realm":"leydata","enabled":true,"accessTokenLifespan":300,"ssoSessionMaxLifespan":1800}'

# 3. Crear roles
for ROLE in ADMIN DPO JEFE_DOMINIO; do
  curl -s -o /dev/null -w "Rol $ROLE: %{http_code}\n" \
    -X POST "http://localhost:8180/admin/realms/leydata/roles" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"name\":\"$ROLE\"}"
done

# 4. Crear cliente leydata-frontend (public)
curl -s -o /dev/null -w "leydata-frontend: %{http_code}\n" \
  -X POST "http://localhost:8180/admin/realms/leydata/clients" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"clientId":"leydata-frontend","enabled":true,"publicClient":true,
       "standardFlowEnabled":true,"directAccessGrantsEnabled":true,
       "redirectUris":["http://localhost:5173/*","http://localhost:3000/*","http://localhost:8080/*"],
       "webOrigins":["http://localhost:5173","http://localhost:3000","http://localhost:8080"]}'

# 5. Crear cliente leydata-backend (service account)
curl -s -o /dev/null -w "leydata-backend: %{http_code}\n" \
  -X POST "http://localhost:8180/admin/realms/leydata/clients" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"clientId":"leydata-backend","enabled":true,"publicClient":false,
       "serviceAccountsEnabled":true,"standardFlowEnabled":false,"directAccessGrantsEnabled":false}'

# 6. Asignar manage-users y view-realm al service account de leydata-backend
BACKEND_ID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-backend" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")
SA_ID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients/$BACKEND_ID/service-account-user" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
RM_ID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=realm-management" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")
MU=$(curl -s "http://localhost:8180/admin/realms/leydata/clients/$RM_ID/roles/manage-users" \
  -H "Authorization: Bearer $TOKEN")
VR=$(curl -s "http://localhost:8180/admin/realms/leydata/clients/$RM_ID/roles/view-realm" \
  -H "Authorization: Bearer $TOKEN")
curl -s -o /dev/null -w "Roles service account: %{http_code}\n" \
  -X POST "http://localhost:8180/admin/realms/leydata/users/$SA_ID/role-mappings/clients/$RM_ID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "[$MU,$VR]"

# 7. Crear usuario admin@leydata.cl
USER_ID=$(curl -s -X POST "http://localhost:8180/admin/realms/leydata/users" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@leydata.cl","firstName":"Administrador",
       "lastName":"LeyData","enabled":true,"emailVerified":true,
       "credentials":[{"type":"password","value":"Admin1234!","temporary":false}]}' \
  -D - | grep -i "^location:" | tr -d '\r' | awk -F'/' '{print $NF}')
ADMIN_ROLE=$(curl -s "http://localhost:8180/admin/realms/leydata/roles/ADMIN" \
  -H "Authorization: Bearer $TOKEN")
curl -s -o /dev/null -w "Rol ADMIN al admin: %{http_code}\n" \
  -X POST "http://localhost:8180/admin/realms/leydata/users/$USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "[$ADMIN_ROLE]"

# 8. Mostrar el KC_BACKEND_SECRET generado
echo ""
echo "========================================"
SECRET=$(curl -s "http://localhost:8180/admin/realms/leydata/clients/$BACKEND_ID/client-secret" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])")
echo "KC_BACKEND_SECRET=$SECRET"
echo "Copiarlo para usarlo en el paso siguiente."
echo "========================================"
```

---

## 4. Levantar el backend

```bash
# WSL / macOS
cd ~/leydata/backend
chmod +x mvnw   # solo la primera vez
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<secret_del_paso_3> ./mvnw spring-boot:run
```

```powershell
# PowerShell
cd C:\...\leydata\backend
$env:DB_USER="admin"; $env:DB_PASS="admin"; $env:DB_NAME="leydata_db"; $env:KC_BACKEND_SECRET="<secret_del_paso_3>"
.\mvnw.cmd spring-boot:run
```

**Señal de inicio exitoso:**
```
Started BackendApplication in X.XXX seconds
```

> El backend descarga automáticamente la configuración desde `http://localhost:8180/realms/leydata`. Keycloak debe estar corriendo **antes** de iniciar el backend.

---

## 5. Verificar que todo funciona

```bash
# Obtener token del admin
curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=leydata-frontend" \
  --data-urlencode "username=admin@leydata.cl" \
  --data-urlencode "password=Admin1234!"
```

Resultado esperado: JSON con `access_token`.

```bash
# Probar endpoint protegido
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer <access_token>"
```

Resultado esperado: `200 OK` con lista de usuarios.

**Swagger UI:** http://localhost:8080/swagger-ui.html  
**Keycloak Admin:** http://localhost:8180 (admin / admin)

---

## Comandos del día a día

```bash
# Levantar todo
docker-compose up -d
cd backend && DB_USER=admin DB_PASS=admin DB_NAME=leydata_db KC_BACKEND_SECRET=<secret> ./mvnw spring-boot:run

# Apagar contenedores (conserva datos)
docker-compose stop

# Reset completo — BORRA TODOS LOS DATOS (BD + Keycloak)
docker-compose down -v
rm -rf ./postgres_data
docker-compose up -d
# Requiere ejecutar el paso 3 (configuración Keycloak) nuevamente

# Ver logs
docker logs leydata-consent-keycloak --tail 50
docker logs leydata-consent-db --tail 50
```

---

## Solución de problemas

### ❌ Backend no arranca — `Unable to obtain configuration from issuer`

El realm `leydata` no existe en Keycloak. Ejecutar el paso 3 completo.

### ❌ `POST /api/users` retorna 500

La cuenta de servicio `leydata-backend` no tiene permisos o el `KC_BACKEND_SECRET` es incorrecto.
Verificar en Keycloak Admin → Clients → leydata-backend → Service account roles: deben aparecer `manage-users` y `view-realm` del cliente `realm-management`.

### ❌ `"Account is not fully set up"` al hacer login

El usuario tiene `lastName` vacío en Keycloak 26. Ir a Keycloak Admin → Users → seleccionar usuario → completar el campo **Last name** → Save.

Al crear usuarios con `POST /api/users`, el campo `name` debe tener nombre y apellido separados por espacio.

### ❌ Contenedor `leydata-consent-db` en bucle / crash

```
FATAL: database files are incompatible with server
```

Los archivos en `./postgres_data` fueron creados con una versión distinta de PostgreSQL. Opciones:
- **Sin perder datos:** no cambiar la versión de la imagen en `docker-compose.yml`
- **Aceptando pérdida de datos:** `docker-compose down -v && rm -rf ./postgres_data && docker-compose up -d`

### ❌ Migración de nombres de contenedores (equipo)

Si un compañero tenía los contenedores con los nombres anteriores (`leydata-keycloak`, `leydata-db`, `keycloak-db`), debe ejecutar **en este orden**:

```bash
# 1. Bajar primero (mientras el compose viejo aún conoce los nombres)
docker-compose down

# 2. Traer los cambios
git pull

# 3. Levantar con los nuevos nombres
docker-compose up -d
```

Si ya hizo `git pull` antes de bajar:
```bash
docker stop leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker rm   leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker-compose up -d
```

### ❌ `GET /api/audit/logs/verify` retorna `valid: false`

Los logs fueron creados antes de un fix de precisión de timestamp. Limpiar con `TRUNCATE system_audit_log` en la BD y generar nuevos logs.

### ❌ Puerto 8080 o 5433 ya en uso

```bash
# Ver qué proceso usa el puerto
lsof -i :8080    # WSL / macOS
netstat -ano | findstr :8080   # PowerShell

# Terminar el proceso
kill -9 <PID>    # WSL / macOS
taskkill /PID <PID> /F   # PowerShell
```
