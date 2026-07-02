# agreement/domain/event/ — Eventos de dominio del módulo Agreement

Eventos publicados por `AgreementService` vía `ApplicationEventPublisher`.
Los listeners reaccionan **después del commit de Postgres** usando `@TransactionalEventListener(phase = AFTER_COMMIT)`.

---

## Eventos disponibles

### AgreementRevokedEvent

Publicado al final de `AgreementService.revoke()`, una vez que:
- El acuerdo está marcado como REVOKED en Postgres
- Los purposes están marcados como REVOKED
- El audit log está escrito

**Campos:**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `subjectIdentifier` | `String` | ID opaco del titular (`DataSubjects.identifier`) — nunca PII |
| `purposeIds` | `List<UUID>` | UUIDs de los purposes revocados en este acuerdo |

**Listener:** `AgreementRevocationCacheListener` — borra las keys `consent:{subjectId}:{purposeId}` de Redis.

---

### AgreementIntegrityFailedEvent

Publicado por `AgreementIntegrityScheduler` cuando la verificación SHA-256 nocturna detecta
que el hash de un acuerdo no coincide con el calculado.

---

## Por qué AFTER_COMMIT y no dentro de @Transactional

```
Dentro de @Transactional (MAL):
  1. Postgres: REVOKED
  2. Redis: delete key  ← si Postgres hace rollback después...
  3. Postgres: rollback
  → Redis sin key, Postgres dice ACTIVE → inconsistencia silenciosa

Con AFTER_COMMIT (BIEN):
  1. Postgres: REVOKED + commit exitoso
  2. Redis: delete key  ← solo llega aquí si el commit fue exitoso
  → Si Redis falla: WARN en logs, revocación legal ya persistida en Postgres
```

`@TransactionalEventListener` no despacha el evento si la transacción hace rollback.
Esto hace la invalidación de caché **eventualmente consistente** con garantía de que
la revocación legal ya es irreversible cuando se intenta borrar Redis.
