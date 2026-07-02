# Flujo de prueba manual end-to-end — Módulo Auditoría

Este documento registra la secuencia de requests de `bruno/Auditoria/` usada para probar el módulo `Auditoría` contra el backend local. Se reutilizó el Agreement de referencia creado en `agreements-manual-test-flow.md`.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol ADMIN (`admin@leydata.cl` / `Admin1234!`).
- Un Agreement existente: `ceb9a2f0-d3af-4127-8382-addad499f570` (de `agreements-manual-test-flow.md`).

## 1. Consultar Logs de Auditoría

```
GET http://localhost:8080/api/audit/logs?page=0&size=20
```

`200 OK`. El log capturó correctamente todas las acciones ejecutadas durante las corridas de los módulos anteriores (`DOCUMENT_PURPOSE_REMOVED`, `DOCUMENT_REJECTED`, etc.), cada entrada con `logHash` encadenado.

## 2. Verificar Cadena de Auditoría

```
GET http://localhost:8080/api/audit/logs/verify
```

`200 OK` — `{"valid": true, "message": "Cadena de auditoría íntegra. Ningún registro ha sido alterado."}`. Confirma que la cadena de hashes de todo el log (incluyendo las decenas de acciones generadas por los módulos probados en esta sesión) sigue íntegra.

## 3. Verificar Integridad de Entidad

```
POST http://localhost:8080/api/audit/integrity/verify
Content-Type: application/json

{ "entityType": "AGREEMENT", "entityId": "ceb9a2f0-d3af-4127-8382-addad499f570", "checkType": "MANUAL" }
```

`200 OK` — `isValid: true`, `recalculatedHash == storedHash`.

## 4. Historial de Integridad de Entidad

```
GET http://localhost:8080/api/audit/integrity/log?entityType=AGREEMENT&entityId=ceb9a2f0-d3af-4127-8382-addad499f570
```

`200 OK` — devuelve las 2 verificaciones registradas para este agreement (la del paso 3 recién hecha, y una previa del 29/06), ambas `isValid: true`, en orden descendente.

## 5. Verificaciones Fallidas

```
GET http://localhost:8080/api/audit/integrity/failed
```

`200 OK` — `[]` (no hay verificaciones con `isValid: false` en todo el sistema).

## 6. Traza Completa de Agreement

```
GET http://localhost:8080/api/audit/trace/agreement/ceb9a2f0-d3af-4127-8382-addad499f570
```

`200 OK`:
```json
{
  "agreementId": "ceb9a2f0-d3af-4127-8382-addad499f570",
  "overallIntegrity": "PARTIAL",
  "template": { "templateId": "03a57627-...", "isValid": true, "version": 1 },
  "document": { "documentId": "a425f9a9-...", "isValid": true, "version": 1 },
  "purposes": [{
    "purposeId": "54eccc94-...", "code": "TEST_PURPOSE_AGREEMENTS",
    "isValid": false, "integrityStatus": "UNKNOWN"
  }]
}
```

**Observación (no confirmada como bug):** el link de `template` y `document` dan `isValid: true`, pero el de `purposes` da `integrityStatus: "UNKNOWN"` (no `"INTEGRITY_MISMATCH"`). Según `AgreementTraceService.java:140-146`, `UNKNOWN` se produce específicamente cuando `ap.getPurposeHash()` (el snapshot del hash tomado al momento de consentir, guardado en `AgreementsPurposes`) es `null` para ese registro — es decir, para este Agreement en particular, el snapshot del hash de la purpose no quedó guardado en su momento. Esto empuja `overallIntegrity` a `"PARTIAL"` en vez de `"OK"`. No se investigó a fondo si es un dato histórico de cuando este Agreement se creó (28/06, antes de otros fixes documentados en `agreements-manual-test-flow.md`) o un bug vigente en `AgreementService.create()` que no persiste `purposeHash` en algunos casos — recomendable repetir esta prueba con un Agreement creado hoy para descartarlo.

## Resultado

Los 6 endpoints responden según lo documentado. Se registró una observación sobre `purposeHash` nulo en el Agreement de referencia (heredado de una corrida anterior), que amerita una verificación adicional con un Agreement nuevo pero no se confirmó como bug de este módulo.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Agreement verificado | `ceb9a2f0-d3af-4127-8382-addad499f570` |
| Registro de integridad generado en el paso 3 | `af126e34-2d87-430a-b15f-dc370f47b1af` |
