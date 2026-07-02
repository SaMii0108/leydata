# Migración de Hibernate ddl-auto a Flyway (creación de esquema)

## Contexto

Hoy el esquema de la BD lo genera Hibernate en tiempo de arranque (`spring.jpa.hibernate.ddl-auto=update`). Flyway no está en el `pom.xml`. Existen archivos de migración sueltos `V2`–`V7` en `backend/src/main/resources/db/migration/`, escritos como preparación manual, pero sin `V1` (baseline) ni Flyway habilitado — nunca se ejecutaron con Flyway (no existe la tabla `flyway_schema_history`).

La BD de dev (`leydata-consent-db`, contenedor Docker, `leydata_db`) ya tiene el esquema completo aplicado por Hibernate, incluyendo los cambios que describen `V2`–`V7` (ej. `privacy_documents.document_family_id` ya existe).

## Objetivo

Flyway pasa a ser el dueño del esquema: lo crea y versiona mediante migraciones SQL. Hibernate deja de generar DDL y solo valida (`ddl-auto=validate`) que las entidades JPA coincidan con lo que Flyway creó.

## Diseño

### 1. Dependencias — `backend/pom.xml`

Agregar junto a `spring-boot-starter-data-jpa`:

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

Sin `<version>` explícita: las gestiona el BOM de `spring-boot-starter-parent` (4.0.6).

### 2. Baseline `V1__init_schema.sql`

Generado con `pg_dump --schema-only` contra el contenedor `leydata-consent-db` (base `leydata_db`, usuario `victor`), que hoy tiene las 23 tablas de la app más triggers/funciones (de la migración V5 ya aplicada manualmente).

El dump se limpia de:
- Sentencias `OWNER TO`, `GRANT`, `REVOKE`
- Comentarios/metadata propios de `pg_dump` (versión, fecha, etc.)
- `SET` de sesión que no aplican a Flyway (search_path se deja si es necesario para el esquema `public`)

Se guarda como `backend/src/main/resources/db/migration/V1__init_schema.sql`.

### 3. Eliminar migraciones sueltas V2–V7

Se eliminan estos 6 archivos, ya que su contenido queda absorbido en el V1 (la BD de dev ya los tiene aplicados):

- `V2__add_document_family_id.sql`
- `V3__add_data_category_system_fields.sql`
- `V4__add_consent_statement_and_data_uses.sql`
- `V5__entity_integrity_log_trigger.sql`
- `V6__purposes_versioning_backfill.sql`
- `V7__purposes_code_not_unique.sql`

La numeración de Flyway arranca limpia desde `V1`. La próxima migración real de este proyecto será `V2` de nuevo.

### 4. Configuración — `backend/src/main/resources/application.properties`

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

### 5. Documentación — `backend/src/main/resources/db/migration/README.md`

Actualizar el documento: ya no estamos en "Fase 1 — ahora (desarrollo)" con `ddl-auto=update`; estamos en Fase 2, con Flyway activo y `ddl-auto=validate`. Ajustar la tabla de recomendación final y las secciones de "cuándo migrar" para reflejar que la migración ya ocurrió.

### 6. Verificación

Para confirmar que Flyway construye el esquema completo desde cero (sin depender de que Hibernate ya lo haya creado antes), se recrea la BD de dev vacía:

- Bajar el volumen del contenedor `leydata-consent-db` (o `DROP SCHEMA public CASCADE` + recrear el schema) para partir de una base vacía.
- Levantar el backend y confirmar en logs que Flyway ejecuta `V1__init_schema.sql` y crea la tabla `flyway_schema_history`.
- Confirmar que Hibernate arranca sin errores de validación (`ddl-auto=validate` no reporta discrepancias contra lo creado por Flyway).

Este paso borra los datos actuales de `leydata_db` en dev. Se pide confirmación explícita al usuario antes de ejecutarlo.

## Fuera de alcance

- No se toca la configuración de `leydata-consent-db-replica` (replicación) más allá de lo que ya hace Postgres automáticamente al replicar desde el primario.
- No se define aquí el manejo por perfiles (dev/staging/prod) más allá de lo que ya existe — se aplica el mismo `ddl-auto=validate` + Flyway en todos los entornos vía `application.properties` (no hay archivos de perfil separados hoy en el proyecto).
- No se migran los scripts de `init-db/` (solo contienen `99-init-replication.sh`, sin SQL de esquema que consolidar).
