# Módulo: Agreements (Acuerdos de Consentimiento)

## Descripción

El módulo de Agreements registra el acto de consentimiento real de un titular de datos (DATA_SUBJECT). Es el resultado de presentarle un TEMPLATE: por cada PURPOSE visible en ese template, el titular acepta o rechaza explícitamente, y ese detalle queda congelado como snapshot inmutable en AGREEMENTS_PURPOSES.

Un AGREEMENT es evidencia legal: una vez creado no se edita. Cualquier cambio de voluntad del titular (revocar, renovar) genera un nuevo registro, nunca una modificación del existente. La integridad se protege con HASH_SHA256, calculado por el service al crear el acuerdo y revalidado por la base de datos (función `GENERATE_AGREEMENT_HASH`) y por verificaciones registradas en AGREEMENT_INTEGRITY_LOG.

> **Estado de este documento:** cubre análisis y diseño. No se ha construido código de este módulo en esta sesión — el `STATUS` (REVOKED/EXPIRED) depende de un orquestador que aún no existe, y el flujo de creación de DATA_SUBJECTS está pendiente de definir.

---

## Reglas de negocio

1. Un AGREEMENT es inmutable una vez creado: no se actualizan sus columnas de negocio (solo se inserta).
2. Un AGREEMENT solo puede crearse contra un TEMPLATE en estado `ACTIVE` (no se puede consentir sobre un template `DRAFT` o `APPROVED`).
3. `TEMPLATE_VERSION` debe coincidir con la versión del `TEMPLATE_ID` referenciado al momento de la creación (snapshot de qué versión vio el titular).
4. Por cada PURPOSE visible (`IS_VISIBLE = TRUE`) del template, debe crearse exactamente un registro en `AGREEMENTS_PURPOSES`. No puede faltar ninguna ni duplicarse (única por `AGREEMENT_ID` + `PURPOSE_ID`).
5. Si una PURPOSE es `REQUIRED = TRUE`, no puede registrarse `ACCEPTED = FALSE` para ella (constraint de BD). Esto es coherente con la regla de Templates de que una purpose `REQUIRED` no puede asociarse a un template de base "consentimiento".
6. `AGREEMENTS_PURPOSES` guarda un snapshot de los atributos de la purpose al momento del consentimiento (código, nombre, descripción, required, revocable, hash) — no se lee en vivo desde `PURPOSES`, para que cambios posteriores en la purpose no alteren acuerdos ya firmados.
7. `PURPOSE_HASH` en el snapshot debe coincidir con el `HASH_SHA256` de la purpose original al momento de la creación del agreement (permite detectar alteraciones).
8. `STATUS` inicia siempre en `ACTIVE`. El tránsito a `REVOKED` o `EXPIRED` está fuera del alcance de este documento: corresponde a un orquestador/job aún no diseñado.
9. `PREVIOUS_AGREEMENTS_ID` enlaza un agreement con el que reemplaza (renovación o cambio de voluntad), formando una cadena histórica. La definición de cuándo se genera esa cadena también depende del orquestador pendiente.
10. `HASH_SHA256` del AGREEMENT se calcula dos veces como doble resguardo: el service lo calcula y persiste al crear el agreement (incluye sus AGREEMENTS_PURPOSES); la base de datos expone `GENERATE_AGREEMENT_HASH(agreement_id)` para recalcularlo y compararlo en verificaciones de integridad.
11. Toda verificación de integridad (programada, manual o bajo demanda) sobre un AGREEMENT debe registrar un `AGREEMENT_INTEGRITY_LOG`, con el hash almacenado, el recalculado, si coinciden (`IS_VALID`) y el tipo de chequeo.
12. Un DATA_SUBJECT debe existir previamente a la creación del AGREEMENT — el flujo de creación/registro de DATA_SUBJECTS aún no está definido (pendiente, ver sección "Preguntas abiertas").
13. No se puede eliminar (`DELETE`) un AGREEMENT — el `ON DELETE RESTRICT` en `TEMPLATE_ID` y `DOCUMENT_ID` protege la cadena de integridad; solo `DATA_SUBJECT_ID` tiene `ON DELETE CASCADE` (al eliminar el titular, se elimina su historial).

---

## Estados

```
ACTIVE → REVOKED
ACTIVE → EXPIRED
```

- **ACTIVE**: consentimiento vigente.
- **REVOKED**: el titular (o un proceso interno) retiró el consentimiento. Disparador pendiente de diseño (orquestador).
- **EXPIRED**: venció `EXPIRATION`. Disparador pendiente de diseño (orquestador/job).

---

## Casos de uso (propuestos para una futura iteración de implementación)

### Consulta y creación
1. Crear AGREEMENT (titular acepta/rechaza cada purpose visible del template activo) — genera AGREEMENTS_PURPOSES y calcula HASH_SHA256.
2. Obtener AGREEMENT por ID (incluye sus AGREEMENTS_PURPOSES).
3. Listar AGREEMENTS de un DATA_SUBJECT (historial, incluye cadena vía PREVIOUS_AGREEMENTS_ID).
4. Listar AGREEMENTS por TEMPLATE_ID / STATUS (filtros administrativos).

### Integridad
5. Verificar integridad de un AGREEMENT bajo demanda (recalcula hash, compara, registra en AGREEMENT_INTEGRITY_LOG).
6. Listar verificaciones fallidas (`IS_VALID = FALSE`) para investigación.

### Fuera de alcance por ahora (dependen del orquestador)
- Revocar AGREEMENT.
- Expirar AGREEMENT automáticamente.
- Renovar AGREEMENT (crear nuevo enlazado vía PREVIOUS_AGREEMENTS_ID).

---

## Endpoints propuestos

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/agreements` | Crear agreement con el detalle de purposes aceptadas/rechazadas |
| `GET` | `/api/agreements/{id}` | Obtener por ID con su detalle de purposes |
| `GET` | `/api/agreements` | Listar (filtros: `dataSubjectId`, `templateId`, `status`) |
| `POST` | `/api/agreements/{id}/verify-integrity` | Verificar integridad bajo demanda, registra `AGREEMENT_INTEGRITY_LOG` |
| `GET` | `/api/agreements/{id}/integrity-log` | Historial de verificaciones de integridad del agreement |

> No se incluyen endpoints de revocación/expiración/renovación: dependen del orquestador no definido aún.

---

## Preguntas abiertas (pendientes de definición antes de planear código)

1. **Alta de DATA_SUBJECTS**: ¿se crea en un endpoint propio (`POST /api/data-subjects`) antes de poder consentir, o se resuelve de otra forma? Mencionaste que "debería ya existir al momento de solicitar consentimiento" — falta decidir el mecanismo concreto.
2. **Orquestador de REVOKED/EXPIRED/renovación**: falta diseñar quién lo dispara (job, evento externo, endpoint del titular) y cómo se relaciona con `PREVIOUS_AGREEMENTS_ID`.
3. ~~**AGREEMENT_METADATA**~~ — **Resuelto.** Se confirmó que en este caso el código tenía la razón: el DDL fue actualizado para agregar la tabla `AGREEMENT_METADATA` (`ip_origin`, `user_agent`, `capture_channel`, `signature_token`, `auth_provider`, `extra_variables`), con FK a `AGREEMENTS(ID) ON DELETE CASCADE`. Las columnas `IP_ORIGIN`/`USER_AGENT` se quitaron de `AGREEMENTS` porque ahora viven en la tabla separada. DDL y entity ya están alineados.
4. **AGREEMENT_INTEGRITY_LOG**: mismo caso — la entity Java (`expected_hash`, `actual_hash`, `detected_by`, `detected_at`) no coincide con el DDL (`stored_hash`, `recalculated_hash`, `is_valid`, `check_type`, `error_detail`). Este documento usa el DDL.
