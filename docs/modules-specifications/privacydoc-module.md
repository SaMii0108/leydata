# Módulo: Documentos de Privacidad (`privacydoc/`)

**Paquete:** `com.leydata.backend.privacydoc`  
**Endpoints base:** `/api/privacy-documents/**`  
**Escritura:** DPO  
**Lectura:** Cualquier usuario autenticado

---

## Responsabilidad

Gestión del ciclo de vida de los documentos legales que respaldan el consentimiento: políticas de privacidad, avisos de cookies, formularios de datos sensibles, etc.

Un documento debe estar en estado `PUBLISHED` para poder vincularse a un Agreement. Cada Agreement sella el `documentId` en su registro inmutable — el titular siempre puede saber qué versión del documento respaldaba su consentimiento.

---

## Estados del documento

```
DRAFT  →  IN_REVIEW  →  APPROVED  →  PUBLISHED
                     →  REJECTED  →  DRAFT (revisión)
PUBLISHED  →  ARCHIVED
```

Solo el DPO puede mover un documento entre estados. Un documento `PUBLISHED` no puede ser modificado — cualquier cambio requiere crear una nueva versión (`DRAFT`) y pasar por el workflow completo.

**Relación con Templates — el documento es el dueño del vínculo:** un documento puede tener cero, uno o varios templates asociados (tabla de unión `document_templates`). La asociación se gestiona vía `POST/DELETE /api/privacy-documents/{id}/templates/{templateId}` y es independiente del ciclo de vida del documento: se puede vincular o desvincular un template en cualquier estado (DRAFT, IN_REVIEW, APPROVED, PUBLISHED, ARCHIVED), y no se exige que el template esté `ACTIVE`. A diferencia de las purposes, el documento **no necesita ningún template asociado para poder publicarse** — el orden habitual de trabajo es publicar el documento primero y asociarle template(s) después, nunca al revés.

**Un solo `PUBLISHED` por template:** para cada template vinculado, `publish()` archiva automáticamente cualquier otro documento `PUBLISHED` que comparta ese mismo template — mismo patrón que `TemplateService.activate()` usa para desactivar la versión anterior de un `TEMPLATE_KEY`. Esto garantiza que "el documento vigente de este template" siga siendo una búsqueda sin ambigüedad, usada por `AgreementService.create()` para resolver `documentId` automáticamente cuando no viene en el request (ver [`docs/agreements-module.md`](agreements-module.md)) y por el endpoint B2B `GET /api/templates/resolve` del Orquestador (ver [`docs/orchestrator-module.md`](orchestrator-module.md)).

---

## Categorías de documento (`DocumentCategory`)

| Código | Descripción |
|---|---|
| `POLITICA_PRIVACIDAD` | Política general de privacidad |
| `AVISO_COOKIES` | Aviso de uso de cookies |
| `DATOS_SENSIBLES` | Formulario específico para categorías sensibles (salud, biométrico, etc.) |
| `MARKETING_DIRECTO` | Consentimiento explícito para comunicaciones comerciales |
| `MENORES_EDAD` | Formulario con requisitos especiales para menores |
| `TRANSFERENCIA_TERCEROS` | Autorización de transferencia a terceros o al exterior |

---

## Endpoints

```
POST   /api/privacy-documents                        — Crear documento (DRAFT)
GET    /api/privacy-documents                        — Listar documentos (con filtros opcionales)
GET    /api/privacy-documents/{id}                   — Obtener documento por ID
PUT    /api/privacy-documents/{id}                   — Actualizar documento (solo en DRAFT)
PATCH  /api/privacy-documents/{id}/submit            — Enviar a revisión (DRAFT → IN_REVIEW)
PATCH  /api/privacy-documents/{id}/approve           — Aprobar (IN_REVIEW → APPROVED)
PATCH  /api/privacy-documents/{id}/reject            — Rechazar con motivo
PATCH  /api/privacy-documents/{id}/publish           — Publicar (APPROVED → PUBLISHED)
PATCH  /api/privacy-documents/{id}/archive           — Archivar documento obsoleto
GET    /api/privacy-documents/{id}/verify            — Verificar integridad del documento
POST   /api/privacy-documents/{id}/purposes          — Vincular purposes al documento
DELETE /api/privacy-documents/{id}/purposes/{pid}    — Desvincular purpose
POST   /api/privacy-documents/{id}/templates/{tid}   — Vincular template (cualquier estado)
DELETE /api/privacy-documents/{id}/templates/{tid}   — Desvincular template (cualquier estado)
GET    /api/privacy-documents/download/{id}          — Descargar PDF generado
```

---

## Relación con Agreements

Un Agreement **requiere** un documento `PUBLISHED`. Al crear un Agreement (`POST /api/agreements`), el backend valida:

1. El `documentId` existe y está en estado `PUBLISHED`
2. El documento cubre (vía `document_purposes`) **todas** las finalidades del request

Si alguna finalidad del Agreement no está vinculada al documento, el backend responde `422 Unprocessable Entity`.

---

## Generación de PDF (`PdfGeneratorService`)

El servicio `PdfGeneratorService` (`privacydoc/infrastructure/pdf/`) genera un PDF firmado del documento. El PDF puede descargarse vía `GET /api/privacy-documents/download/{id}` y se puede adjuntar al email de confirmación de consentimiento.

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `privacydoc/web/PrivacyDocumentController.java` | Endpoints + transiciones de estado |
| `privacydoc/application/service/PrivacyDocumentService.java` | Lógica de workflow y validaciones |
| `privacydoc/domain/enums/DocumentStatus.java` | Enum de estados |
| `privacydoc/domain/enums/DocumentCategory.java` | Enum de categorías |
| `privacydoc/infrastructure/pdf/PdfGeneratorService.java` | Generación de PDF |
| `privacydoc/infrastructure/persistence/PrivacyDocumentsRepository.java` | JPA sobre `privacy_documents` |
| `privacydoc/infrastructure/persistence/DocumentPurposesRepository.java` | JPA sobre `document_purposes` |
| `privacydoc/infrastructure/persistence/DocumentTemplatesRepository.java` | JPA sobre `document_templates` |
