# Estrategia de migraciones de base de datos

## Estado actual — desarrollo activo

`spring.jpa.hibernate.ddl-auto=update`

Hibernate detecta los cambios en las entidades JPA y actualiza el esquema automáticamente.
**Esto es correcto para el desarrollo**, pero NO para producción.

---

## Cuándo y cómo migrar a Flyway

### Fase 1 — ahora (desarrollo)
Mantener `ddl-auto=update`. El esquema evoluciona junto con el código.
Los scripts de `init-db/` inicializan el Docker de desarrollo.

### Fase 2 — pre-producción (cuando el esquema se estabilice)

1. Agregar Flyway al `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-database-postgresql</artifactId>
   </dependency>
   ```

2. Cambiar en `application.properties`:
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   spring.flyway.enabled=true
   spring.flyway.locations=classpath:db/migration
   ```

3. Capturar el esquema actual como `V1__init_schema.sql` (todo lo que está en `init-db/`).

4. Cada cambio futuro al esquema → nuevo archivo:
   ```
   V2__add_privacy_documents_pdf_content.sql
   V3__add_agreements_integrity_log.sql
   ```

### Regla clave
- `ddl-auto=update` crea columnas pero NUNCA las borra. Es destructivo en casos edge.
- Flyway da control total: sabes exactamente qué scripts corrieron en cada entorno.
- El historial de migraciones queda en la tabla `flyway_schema_history`.

### Nomenclatura obligatoria de Flyway
```
V{número}__{descripción_snake_case}.sql
         ^^
     doble guión bajo
```

Ejemplos:
- `V1__init_schema.sql`
- `V2__privacy_documents_bytea.sql`
- `V3__agreements_integrity_log.sql`

---

## ¿Qué pasa con los init-db/ actuales?

Los scripts de `init-db/` los corre PostgreSQL solo cuando el container se crea por primera vez
(volumen vacío). En producción no sirven — ahí usas Flyway.

Cuando hagas la transición:
1. Consolida todos los `init-db/*.sql` en `V1__init_schema.sql`
2. Elimina `init-db/` del docker-compose o déjalo solo para desarrollo local
3. En el `docker-compose.yml` de producción, no montes `init-db/` — Flyway se encarga

---

## Recomendación final para LeyData

| Entorno | ddl-auto | Flyway |
|---------|----------|--------|
| Local (Docker dev) | `update` | deshabilitado |
| Staging | `validate` | habilitado |
| Producción | `validate` | habilitado |
