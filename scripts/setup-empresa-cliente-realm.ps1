# setup-empresa-cliente-realm.ps1
# Crea el realm "empresa-cliente" en Keycloak local para simular el IdP externo del cliente.
#
# Uso (PowerShell, desde la raíz del proyecto):
#   .\scripts\setup-empresa-cliente-realm.ps1
#
# Qué hace:
#   1. Crea el realm "empresa-cliente"
#   2. Crea el cliente público "crm-sistema" (direct access grants)
#   3. Crea el usuario de prueba operador@empresa.cl / operador123
#   4. Muestra la JWKS URL y el curl de prueba
#
# Prerequisito: Keycloak corriendo en http://localhost:8180

$KC_URL     = "http://localhost:8180"
$REALM      = "empresa-cliente"
$CLIENT_ID  = "crm-sistema"
$TEST_USER  = "operador@empresa.cl"
$TEST_PASS  = "operador123"

Write-Host ""
Write-Host "=== Configurando realm '$REALM' en Keycloak ===" -ForegroundColor Cyan
Write-Host ""

# ── Esperar a que Keycloak esté listo ─────────────────────────────────────────
Write-Host "-> Esperando a que Keycloak este disponible en $KC_URL ..."
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$KC_URL/realms/master" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Write-Host "   Intento $i/30 - reintentando en 3 segundos..."
    Start-Sleep -Seconds 3
}
if (-not $ready) {
    Write-Host "ERROR: Keycloak no disponible. Verificar que el contenedor este corriendo." -ForegroundColor Red
    exit 1
}
Write-Host "   Keycloak listo." -ForegroundColor Green

# ── Obtener token de administrador ────────────────────────────────────────────
Write-Host "-> Obteniendo token de admin (realm master)..."
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
    Write-Host "   El realm '$REALM' ya existe. Continuando con los recursos internos..." -ForegroundColor Yellow
} catch {
    Write-Host "-> Creando realm '$REALM'..."
    $realmBody = @{
        realm   = $REALM
        enabled = $true
        displayName = "Empresa Cliente (IdP externo simulado)"
        accessTokenLifespan = 300
        loginWithEmailAllowed = $true
    } | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms" -Headers $headers -Body $realmBody -ErrorAction Stop | Out-Null
        Write-Host "   Realm creado." -ForegroundColor Green
    } catch {
        Write-Host "ERROR al crear realm: $_" -ForegroundColor Red
        exit 1
    }
}

# ── 2. Crear cliente público ───────────────────────────────────────────────────
Write-Host "-> Verificando cliente '$CLIENT_ID'..."
$existingClients = Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" `
    -Headers $headers
if ($existingClients.Count -gt 0) {
    Write-Host "   El cliente '$CLIENT_ID' ya existe. Omitiendo." -ForegroundColor Yellow
} else {
    Write-Host "-> Creando cliente '$CLIENT_ID'..."
    $clientBody = @{
        clientId                  = $CLIENT_ID
        enabled                   = $true
        publicClient              = $true
        directAccessGrantsEnabled = $true
        standardFlowEnabled       = $false
    } | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms/$REALM/clients" `
            -Headers $headers -Body $clientBody -ErrorAction Stop | Out-Null
        Write-Host "   Cliente '$CLIENT_ID' creado." -ForegroundColor Green
    } catch {
        Write-Host "ERROR al crear cliente: $_" -ForegroundColor Red
        exit 1
    }
}

# ── 3. Crear usuario de prueba ─────────────────────────────────────────────────
Write-Host "-> Verificando usuario '$TEST_USER'..."
$existingUsers = Invoke-RestMethod -Method Get `
    -Uri "$KC_URL/admin/realms/$REALM/users?username=$TEST_USER" `
    -Headers $headers
if ($existingUsers.Count -gt 0) {
    Write-Host "   El usuario '$TEST_USER' ya existe. Omitiendo." -ForegroundColor Yellow
} else {
    Write-Host "-> Creando usuario '$TEST_USER'..."
    $userBody = @{
        username    = $TEST_USER
        email       = $TEST_USER
        firstName   = "Operador"
        lastName    = "CRM"
        enabled     = $true
        credentials = @(@{
            type      = "password"
            value     = $TEST_PASS
            temporary = $false
        })
    } | ConvertTo-Json -Depth 5
    try {
        Invoke-RestMethod -Method Post -Uri "$KC_URL/admin/realms/$REALM/users" `
            -Headers $headers -Body $userBody -ErrorAction Stop | Out-Null
        Write-Host "   Usuario '$TEST_USER' creado." -ForegroundColor Green
    } catch {
        Write-Host "ERROR al crear usuario: $_" -ForegroundColor Red
        exit 1
    }
}

# ── Resumen ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "Realm '$REALM' listo." -ForegroundColor Green
Write-Host ""
Write-Host "JWKS URL (valor para EXTERNAL_JWKS_URI en docker-compose.override.yml):"
Write-Host "  $KC_URL/realms/$REALM/protocol/openid-connect/certs" -ForegroundColor White
Write-Host ""
Write-Host "Obtener token de prueba (pegar en Bruno / Postman / curl):"
Write-Host "  curl -s -X POST $KC_URL/realms/$REALM/protocol/openid-connect/token \" -ForegroundColor White
Write-Host "    -H 'Content-Type: application/x-www-form-urlencoded' \" -ForegroundColor White
Write-Host "    -d 'grant_type=password&client_id=$CLIENT_ID&username=$TEST_USER&password=$TEST_PASS'" -ForegroundColor White
Write-Host ""
Write-Host "Credenciales del usuario de prueba:"
Write-Host "  Email:    $TEST_USER"
Write-Host "  Password: $TEST_PASS"
Write-Host "====================================================================" -ForegroundColor Cyan
