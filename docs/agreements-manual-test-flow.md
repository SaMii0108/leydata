# Flujo de prueba manual end-to-end — Módulo Agreements

Este documento registra la secuencia exacta de requests usada para probar el módulo `Agreements` contra el backend local, partiendo de una base de datos vacía. Sirve como guía repetible para volver a probar el módulo después de un `docker-compose down -v` o de cualquier reseteo de datos.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Keycloak corriendo en `http://localhost:8180`, realm `leydata` configurado (ver `scripts/setup-keycloak.ps1`).
- Usuarios de prueba ya creados: `admin@leydata.cl` / `Admin1234!` (rol ADMIN), `dpo@leydata.cl` / `Test1234!` (rol DPO).
- El orquestador ya está implementado. `DATA_SUBJECTS` se crean automáticamente vía `findOrCreate` en `AgreementService` cuando se envía `subjectIdentifier` en el body. No se necesita ningún endpoint de prueba.

## 1. Obtener token (Keycloak)

```
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=leydata-frontend
username=admin@leydata.cl
password=Admin1234!
```

El token dura 5 minutos (`expires_in: 300`). Para los pasos que requieren rol DPO específicamente (documentos de privacidad), pedir un token aparte con `username=dpo@leydata.cl` / `password=Test1234!`.

Todas las requests siguientes usan `Authorization: Bearer {token}`.

## 2. Nota sobre Data Subjects

No es necesario crear el `DataSubject` manualmente. Al enviar `"subjectIdentifier"` en el body del agreement (paso 12), `AgreementService` hace un `findOrCreate` automático: si el identificador ya existe lo reutiliza, si no lo crea. El endpoint `POST /api/test/data-subjects` fue eliminado.

## 3. Crear Dominio (rol ADMIN)

```
POST http://localhost:8080/api/domains
Content-Type: application/json

{
  "code": "test",
  "name": "Dominio de Prueba",
  "description": "Dominio creado para pruebas de Agreements"
}
```

`jefeId` es opcional, se omite. Guardar `domainId` de la respuesta.

## 4. Crear Finalidad / Purpose (rol DPO o ADMIN)

```
POST http://localhost:8080/api/purposes
Content-Type: application/json

{
  "code": "TEST_PURPOSE_AGREEMENTS",
  "name": "Finalidad de prueba para Agreements",
  "description": "Finalidad creada para probar el flujo de Agreements end-to-end",
  "shortDescription": "Prueba Agreements",
  "required": false,
  "revocable": true,
  "presentationOrder": 1,
  "legalBasisId": "<id real de legal_basis_catalog, ej. CONSENTIMIENTO>",
  "domainId": "<domainId del paso 3>",
  "consentStatement": "Acepto el uso de mis datos para esta finalidad de prueba."
}
```

`legalBasisId` debe existir en `legal_basis_catalog` (sembrado por `CatalogSeeder` al levantar el backend — códigos: `CONSENTIMIENTO`, `CONTRATO`, `OBLIGACION_LEGAL`, `INTERES_VITAL`, `INTERES_PUBLICO`, `INTERES_LEGITIMO`). Guardar `purposeId`.

## 5. Crear Template en DRAFT (rol DPO o ADMIN)

```
POST http://localhost:8080/api/templates
Content-Type: application/json

{
  "templateKey": "TEST_TEMPLATE_AGREEMENTS",
  "name": "Template de prueba para Agreements",
  "description": "Template creado para probar el flujo de Agreements end-to-end",
  "title": "Consentimiento de prueba"
}
```

Guardar `templateId`.

## 6. Vincular Purpose al Template (solo DRAFT)

```
POST http://localhost:8080/api/templates/{templateId}/purposes
Content-Type: application/json

{
  "purposeId": "<purposeId del paso 4>",
  "orderPosition": 1,
  "isVisible": true
}
```

Respuesta `204 No Content`. `isVisible: true` es obligatorio — el `create` de Agreements valida que el set de purposes del request coincida exactamente con las purposes **visibles** del template.

## 7. Aprobar el Template

```
POST http://localhost:8080/api/templates/{templateId}/approve
```

Status pasa de `DRAFT` a `APPROVED`.

## 8. Activar el Template

```
POST http://localhost:8080/api/templates/{templateId}/activate
```

Status pasa a `ACTIVE`, `isActive: true`. Solo se puede crear un Agreement contra un template `ACTIVE` (Regla 2).

## 9. Crear Documento de Privacidad en DRAFT (rol DPO obligatorio)

```
POST http://localhost:8080/api/privacy-documents
Content-Type: application/json

{
  "category": "MARKETING_DIRECTO",
  "name": "Documento de prueba para Agreements",
  "content": "Este documento describe las condiciones de tratamiento de datos para pruebas del modulo Agreements.",
  "templateId": "<templateId del paso 5>"
}
```

`category` debe ser uno de: `POLITICA_PRIVACIDAD`, `AVISO_COOKIES`, `DATOS_SENSIBLES`, `MARKETING_DIRECTO`, `MENORES_EDAD`, `TRANSFERENCIA_TERCEROS`. Guardar `documentId` (= `id` de la respuesta).

Importante: este endpoint exige rol **DPO** específicamente vía `@PreAuthorize`; un token con solo rol ADMIN recibe `403`.

## 10. Vincular Purpose al Documento (solo DRAFT)

```
POST http://localhost:8080/api/privacy-documents/{documentId}/purposes/{purposeId}
```

Respuesta `204`. El `create` de Agreements valida (Regla 16) que el documento cubra, vía `DOCUMENT_PURPOSES`, todas las purposes del request.

## 11. Workflow del documento: DRAFT → IN_REVIEW → APPROVED → PUBLISHED

```
POST http://localhost:8080/api/privacy-documents/{documentId}/submit    → IN_REVIEW
POST http://localhost:8080/api/privacy-documents/{documentId}/approve   → APPROVED
POST http://localhost:8080/api/privacy-documents/{documentId}/publish   → PUBLISHED (genera PDF + hash SHA-256)
```

## 12. Crear el Agreement

```
POST http://localhost:8080/api/agreements
Content-Type: application/json

{
  "subjectIdentifier": "test-titular-001",
  "templateId": "<paso 5>",
  "documentId": "<paso 9>",
  "purposes": [
    { "purposeId": "<paso 4>", "accepted": true }
  ],
  "metadata": {
    "captureChannel": "WEB",
    "signatureToken": "test-token-123",
    "authProvider": "KEYCLOAK",
    "extraVariables": "{}"
  }
}
```

`ipOrigin` y `userAgent` no se envían en el body — los captura el controller automáticamente desde `request.getRemoteAddr()` y el header `User-Agent`.

Respuesta `201` con el `Agreement` completo: `id`, `status: ACTIVE`, `hashSha256`, `purposes[]` con su propio hash de cadena, y `metadata` con `ipOrigin` ya en formato canónico.

## 13. Verificar el resto de los endpoints

```
GET  http://localhost:8080/api/agreements/{id}
GET  http://localhost:8080/api/agreements?dataSubjectId=...&templateId=...&status=...
GET  http://localhost:8080/api/agreements/active?dataSubjectId=...&templateId=...
POST http://localhost:8080/api/agreements/{id}/verify-integrity     body: { "checkType": "MANUAL" }
GET  http://localhost:8080/api/agreements/{id}/integrity-log
GET  http://localhost:8080/api/agreements/integrity-log/failed
```

## IDs usados en la corrida de referencia (28/06/2026)

| Recurso | ID |
|---|---|
| Data Subject | `d624374d-a2be-49ba-97f5-0a3b44a7f363` |
| Dominio | `03669444-1409-426a-9c27-be501921524b` |
| Purpose | `54eccc94-cf47-43b3-8558-2a6d7237f615` |
| Template | `03a57627-1c83-4bd5-b005-9a9da3d54eb8` |
| Documento | `a425f9a9-8092-4080-adc8-2c2ee47366ce` |
| Agreement (verificación exitosa) | `ceb9a2f0-d3af-4127-8382-addad499f570` |

## Bugs encontrados y corregidos durante esta corrida

1. **`AgreementMetadata.extraVariables`** — columna `jsonb`, faltaba `@JdbcTypeCode(SqlTypes.JSON)`. Sin esto, el insert fallaba con `column "extra_variables" is of type jsonb but expression is of type character varying`.
2. **`AgreementMetadata.ipOrigin`** — columna `inet`, faltaba `@ColumnTransformer(write = "?::inet")`. Sin esto, el insert fallaba con `column "ip_origin" is of type inet but expression is of type character varying`.
3. **`verify-integrity` siempre devolvía `isValid: false`** en agreements recién creados, por dos causas combinadas:
   - `LocalDateTime.now()` tiene precisión de nanosegundos, pero la columna es `timestamp(6)` (microsegundos) — Postgres trunca al guardar, y el hash en memoria (sin truncar) no coincidía con el hash recalculado tras releer de la BD. Fix: truncar a microsegundos antes de usar el timestamp (`LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)`).
   - Postgres normaliza el valor `inet` (ej. `0:0:0:0:0:0:0:1` → `::1`), pero Java's `InetAddress.getHostAddress()` no comprime IPv6 de la misma forma, así que normalizar en memoria tampoco alcanzaba. Fix: forzar `flush()` + `entityManager.refresh(savedMetadata)` después de guardar la metadata, para que el hash de creación use el valor ya normalizado por la BD.

Todos los fixes están en `AgreementMetadata.java` y `AgreementService.java`.
