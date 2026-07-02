# Resumen general de tests — Funcionales de servicios

Compendio de los tests unitarios de la capa de servicio (`application.service`), cruzando los casos definidos en los planes de prueba manuales de `docs/test-plans/` con los resultados de la última corrida completa (`./mvnw test`, rama `feature/pruebase2e`, 2026-07-01).

| Módulo | Casos en plan de pruebas (manual) | Clase JUnit (servicio) | Tests automatizados | Resultado |
|---|---|---|---|---|
| Agreements | 22 | `AgreementServiceTest` | 22 | ✅ |
| Auditoría | 6 | `AuditServiceTest` | 12 | ✅ |
| Auditoría (trazabilidad) | — | `AgreementTraceServiceTest` | 10 | ✅ |
| Auditoría (scheduler) | — | `IntegritySchedulerTest` | 3 | ✅ |
| Auditoría (verificador) | — | `IntegrityVerifierTest` | 12 | ✅ |
| Categorías de Datos | 6 | `DataCategoryServiceTest` | 15 | ✅ |
| Notificaciones | 5 | `NotificationServiceTest` | 8 | ✅ |
| Dominios | 4 | `DomainServiceTest` | 12 | ✅ |
| Documentos de Privacidad | 18 | `PrivacyDocumentServiceTest` | 50 | ✅ |
| Vínculo Finalidad↔Categoría | 5 | `PurposeDataCategoryServiceTest` | 19 | ✅ |
| Solicitudes de Finalidad | 5 | `PurposeRequestServiceTest` | 14 | ✅ |
| Finalidades | 10 | `PurposeServiceTest` | 27 | ✅ |
| Templates | 32 | `TemplateServiceTest` | 22 | ✅ |
| Usuarios | 7 | `UserServiceTest` | 27 | ✅ |
| **Total** | | | **253** | **✅ 253/253** |

## Notas

- El plan manual `audit-test-plan.md` describe 6 casos generales que en código se implementaron como 4 clases separadas: `AuditServiceTest`, `AgreementTraceServiceTest`, `IntegritySchedulerTest` e `IntegrityVerifierTest`.
- Alcance de este documento: solo tests funcionales de la capa de servicio. Quedan fuera `TemplateControllerTest` (capa web) y `BackendApplicationTests` (smoke test de arranque de contexto, no es funcional de un servicio).
- Se encontró un reporte de Surefire obsoleto (2026-06-28) para `AgreementIntegritySchedulerTest`, clase que ya no existe en `src/test`. No se incluye en los conteos por no formar parte del código fuente actual.
