#!/usr/bin/env bash
# setup-keycloak.sh — Configuración automática del realm leydata en Keycloak 26
#
# Uso:
#   bash scripts/setup-keycloak.sh
#
# Qué hace:
#   1. Crea el realm "leydata"
#   2. Crea los roles: ADMIN, DPO, JEFE_DOMINIO
#   3. Crea el cliente "leydata-frontend" (public, para el frontend)
#   4. Crea el cliente "leydata-backend" (confidential, service account)
#   5. Asigna manage-users + view-realm al service account de leydata-backend
#   6. Crea el usuario admin@leydata.cl con contraseña Admin1234! y rol ADMIN
#   7. Crea usuarios de prueba (dpo, jefe)
#   8. Crea el cliente "leydata-orchestrator" (M2M para el orquestador)
#   9. Muestra KC_BACKEND_SECRET y KC_ORCHESTRATOR_CLIENT_SECRET
#
# Prerequisito: Keycloak corriendo en http://localhost:8180

set -e

KC_URL="http://localhost:8180"
REALM="leydata"

echo ""
echo "=== Configurando Keycloak realm '$REALM' ==="
echo ""

# ── Esperar a que Keycloak esté listo ────────────────────────────────────────
echo "→ Esperando a que Keycloak esté disponible..."
# Keycloak 26 puede tardar más de 90s en arranque frío — esperamos hasta 3 minutos.
MAX_ATTEMPTS=60
for i in $(seq 1 $MAX_ATTEMPTS); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$KC_URL/realms/master" 2>/dev/null || echo "000")
  if [ "$STATUS" = "200" ]; then
    echo "  Keycloak listo."
    break
  fi
  echo "  Intento $i/$MAX_ATTEMPTS — esperando 3 segundos..."
  sleep 3
done

if [ "$STATUS" != "200" ]; then
  echo "ERROR: Keycloak no respondió en ${MAX_ATTEMPTS} intentos ($(( MAX_ATTEMPTS * 3 ))s)."
  echo "       Verificar que el contenedor esté corriendo: docker logs leydata-consent-keycloak --tail 30"
  exit 1
fi

# ── Obtener token de administrador ───────────────────────────────────────────
echo "→ Obteniendo token de admin..."
TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "username=admin" \
  --data-urlencode "password=admin" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: No se pudo obtener token. Verificar credenciales de Keycloak (admin/admin)."
  exit 1
fi

AUTH="-H \"Authorization: Bearer $TOKEN\""

# ── Verificar si el realm ya existe ──────────────────────────────────────────
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/$REALM")

if [ "$REALM_EXISTS" = "200" ]; then
  echo "  El realm '$REALM' ya existe. Omitiendo creación."
else
  # ── 1. Crear realm ───────────────────────────────────────────────────────
  echo "→ Creando realm '$REALM'..."
  curl -s -o /dev/null -w "  Status: %{http_code}\n" \
    -X POST "$KC_URL/admin/realms" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"realm\": \"$REALM\",
      \"enabled\": true,
      \"displayName\": \"Ley Data\",
      \"accessTokenLifespan\": 300,
      \"ssoSessionMaxLifespan\": 1800,
      \"loginWithEmailAllowed\": true,
      \"editUsernameAllowed\": true
    }"
fi

# Aplicar siempre (idempotente): permite al admin API actualizar username cuando cambia el email
echo "→ Actualizando configuración del realm..."
curl -s -o /dev/null -w "  Status: %{http_code}\n" \
  -X PUT "$KC_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"editUsernameAllowed\": true, \"loginWithEmailAllowed\": true}"

# ── 2. Crear roles ───────────────────────────────────────────────────────────
echo "→ Creando roles..."
for ROLE in ADMIN DPO JEFE_DOMINIO USER TITULAR; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$KC_URL/admin/realms/$REALM/roles" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"$ROLE\"}")
  if [ "$CODE" = "201" ]; then
    echo "  Rol $ROLE creado."
  elif [ "$CODE" = "409" ]; then
    echo "  Rol $ROLE ya existe, omitido."
  else
    echo "  Rol $ROLE: HTTP $CODE"
  fi
done

# ── 3. Crear cliente leydata-frontend ────────────────────────────────────────
echo "→ Creando cliente 'leydata-frontend'..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$KC_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "leydata-frontend",
    "enabled": true,
    "publicClient": true,
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true,
    "redirectUris": ["http://localhost:5173/*","http://localhost:3000/*","http://localhost:8080/*"],
    "webOrigins": ["http://localhost:5173","http://localhost:3000","http://localhost:8080"]
  }')
[ "$CODE" = "201" ] && echo "  Creado." || echo "  HTTP $CODE (puede que ya exista)"

# ── 4. Crear cliente leydata-backend ─────────────────────────────────────────
echo "→ Creando cliente 'leydata-backend' (service account)..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$KC_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "leydata-backend",
    "enabled": true,
    "publicClient": false,
    "serviceAccountsEnabled": true,
    "standardFlowEnabled": false,
    "directAccessGrantsEnabled": false
  }')
[ "$CODE" = "201" ] && echo "  Creado." || echo "  HTTP $CODE (puede que ya exista)"

# ── 5. Asignar roles al service account ──────────────────────────────────────
echo "→ Asignando permisos al service account de 'leydata-backend'..."

BACKEND_ID=$(curl -s "$KC_URL/admin/realms/$REALM/clients?clientId=leydata-backend" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

SA_ID=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$BACKEND_ID/service-account-user" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

RM_ID=$(curl -s "$KC_URL/admin/realms/$REALM/clients?clientId=realm-management" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

MU=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$RM_ID/roles/manage-users" \
  -H "Authorization: Bearer $TOKEN")
VR=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$RM_ID/roles/view-realm" \
  -H "Authorization: Bearer $TOKEN")

CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$KC_URL/admin/realms/$REALM/users/$SA_ID/role-mappings/clients/$RM_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$MU,$VR]")
[ "$CODE" = "204" ] && echo "  manage-users + view-realm asignados." || echo "  HTTP $CODE"

# ── 6. Crear usuario admin@leydata.cl ────────────────────────────────────────
echo "→ Creando usuario admin@leydata.cl..."
RESPONSE=$(curl -s -X POST "$KC_URL/admin/realms/$REALM/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "email": "admin@leydata.cl",
    "firstName": "Administrador",
    "lastName": "LeyData",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{"type":"password","value":"Admin1234!","temporary":false}]
  }' -D - -o /dev/null)

USER_ID=$(echo "$RESPONSE" | grep -i "^location:" | tr -d '\r' | awk -F'/' '{print $NF}')

if [ -z "$USER_ID" ]; then
  # El usuario ya existe — buscarlo
  USER_ID=$(curl -s "$KC_URL/admin/realms/$REALM/users?email=admin@leydata.cl" \
    -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id']) if d else print('')")
  [ -n "$USER_ID" ] && echo "  Usuario ya existía." || echo "  No se pudo obtener el ID del usuario."
else
  echo "  Usuario creado."
fi

# Asignar rol ADMIN
if [ -n "$USER_ID" ]; then
  ADMIN_ROLE=$(curl -s "$KC_URL/admin/realms/$REALM/roles/ADMIN" \
    -H "Authorization: Bearer $TOKEN")
  CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$KC_URL/admin/realms/$REALM/users/$USER_ID/role-mappings/realm" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "[$ADMIN_ROLE]")
  [ "$CODE" = "204" ] && echo "  Rol ADMIN asignado." || echo "  HTTP $CODE al asignar rol"
fi

# ── 7. Crear usuarios de prueba ──────────────────────────────────────────────
echo "→ Creando usuarios de prueba..."

create_user_with_role() {
  local USERNAME="$1"
  local EMAIL="$2"
  local FIRST="$3"
  local LAST="$4"
  local ROLE="$5"
  local PASSWORD="${6:-Test1234!}"

  RESPONSE=$(curl -s -X POST "$KC_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"$USERNAME\",
      \"email\": \"$EMAIL\",
      \"firstName\": \"$FIRST\",
      \"lastName\": \"$LAST\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{\"type\":\"password\",\"value\":\"$PASSWORD\",\"temporary\":false}]
    }" -D - -o /dev/null)

  KC_USER_ID=$(echo "$RESPONSE" | grep -i "^location:" | tr -d '\r' | awk -F'/' '{print $NF}')

  if [ -z "$KC_USER_ID" ]; then
    KC_USER_ID=$(curl -s "$KC_URL/admin/realms/$REALM/users?email=$EMAIL" \
      -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id']) if d else print('')")
    [ -n "$KC_USER_ID" ] && echo "  $EMAIL ya existía." || echo "  No se pudo obtener ID de $EMAIL."
  else
    echo "  $EMAIL creado."
  fi

  if [ -n "$KC_USER_ID" ]; then
    ROLE_JSON=$(curl -s "$KC_URL/admin/realms/$REALM/roles/$ROLE" \
      -H "Authorization: Bearer $TOKEN")
    CODE=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "$KC_URL/admin/realms/$REALM/users/$KC_USER_ID/role-mappings/realm" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "[$ROLE_JSON]")
    [ "$CODE" = "204" ] && echo "  Rol $ROLE asignado a $EMAIL." || echo "  HTTP $CODE al asignar $ROLE a $EMAIL"
  fi
}

create_user_with_role "dpo" "dpo@leydata.cl" "DPO" "LeyData" "DPO"
create_user_with_role "jefe" "jefe@test.cl" "Jefe" "Dominio" "JEFE_DOMINIO"

# ── 8. Crear cliente leydata-orchestrator (M2M) ─────────────────────────────
echo "→ Creando cliente 'leydata-orchestrator' (service account M2M)..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$KC_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "leydata-orchestrator",
    "enabled": true,
    "publicClient": false,
    "serviceAccountsEnabled": true,
    "standardFlowEnabled": false,
    "directAccessGrantsEnabled": false
  }')
[ "$CODE" = "201" ] && echo "  Creado." || echo "  HTTP $CODE (puede que ya exista)"

ORCH_ID=$(curl -s "$KC_URL/admin/realms/$REALM/clients?clientId=leydata-orchestrator" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

# ── 9. Mostrar los secrets ───────────────────────────────────────────────────
SECRET=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$BACKEND_ID/client-secret" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])")

ORCH_SECRET=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$ORCH_ID/client-secret" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])")

echo ""
echo "======================================================"
echo " Keycloak configurado correctamente."
echo ""
echo " Credenciales de acceso:"
echo "   Admin UI:  http://localhost:8180  (admin / admin)"
echo "   App admin:  admin@leydata.cl     / Admin1234!  (rol ADMIN)"
echo "   App DPO:    dpo@leydata.cl       / Test1234!   (rol DPO)"
echo "   App prueba: jefe@test.cl         / Test1234!   (rol JEFE_DOMINIO)"
echo ""
echo " Variables de entorno — copiar en .env:"
echo "   KC_BACKEND_SECRET=$SECRET"
echo "   KC_ORCHESTRATOR_CLIENT_SECRET=$ORCH_SECRET"
echo ""
echo " Arrancar el backend con:"
echo "   DB_USER=admin DB_PASS=admin DB_NAME=leydata_db \\"
echo "   KC_BACKEND_SECRET=$SECRET \\"
echo "   ./mvnw spring-boot:run"
echo "======================================================"
echo ""
