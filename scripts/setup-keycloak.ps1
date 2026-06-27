# setup-keycloak.ps1 — Configuración automática del realm leydata en Keycloak 26
#
# Uso (PowerShell, desde la raíz del proyecto):
#   .\scripts\setup-keycloak.ps1
#
# Qué hace:
#   1. Crea el realm "leydata"
#   2. Crea los roles: ADMIN, DPO, JEFE_DOMINIO, USER, TITULAR
#   3. Crea el cliente "leydata-frontend" (public, para el frontend)
#   4. Crea el cliente "leydata-backend" (confidential, service account)
#   5. Asigna manage-users + view-realm al service account de leydata-backend
#   6. Crea el usuario admin@leydata.cl con contraseña Admin1234! y rol ADMIN
#   7. Muestra el KC_BACKEND_SECRET generado
#
# Prerequisito: Keycloak corriendo en http://localhost:8180

$KC_URL = "http://localhost:8180"
$REALM   = "leydata"

Write-Host ""
Write-Host "=== Configurando Keycloak realm '$REALM' ===" -ForegroundColor Cyan
Write-Host ""

# ── Esperar a que Keycloak esté listo ─────────────────────────────────────────
Write-Host "-> Esperando a que Keycloak este disponible..."
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$KC_URL/realms/master" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host "   Intento $i/30 - esperando 3 segundos..."
    Start-Sleep -Seconds 3
}
if (-not $ready) {
    Write-Host "ERROR: Keycloak no disponible en $KC_URL. Verificar que el contenedor este corriendo." -ForegroundColor Red
    exit 1
}
Write-Host "   Keycloak listo."

# ── Obtener token de administrador ────────────────────────────────────────────
Write-Host "-> Obteniendo token de admin..."
$tokenBody = @{
    grant_type = "password"
    client_id  = "admin-cli"
    username   = "admin"
    password   = "admin"
}
try {
    $tokenResp = Invoke-RestMethod -Method Post `
        -Uri "$KC_URL/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $tokenBody -ErrorAction Stop
    $TOKEN = $tokenResp.access_token
} catch {
    Write-Host "ERROR: No se pudo obtener token. Verificar credenciales de Keycloak (admin/admin)." -ForegroundColor Red
    exit 1
}

$headers = @{ Authorization = "Bearer $TOKEN"; "Content-Type" = "application/json" }

# ── 1. Crear realm ────────────────────────────────────────────────────────────
Write-Host "-> Verificando realm '$REALM'..."
try {
    Invoke-RestMethod -Method Get -Uri "$KC_URL/admin/realms/$REALM" -Headers $headers -ErrorAction Stop | Out-Null
    Write-Host "   El realm '$REALM' ya existe. Omitiendo."
} catch {
    Write-Host "-> Creando realm '$REALM'..."
    $realmBody = @{
        realm                 = $REALM
        enabled               = $true
        displayName           = "Ley Data"
        accessTokenLifespan   = 300
        ssoSessionMaxLifespan = 1800
        loginWithEmailAllowed = $true
        editUsernameAllowed   = $true
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms" -Headers $headers -Body $realmBody | Out-Null
    Write-Host "   Realm creado."
}

# Aplicar siempre (idempotente): permite al admin API actualizar username cuando cambia el email
Write-Host "-> Actualizando configuracion del realm..."
$realmPatch = @{ editUsernameAllowed = $true; loginWithEmailAllowed = $true } | ConvertTo-Json
try {
    Invoke-RestMethod -Method Put -Uri "$KC_URL/admin/realms/$REALM" `
        -Headers $headers -Body $realmPatch -ErrorAction Stop | Out-Null
    Write-Host "   Configuracion actualizada."
} catch {
    Write-Host "   HTTP $($_.Exception.Response.StatusCode.value__)"
}

# ── 2. Crear roles ────────────────────────────────────────────────────────────
Write-Host "-> Creando roles..."
foreach ($ROLE in @("ADMIN","DPO","JEFE_DOMINIO","USER","TITULAR")) {
    try {
        Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms/$REALM/roles" `
            -Headers $headers -Body (@{ name = $ROLE } | ConvertTo-Json) -ErrorAction Stop | Out-Null
        Write-Host "   Rol $ROLE creado."
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 409) {
            Write-Host "   Rol {$ROLE} ya existe, omitido."
        } else {
            Write-Host "   Rol {$ROLE}: $($_.Exception.Message)"
        }
    }
}

# ── 3. Crear cliente leydata-frontend ─────────────────────────────────────────
Write-Host "-> Creando cliente 'leydata-frontend'..."
$frontendBody = @{
    clientId                = "leydata-frontend"
    enabled                 = $true
    publicClient            = $true
    standardFlowEnabled     = $true
    directAccessGrantsEnabled = $true
    redirectUris            = @("http://localhost:5173/*","http://localhost:3000/*","http://localhost:8080/*")
    webOrigins              = @("http://localhost:5173","http://localhost:3000","http://localhost:8080")
} | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms/$REALM/clients" `
        -Headers $headers -Body $frontendBody -ErrorAction Stop | Out-Null
    Write-Host "   Cliente 'leydata-frontend' creado."
} catch {
    Write-Host "   HTTP $($_.Exception.Response.StatusCode.value__) (puede que ya exista)"
}

# ── 4. Crear cliente leydata-backend ──────────────────────────────────────────
Write-Host "-> Creando cliente 'leydata-backend' (service account)..."
$backendBody = @{
    clientId                = "leydata-backend"
    enabled                 = $true
    publicClient            = $false
    serviceAccountsEnabled  = $true
    standardFlowEnabled     = $false
    directAccessGrantsEnabled = $false
} | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms/$REALM/clients" `
        -Headers $headers -Body $backendBody -ErrorAction Stop | Out-Null
    Write-Host "   Cliente 'leydata-backend' creado."
} catch {
    Write-Host "   HTTP $($_.Exception.Response.StatusCode.value__) (puede que ya exista)"
}

# ── 5. Asignar permisos al service account ────────────────────────────────────
Write-Host "-> Asignando permisos al service account de 'leydata-backend'..."

$BACKEND_ID = (Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients?clientId=leydata-backend" `
    -Headers $headers)[0].id

$SA_ID = (Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients/$BACKEND_ID/service-account-user" `
    -Headers $headers).id

$RM_ID = (Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients?clientId=realm-management" `
    -Headers $headers)[0].id

$MU = Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients/$RM_ID/roles/manage-users" -Headers $headers
$VR = Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients/$RM_ID/roles/view-realm" -Headers $headers

$rolesBody = @($MU, $VR) | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post `
        -Uri "$KC_URL/admin/realms/$REALM/users/$SA_ID/role-mappings/clients/$RM_ID" `
        -Headers $headers -Body $rolesBody -ErrorAction Stop | Out-Null
    Write-Host "   manage-users + view-realm asignados."
} catch {
    Write-Host "   HTTP $($_.Exception.Response.StatusCode.value__)"
}

# ── 6. Crear usuario admin@leydata.cl ─────────────────────────────────────────
Write-Host "-> Creando usuario admin@leydata.cl..."
$adminBody = @{
    username        = "admin"
    email           = "admin@leydata.cl"
    firstName       = "Administrador"
    lastName        = "LeyData"
    enabled         = $true
    emailVerified   = $true
    requiredActions = @()
    credentials     = @(@{ type = "password"; value = "Admin1234!"; temporary = $false })
} | ConvertTo-Json -Depth 5

$USER_ID = $null
try {
    # Crear y capturar el header Location para extraer el ID
    $resp = Invoke-WebRequest -Method Post `
        -Uri "$KC_URL/admin/realms/$REALM/users" `
        -Headers $headers -Body $adminBody -UseBasicParsing -ErrorAction Stop
    $location = $resp.Headers["Location"]
    $USER_ID  = $location -replace ".*/",""
    Write-Host "   Usuario creado."
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 409) {
        Write-Host "   Usuario ya existia."
        $users = Invoke-RestMethod -Method Get `
            -Uri "$KC_URL/admin/realms/$REALM/users?email=admin@leydata.cl" -Headers $headers
        if ($users.Count -gt 0) { $USER_ID = $users[0].id }
    } else {
        Write-Host "   Error al crear usuario: $($_.Exception.Message)"
    }
}

if ($USER_ID) {
    $ADMIN_ROLE = Invoke-RestMethod -Method Get `
        -Uri "$KC_URL/admin/realms/$REALM/roles/ADMIN" -Headers $headers
    try {
        Invoke-RestMethod -Method Post `
            -Uri "$KC_URL/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" `
            -Headers $headers -Body (@($ADMIN_ROLE) | ConvertTo-Json -Depth 5) -ErrorAction Stop | Out-Null
        Write-Host "   Rol ADMIN asignado."
    } catch {
        Write-Host "   HTTP $($_.Exception.Response.StatusCode.value__) al asignar rol"
    }
}

# ── 7. Mostrar KC_BACKEND_SECRET ──────────────────────────────────────────────
$SECRET = (Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients/$BACKEND_ID/client-secret" `
    -Headers $headers).value

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host " Keycloak configurado correctamente." -ForegroundColor Green
Write-Host ""
Write-Host " Credenciales de acceso:"
Write-Host "   Admin UI:  http://localhost:8180  (admin / admin)"
Write-Host "   App admin: admin@leydata.cl / Admin1234!"
Write-Host ""
Write-Host " Variable de entorno para el backend:"
Write-Host "   KC_BACKEND_SECRET=$SECRET" -ForegroundColor Yellow
Write-Host ""
Write-Host " Arrancar el backend (PowerShell):"
Write-Host "   `$env:DB_USER='admin'"
Write-Host "   `$env:DB_PASS='admin'"
Write-Host "   `$env:DB_NAME='leydata_db'"
Write-Host "   `$env:KC_BACKEND_SECRET='$SECRET'"
Write-Host "   .\mvnw.cmd spring-boot:run"
Write-Host "======================================================" -ForegroundColor Green
Write-Host ""
