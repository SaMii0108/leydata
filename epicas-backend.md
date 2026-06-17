# Ley Data — Épicas de Backend

**Rol:** Tech Lead / Arquitecto de Software  
**Fecha:** 2026-06-09  
**Stack:** Java 21 · Spring Boot 4.0.6 · Keycloak 26 · PostgreSQL 16 · Docker Compose

---

## Confirmación de contexto

**Ley Data** es un sistema de gestión de consentimiento informado bajo la Ley 21.719 (Chile). Su núcleo es el ciclo de vida completo de un consentimiento: una organización define para qué quiere usar datos de personas (*purposes*), obtiene aprobación interna del DPO, construye documentos y plantillas de consentimiento, los presenta a los titulares, y registra los acuerdos de forma íntegra e inmutable.

**Estado actual del código:** hay 21 entidades JPA definidas, pero solo se han implementado controladores/servicios para una fracción: Users, Domains, PurposeRequests y Audit. El corazón del sistema — consentimientos reales (`Agreements`), titulares (`DataSubjects`), documentos y plantillas — existe solo como modelo de datos, sin lógica de negocio ni API.

---

## Mapa de dependencias (ruta crítica)

```
[E1] IAM & Seguridad ──► [E2] Catálogos Base ──► [E3] Flujo de Aprobación
                                    │
                                    ▼
                         [E4] Titulares ──────────────────────────►
                                    │                              │
                                    ▼                              ▼
                         [E5] Documentos & Plantillas ──► [E6] Consentimientos (CORE)
                                                                   │
                                                    ───────────────┘
                                                    │
                                                    ▼
                                         [E7] Retención & Políticas
                                         [E8] Auditoría Completa  (transversal)
```

---

## E1 — IAM & Seguridad *(BLOQUEANTE — atacar primero)*

**Qué:** La capa de identidad, acceso y ciclo de vida de usuarios dentro del sistema. Incluye la integración con Keycloak y las reglas de negocio locales que Keycloak no conoce.

**Por qué:** Todo endpoint protegido depende de que esta capa esté correcta. Un error aquí compromete la integridad del cumplimiento legal.

**Alcance funcional:**
- Ciclo de vida de usuario: creación unificada (Keycloak + BD local), actualización, bloqueo/desbloqueo, activación/desactivación
- Gestión de roles: ADMIN, DPO, JEFE_DOMINIO, USER, TITULAR
- Sincronización de identidad via `keycloak_id`
- Filtro de estado de usuario (`active`, `blocked`)

**Deuda técnica conocida que debe cerrarse aquí:**
- Tabla `role` incompleta: faltan `USER` y `TITULAR` — rompe `POST /api/users` para esos roles
- `@PreAuthorize` inconsistente en `purpose-requests`: ADMIN no puede revisar solicitudes aunque `SecurityConfig` sí lo permite
- `dpo@leydata.cl` desincronizado: `keycloak_id` apunta a un usuario que no existe en Keycloak

**Entregables que se le pide al developer:**
- Contrato de API completo (request/response DTOs para todos los endpoints de usuario)
- Tabla de roles con descripción de permisos por rol
- Diagrama de estados del usuario (`active`, `blocked`, `mustChangePassword`)
- Casos de borde documentados: ¿qué pasa si el usuario existe en Keycloak pero no en BD? ¿Y al revés?

---

## E2 — Catálogos Base *(BLOQUEANTE para E3, E4, E5)*

**Qué:** Las tablas maestras que el resto del sistema referencia. Son datos relativamente estables que otros módulos usan como FK.

**Por qué:** Sin un catálogo de bases legales y categorías de datos, no se puede definir un propósito correctamente. Sin dominios correctamente modelados, no se puede asignar usuarios ni propósitos a unidades organizacionales.

**Alcance funcional:**
- **Dominios organizacionales:** CRUD completo con soft-delete; corrección del endpoint `GET /api/domains/all` que actualmente expone entidades con objetos anidados sin DTO
- **Catálogo de Bases Legales (`legal_basis_catalog`):** los artículos de la Ley 21.719 que justifican el tratamiento de datos. Probablemente solo lectura para la API (son fijos por ley), pero deben ser consultables
- **Categorías de datos (`data_categories`):** tipos de datos que pueden tratarse (ej. datos de salud, datos financieros). CRUD para ADMIN
- **Retención de datos (`data_retention_policies`):** políticas de cuánto tiempo se conserva cada categoría de datos bajo cada propósito

**Entregables:**
- Diseño de endpoints REST (GET de catálogos, CRUD donde aplique)
- Definición de qué catálogos son editables y por qué rol
- DTOs de respuesta que nunca expongan relaciones bidireccionales ni objetos Hibernate lazy

---

## E3 — Flujo de Aprobación de Propósitos *(depende de E1 y E2)*

**Qué:** El workflow interno de solicitud y revisión de propósitos. Una unidad organizacional (via su JEFE_DOMINIO) solicita autorización para tratar un tipo de dato bajo un fundamento legal específico. El DPO (o ADMIN) aprueba o rechaza.

**Por qué:** Es el mecanismo de control interno que garantiza que cada tratamiento de datos tiene respaldo legal antes de activarse. Ningún consentimiento puede recabarse de titulares sin un propósito aprobado.

**Alcance funcional:**
- `PurposeRequests`: estados PENDING → APPROVED / REJECTED, con comentario de revisión
- `Purposes`: la entidad aprobada resultante, vinculada a `LegalBasisCatalog`, `Domains` y `DataCategories`
- `PurposeDataCategories`: relación many-to-many entre propósitos y categorías de datos

**Deuda conocida que debe cerrarse:**
- `@PreAuthorize("hasRole('DPO')")` en los endpoints de revisión excluye a ADMIN — debe corregirse

**Entregables:**
- Diagrama de estado del PurposeRequest (transiciones válidas e inválidas)
- Contrato de los endpoints de review (¿qué datos necesita el DPO para decidir?)
- Regla de negocio: ¿puede un propósito aprobado volver a PENDING? ¿Puede desactivarse?

---

## E4 — Gestión de Titulares *(puede ejecutarse en paralelo con E3)*

**Qué:** El módulo para registrar y administrar a las personas naturales cuyos datos se tratan. En la ley chilena se denominan "titulares de datos".

**Por qué:** Es la otra punta del consentimiento. Sin titulares registrados no hay acuerdos posibles. Este módulo también debe manejar los derechos ARCO (Acceso, Rectificación, Cancelación, Oposición) que la Ley 21.719 garantiza.

**Alcance funcional:**
- CRUD de `DataSubjects`: nombre, RUT u otro identificador, canal de contacto, fecha de registro
- Consulta de consentimientos activos de un titular
- Revocación de consentimientos (inicia desde el titular)
- Consideración de privacidad: ¿qué datos del titular son visibles para cada rol?

**Entregables:**
- Definición de identificadores únicos del titular (¿RUT? ¿email? ¿UUID interno?)
- Política de visibilidad de datos por rol (un JEFE_DOMINIO no debería ver titulares fuera de su dominio)
- Endpoints de derechos ARCO: al menos acceso y revocación

---

## E5 — Documentos de Privacidad y Plantillas de Consentimiento *(depende de E3)*

**Qué:** Los documentos legales que describen el tratamiento de datos y las plantillas estructuradas a partir de las cuales se generan los consentimientos que firma el titular.

**Por qué:** El consentimiento informado exige que el titular sepa exactamente a qué está consintiendo. El documento de privacidad es el respaldo legal; la plantilla es el instrumento operativo que genera el acuerdo.

**Alcance funcional:**
- `PrivacyDocuments`: documento de privacidad por versión, vinculado a uno o más propósitos (`DocumentPurposes`)
- `Templates`: estructura del consentimiento (campos requeridos, texto del acuerdo) vinculada a propósitos (`TemplatePurposes`)
- Versionado: cuando cambia el documento o la plantilla, los consentimientos existentes no se invalidan retroactivamente, pero las nuevas capturas usan la versión vigente

**Entregables:**
- Modelo de versionado: ¿la versión es un número? ¿una fecha? ¿cómo se activa una nueva versión?
- ¿El template es texto libre o tiene campos estructurados? Definir el esquema JSON o de campos
- Regla sobre qué pasa con consentimientos activos cuando el documento de privacidad cambia (¿notificación? ¿re-consentimiento?)

---

## E6 — Ciclo de Vida del Consentimiento *(CORE — depende de E3, E4, E5)*

**Qué:** El núcleo funcional del sistema. Es el registro de que un titular específico consintió un propósito específico, bajo un documento y una plantilla en una versión determinada, en una fecha y por un canal concreto.

**Por qué:** Es la razón de existir del sistema. Todo lo anterior es setup. Aquí es donde el cumplimiento legal se materializa. La integridad de estos registros es lo que se presenta ante reguladores.

**Alcance funcional:**
- Creación de `Agreements`: captura del consentimiento, vinculado a titular, propósito, plantilla y documento vigentes
- `AgreementsPurposes`: un acuerdo puede cubrir múltiples propósitos
- `AgreementMetadata`: contexto de la captura (canal, IP, dispositivo, timestamp)
- `AgreementIntegrityLog`: hash SHA-256 por acuerdo para verificar que no fue alterado post-firma
- Revocación: el titular puede retirar el consentimiento; esto NO borra el registro, sino que crea un evento de revocación
- Consulta: ¿qué propósitos tiene consentidos un titular? ¿Desde cuándo? ¿Bajo qué versión del documento?

**Entregables:**
- Diagrama de estados del Agreement (ACTIVE → REVOKED → ?, ¿puede reactivarse?)
- Esquema del `AgreementMetadata`: qué campos son obligatorios para evidencia legal
- Estrategia de integridad: ¿un hash por acuerdo, o una cadena como el audit log de operadores?
- Contrato de API diferenciado: ¿quién puede crear acuerdos (backend en nombre del titular, o el titular directamente)? ¿Quién puede consultarlos?

---

## E7 — Retención y Políticas de Datos *(depende de E2 y E6)*

**Qué:** La gestión del ciclo de vida de los datos después de que el consentimiento finaliza o expira. La Ley 21.719 exige que los datos no se conserven más allá de lo necesario.

**Por qué:** Sin políticas de retención el sistema cumple solo la mitad de la ley: capta el consentimiento pero no gestiona el fin del tratamiento.

**Alcance funcional:**
- `DataRetentionPolicies`: período máximo de retención por propósito y categoría de datos
- Alerta o acción cuando un acuerdo supera su período de retención
- Reporte de datos próximos a expirar o ya expirados

**Entregables:**
- Definición de qué hace el sistema cuando expira: ¿envía notificación? ¿cambia estado automáticamente? ¿requiere acción manual?
- Interfaz de consulta: ¿quién puede ver el reporte de retención y con qué filtros?

---

## E8 — Auditoría e Integridad *(transversal — crece con cada épica)*

**Qué:** La capa de registro inmutable y verificable de todas las acciones críticas del sistema. Ya tiene una base sólida implementada; necesita expandirse para cubrir los módulos nuevos.

**Por qué:** La auditoría inmutable no es una feature opcional — es un requisito de la Ley 21.719 para demostrar que el sistema operó correctamente.

**Alcance funcional:**
- Extender `AuditService.tryLog()` a todos los servicios que se creen en E3–E6
- Definir las nuevas acciones auditables: `CREAR_TITULAR`, `REGISTRAR_CONSENTIMIENTO`, `REVOCAR_CONSENTIMIENTO`, `APROBAR_DOCUMENTO`, `PUBLICAR_PLANTILLA`, etc.
- Keycloak Event Listener SPI: centralizar eventos de login/logout en `system_audit_log`
- Expandir `GET /api/audit/logs/verify` para incluir los hashes de `AgreementIntegrityLog`

**Entregables:**
- Lista completa de acciones auditables con: tabla afectada, rol que la ejecuta, campos en `oldData`/`newData`
- Diseño del SPI de Keycloak (si se decide implementar): ¿webhook interno? ¿cola de eventos?

---

## Tabla resumen

| Épica | Bloquea a | Puede iniciar | Complejidad |
|-------|-----------|---------------|-------------|
| E1 — IAM & Seguridad | Todo | Ahora mismo | Media (código existe, hay deuda) |
| E2 — Catálogos Base | E3, E4, E5 | Ahora mismo (paralelo a E1) | Baja-Media |
| E3 — Flujo de Aprobación | E5, E6 | Cuando E1+E2 estén | Media |
| E4 — Titulares | E6 | Cuando E1+E2 estén | Media |
| E5 — Docs & Plantillas | E6 | Cuando E3 esté | Media |
| E6 — Consentimientos | E7 | Cuando E3+E4+E5 estén | Alta (es el core) |
| E7 — Retención | — | Cuando E2+E6 estén | Baja-Media |
| E8 — Auditoría | — | Transversal, crece con cada épica | Continua |

---

## Nota de arranque

E1 y E2 se pueden asignar en paralelo a dos developers distintos hoy mismo — no se pisan.  
E8 debe tener a alguien de referencia desde el inicio: cada developer que toque un servicio debe saber cómo integrarse con `AuditService.tryLog()`.
