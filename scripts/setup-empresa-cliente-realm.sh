#!/bin/bash
# Crea el realm empresa-cliente en Keycloak local para simular el IdP externo del cliente.
# Ejecutar UNA sola vez después de que Keycloak esté corriendo.
# Uso: bash scripts/setup-empresa-cliente-realm.sh

KC_URL="http://localhost:8180"
KC_ADMIN="admin"
KC_ADMIN_PASS="admin"
REALM="empresa-cliente"
CLIENT_ID="crm-sistema"
TEST_USER="operador@empresa.cl"
TEST_PASS="operador123"

echo "==> Obteniendo token de admin de Keycloak..."
ADMIN_TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=$KC_ADMIN&password=$KC_ADMIN_PASS" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: No se pudo obtener el token de admin. ¿Está Keycloak corriendo en $KC_URL?"
  exit 1
fi

echo "==> Creando realm $REALM..."
curl -s -X POST "$KC_URL/admin/realms" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"realm\": \"$REALM\", \"enabled\": true}" \
  && echo "Realm creado OK" || echo "El realm ya existe, continuando..."

echo "==> Creando cliente $CLIENT_ID en $REALM..."
curl -s -X POST "$KC_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"$CLIENT_ID\",
    \"enabled\": true,
    \"publicClient\": true,
    \"directAccessGrantsEnabled\": true,
    \"standardFlowEnabled\": false
  }" && echo "Cliente creado OK"

echo "==> Creando usuario de prueba $TEST_USER en $REALM..."
curl -s -X POST "$KC_URL/admin/realms/$REALM/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$TEST_USER\",
    \"email\": \"$TEST_USER\",
    \"enabled\": true,
    \"credentials\": [{\"type\": \"password\", \"value\": \"$TEST_PASS\", \"temporary\": false}]
  }" && echo "Usuario creado OK"

echo ""
echo "===================================================================="
echo "Realm '$REALM' listo."
echo ""
echo "JWKS URL (para EXTERNAL_JWKS_URI):
  http://localhost:8180/realms/$REALM/protocol/openid-connect/certs"
echo ""
echo "Obtener token de prueba (pegar en Bruno/Postman):
  curl -s -X POST $KC_URL/realms/$REALM/protocol/openid-connect/token \\
    -H 'Content-Type: application/x-www-form-urlencoded' \\
    -d 'grant_type=password&client_id=$CLIENT_ID&username=$TEST_USER&password=$TEST_PASS'"
echo "===================================================================="
