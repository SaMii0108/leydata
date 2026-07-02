# setup-empresa-cliente-realm.ps1
# Crea el realm "empresa-cliente" en Keycloak local para simular el IdP externo del cliente.
#
# Uso (PowerShell, desde la raíz del proyecto):
#   .\scripts\setup-empresa-cliente-realm.ps1
#   .\scripts\setup-empresa-cliente-realm.ps1 -DomainId "<uuid>"    # también agrega el mapper
#
# El claim 'leydata_domain' es requerido por POST /consent/capture.
# Para agregarlo se necesita un UUID de dominio real creado en el backend.
# Si no se pasa -DomainId, el script imprime las instrucciones al final.
#
# Prerequisito: Keycloak corriendo en http://localhost:8180

param(
    [string]$DomainId = ""
)

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
    $clientUUID = $existingClients[0].id
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
        # Obtener UUID del cliente recién creado
        $existingClients = Invoke-RestMethod -Method Get `
            -Uri "$KC_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" `
            -Headers $headers
        $clientUUID = $existingClients[0].id
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

# ── 4. Agregar claim leydata_domain (si se proporcionó DomainId) ───────────────
if ($DomainId -ne "") {
    Write-Host ""
    Write-Host "-> Configurando protocol mapper 'leydata_domain' (domain-id: $DomainId)..."

    $mapperBody = @{
        name           = "leydata-domain-mapper"
        protocol       = "openid-connect"
        protocolMapper = "oidc-hardcoded-claim-mapper"
        config         = @{
            "claim.name"         = "leydata_domain"
            "claim.value"        = $DomainId
            "jsonType.label"     = "String"
            "id.token.claim"     = "true"
            "access.token.claim" = "true"
            "userinfo.token.claim" = "false"
        }
    } | ConvertTo-Json -Depth 5

    try {
        Invoke-RestMethod -Method Post `
            -Uri "$KC_URL/admin/realms/$REALM/clients/$clientUUID/protocol-mappers/models" `
            -Headers $headers -Body $mapperBody -ErrorAction Stop | Out-Null
        Write-Host "   Mapper 'leydata_domain' agregado OK." -ForegroundColor Green
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 409) {
            Write-Host "   El mapper ya existe. Actualizando valor..." -ForegroundColor Yellow
            $existingMappers = Invoke-RestMethod -Method Get `
                -Uri "$KC_URL/admin/realms/$REALM/clients/$clientUUID/protocol-mappers/models" `
                -Headers $headers
            $mapper = $existingMappers | Where-Object { $_.name -eq "leydata-domain-mapper" }
            if ($mapper) {
                $mapperBody2 = @{
                    id             = $mapper.id
                    name           = "leydata-domain-mapper"
                    protocol       = "openid-connect"
                    protocolMapper = "oidc-hardcoded-claim-mapper"
                    config         = @{
                        "claim.name"         = "leydata_domain"
                        "claim.value"        = $DomainId
                        "jsonType.label"     = "String"
                        "id.token.claim"     = "true"
                        "access.token.claim" = "true"
                        "userinfo.token.claim" = "false"
                    }
                } | ConvertTo-Json -Depth 5
                Invoke-RestMethod -Method Put `
                    -Uri "$KC_URL/admin/realms/$REALM/clients/$clientUUID/protocol-mappers/models/$($mapper.id)" `
                    -Headers $headers -Body $mapperBody2 -ErrorAction Stop | Out-Null
                Write-Host "   Mapper actualizado OK." -ForegroundColor Green
            }
        } else {
            Write-Host "ERROR al agregar mapper: $_" -ForegroundColor Red
        }
    }
}

# ── Resumen ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "Realm '$REALM' listo." -ForegroundColor Green
Write-Host ""
Write-Host "JWKS URL (valor para EXTERNAL_JWKS_URI):"
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

if ($DomainId -eq "") {
    Write-Host ""
    Write-Host "--------------------------------------------------------------------" -ForegroundColor Yellow
    Write-Host "PENDIENTE: Agregar el claim 'leydata_domain' (requerido para /consent/capture)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  1. Obtener un UUID de dominio desde el backend (necesita el backend corriendo):"
    Write-Host "     Ver GET /api/domains en Swagger: http://localhost:8080/swagger-ui.html"
    Write-Host ""
    Write-Host "  2. Volver a correr este script con el UUID:"
    Write-Host "     .\scripts\setup-empresa-cliente-realm.ps1 -DomainId `"<uuid-del-dominio>`"" -ForegroundColor White
    Write-Host ""
    Write-Host "  O ver la seccion 8 de GUIA-INSTALACION.md para el proceso manual."
    Write-Host "--------------------------------------------------------------------" -ForegroundColor Yellow
}

Write-Host "====================================================================" -ForegroundColor Cyan
