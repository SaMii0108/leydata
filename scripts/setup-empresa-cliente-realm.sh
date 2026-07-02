#!/bin/bash
# Crea el realm empresa-cliente en Keycloak local para simular el IdP externo del cliente.
# Ejecutar UNA sola vez después de que Keycloak esté corriendo.
#
# Uso:
#   bash scripts/setup-empresa-cliente-realm.sh
#   bash scripts/setup-empresa-cliente-realm.sh --domain-id <uuid>   # también agrega el mapper leydata_domain
#
# El claim 'leydata_domain' es requerido por POST /consent/capture.
# Para agregarlo, primero se necesita un UUID de dominio real en la BD.
# Si no se pasa --domain-id, el script imprime las instrucciones al final.

KC_URL="http://localhost:8180"
KC_ADMIN="admin"
KC_ADMIN_PASS="admin"
REALM="empresa-cliente"
CLIENT_ID="crm-sistema"
TEST_USER="operador@empresa.cl"
TEST_PASS="operador123"
DOMAIN_ID=""

# ── Parsear argumentos ────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain-id)
      DOMAIN_ID="$2"
      shift 2
      ;;
    *)
      echo "Argumento desconocido: $1"
      echo "Uso: bash $0 [--domain-id <uuid>]"
      exit 1
      ;;
  esac
done

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

# ── Agregar claim leydata_domain (si se proporcionó el domain-id) ──────────────
if [ -n "$DOMAIN_ID" ]; then
  echo ""
  echo "==> Agregando protocol mapper 'leydata_domain' al cliente $CLIENT_ID..."

  CRM_UUID=$(curl -s "$KC_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

  if [ -z "$CRM_UUID" ]; then
    echo "ERROR: No se pudo obtener el UUID interno del cliente $CLIENT_ID"
  else
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      "$KC_URL/admin/realms/$REALM/clients/$CRM_UUID/protocol-mappers/models" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"name\": \"leydata-domain-mapper\",
        \"protocol\": \"openid-connect\",
        \"protocolMapper\": \"oidc-hardcoded-claim-mapper\",
        \"config\": {
          \"claim.name\": \"leydata_domain\",
          \"claim.value\": \"$DOMAIN_ID\",
          \"jsonType.label\": \"String\",
          \"id.token.claim\": \"true\",
          \"access.token.claim\": \"true\",
          \"userinfo.token.claim\": \"false\"
        }
      }")

    if [ "$HTTP_STATUS" = "201" ]; then
      echo "Mapper agregado OK (domain-id: $DOMAIN_ID)"
    elif [ "$HTTP_STATUS" = "409" ]; then
      echo "El mapper ya existe (409) — actualizando valor..."
      # obtener el mapper existente y actualizar su claim.value
      MAPPER_ID=$(curl -s "$KC_URL/admin/realms/$REALM/clients/$CRM_UUID/protocol-mappers/models" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        | python3 -c "import sys,json; mappers=json.load(sys.stdin); m=next((x for x in mappers if x.get('name')=='leydata-domain-mapper'), None); print(m['id'] if m else '')")
      if [ -n "$MAPPER_ID" ]; then
        curl -s -X PUT \
          "$KC_URL/admin/realms/$REALM/clients/$CRM_UUID/protocol-mappers/models/$MAPPER_ID" \
          -H "Authorization: Bearer $ADMIN_TOKEN" \
          -H "Content-Type: application/json" \
          -d "{
            \"id\": \"$MAPPER_ID\",
            \"name\": \"leydata-domain-mapper\",
            \"protocol\": \"openid-connect\",
            \"protocolMapper\": \"oidc-hardcoded-claim-mapper\",
            \"config\": {
              \"claim.name\": \"leydata_domain\",
              \"claim.value\": \"$DOMAIN_ID\",
              \"jsonType.label\": \"String\",
              \"id.token.claim\": \"true\",
              \"access.token.claim\": \"true\",
              \"userinfo.token.claim\": \"false\"
            }
          }" && echo "Mapper actualizado OK"
      fi
    else
      echo "ERROR: HTTP $HTTP_STATUS al agregar el mapper"
    fi
  fi
fi

echo ""
echo "===================================================================="
echo "Realm '$REALM' listo."
echo ""
echo "JWKS URL (para EXTERNAL_JWKS_URI):"
echo "  $KC_URL/realms/$REALM/protocol/openid-connect/certs"
echo ""
echo "Obtener token de prueba (pegar en Bruno/Postman):"
echo "  curl -s -X POST $KC_URL/realms/$REALM/protocol/openid-connect/token \\"
echo "    -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "    -d 'grant_type=password&client_id=$CLIENT_ID&username=$TEST_USER&password=$TEST_PASS'"
echo ""

if [ -z "$DOMAIN_ID" ]; then
  echo "--------------------------------------------------------------------"
  echo "PENDIENTE: Agregar el claim 'leydata_domain' (requerido para /consent/capture)"
  echo ""
  echo "  1. Obtener un UUID de dominio del backend:"
  echo "     ADMIN_TOKEN=\$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \\"
  echo "       -H 'Content-Type: application/x-www-form-urlencoded' \\"
  echo "       -d 'grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!' \\"
  echo "       | python3 -c \"import sys,json; print(json.load(sys.stdin)['access_token'])\")"
  echo "     curl -s http://localhost:8080/api/domains \\"
  echo "       -H \"Authorization: Bearer \$ADMIN_TOKEN\" | python3 -m json.tool"
  echo ""
  echo "  2. Volver a correr este script pasando el UUID:"
  echo "     bash scripts/setup-empresa-cliente-realm.sh --domain-id <uuid-del-dominio>"
  echo ""
  echo "  O ver la sección 8 de GUIA-INSTALACION.md para el proceso manual."
  echo "--------------------------------------------------------------------"
fi

echo "===================================================================="
