# Módulo: Templates de Consentimiento

## Descripción

El módulo de Templates define la capa de presentación del consentimiento. Un template es el formulario que se le muestra al titular de datos, donde puede aceptar o rechazar cada finalidad de tratamiento (purpose) de forma explícita.

Cada template agrupa un conjunto de purposes ordenadas, define los textos del formulario (título, botones) y pasa por un flujo de aprobación antes de poder activarse. El versionado garantiza que siempre quede evidencia de qué finalidades se presentaban en cada momento y cuántos acuerdos se generaron bajo cada versión.

---

## Reglas de negocio

1. El mismo `TEMPLATE_KEY` puede tener múltiples versiones, pero solo una puede estar activa a la vez **dentro del mismo dominio**.
2. Al activar una nueva versión, la anterior del mismo dominio + `TEMPLATE_KEY` se desactiva automáticamente en la misma transacción.
3. No se puede editar un template activo — se debe crear una nueva versión.
4. El `TEMPLATE_KEY` debe ser siempre UPPERCASE.
5. No se puede eliminar un template que esté referenciado por un `PRIVACY_DOCUMENT` activo.
6. Los templates no se eliminan, solo se versionan. Siempre debe quedar evidencia de qué finalidades tenía y cuántos acuerdos generó.
7. Solo se pueden vincular purposes aprobadas y activas al template.
8. `ORDER_POSITION` no puede repetirse dentro del mismo template.
9. Un template debe tener al menos una purpose con `IS_VISIBLE = true`.
10. Para activar un template debe tener: al menos una purpose visible y `APPROVED_BY` asignado.
11. `DOMAIN_ID` es obligatorio y se fija al crear el template — no cambia entre versiones (`newVersion()` lo hereda del template origen).

---

## Aislamiento por dominio

`Templates.domainId` (FK a `Domains`) reemplazó la unicidad global de `TEMPLATE_KEY` por una unicidad compuesta `(domain_id, template_key, version)`. Dos dominios distintos pueden tener templates con el mismo `TEMPLATE_KEY` sin conflicto — por ejemplo, `ONBOARDING_CLIENTE` puede existir tanto en el dominio Comercial como en el dominio Soporte, cada uno con su propio ciclo de versiones e independiente entre sí.

**Por qué:** `Templates` no tenía ningún campo de dominio. Eso obligaba a que `TEMPLATE_KEY` fuera único en todo el sistema, lo cual no escala cuando distintas áreas de la organización necesitan formularios de consentimiento con el mismo nombre de negocio pero contenido/finalidades distintas.

**Quién decide el dominio:** a diferencia de `Purposes` (donde el dominio viene implícito del `JEFE_DOMINIO` que solicita la finalidad), `Templates` solo lo crean `DPO`/`ADMIN`, roles que no tienen un dominio propio asignado en `user_domains` (esa tabla es exclusiva de `JEFE_DOMINIO`, y desde esta iteración un jefe de dominio solo puede tener **un** dominio asignado a la vez). Por eso `domainId` es un campo explícito en `CreateTemplateRequest`, igual que en `CreatePurposeRequest` — el DPO lo elige al crear el template, normalmente coincidiendo con el dominio que originó las purposes que va a incluir.

**Impacto en endpoints:** `GET /api/templates/active/{templateKey}` y `GET /api/templates/family/{templateKey}` ahora requieren `domainId` como query param obligatorio, ya que `templateKey` dejó de ser suficiente para identificar un template de forma única.

---

## Estados

```
DRAFT → APPROVED → ACTIVE
```

- **DRAFT**: recién creado o nueva versión. Se pueden vincular y desvincular purposes.
- **APPROVED**: aprobado por el DPO. Listo para activarse.
- **ACTIVE**: en uso. Solo puede haber uno activo por `TEMPLATE_KEY`. No se puede editar.

> El campo `ACTIVATION_DATE` permite registrar una fecha de activación prevista. Si está presente puede usarse para activación automática; si es nulo, la activación es manual.

---

## Casos de uso

### Gestión básica
1. Crear template (nuevo `TEMPLATE_KEY`, versión 1)
2. Crear nueva versión de un template existente (mismo `TEMPLATE_KEY`, versión N+1)
3. Obtener template por ID
4. Listar templates (filtros: `templateKey`, `status`)
5. Obtener historial de versiones de un `TEMPLATE_KEY`
6. Obtener la versión activa de un `TEMPLATE_KEY`

### Workflow de estados
7. Aprobar template (`APPROVED_BY` + `APPROVED_AT`)
8. Activar template (desactiva la versión anterior del mismo `TEMPLATE_KEY` en la misma transacción)

### Gestión de purposes
9. Vincular purpose al template (con `ORDER_POSITION` e `IS_VISIBLE`)
10. Desvincular purpose del template (hard delete, solo en estado DRAFT)
11. Listar purposes del template ordenadas por `ORDER_POSITION`
12. Actualizar `ORDER_POSITION` o `IS_VISIBLE` de una purpose en el template

---

## Endpoints

### Templates

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/templates` | Crear template (versión 1, requiere `domainId`) |
| `POST` | `/api/templates/{id}/new-version` | Crear nueva versión |
| `GET` | `/api/templates/{id}` | Obtener por ID |
| `GET` | `/api/templates` | Listar (filtros: `domainId`, `templateKey`, `status`) |
| `GET` | `/api/templates/family/{templateKey}?domainId=` | Historial de versiones dentro de un dominio |
| `GET` | `/api/templates/active/{templateKey}?domainId=` | Obtener versión activa dentro de un dominio |
| `POST` | `/api/templates/{id}/approve` | Aprobar |
| `POST` | `/api/templates/{id}/activate` | Activar |
| `GET` | `/api/templates/resolve?domainId=&templateKey=` | **Uso interno B2B** — resuelve template activo + documento publicado. Llamado por el Orquestador, no requiere rol DPO/ADMIN (solo autenticación). |

### Purposes del template

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/templates/{id}/purposes` | Vincular purpose |
| `DELETE` | `/api/templates/{id}/purposes/{purposeId}` | Desvincular purpose (solo DRAFT) |
| `GET` | `/api/templates/{id}/purposes` | Listar purposes ordenadas |
| `PATCH` | `/api/templates/{id}/purposes/{purposeId}` | Actualizar `ORDER_POSITION` o `IS_VISIBLE` |

---

## Estructura del módulo

```
template/
  application/
    dto/
      CreateTemplateRequest.java
      UpdateTemplatePurposeRequest.java
      TemplateResponse.java
      TemplatePurposeRequest.java
    service/
      TemplateService.java
  domain/
    enums/
      TemplateStatus.java
    exception/
      TemplateNotFoundException.java
  infrastructure/
    persistence/
      TemplatesRepository.java
      TemplatePurposesRepository.java
  web/
    TemplateController.java
```
