# Guía: Migraciones de base de datos (Flyway)

## Estado actual — Flyway activo

```
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

Flyway crea y versiona el esquema. Hibernate **solo valida** que las entidades JPA coincidan con lo que Flyway aplicó — ya no genera ni modifica DDL. Si tu entidad no coincide con el esquema real, el backend falla al arrancar (no en producción, en tu máquina, apenas levantas).

`V1__init_schema.sql` es el baseline: el esquema completo capturado con `pg_dump --schema-only` desde la base de datos de desarrollo. Reemplaza las migraciones sueltas que existían antes de activar Flyway. El historial de migraciones aplicadas queda registrado en la tabla `flyway_schema_history` de cada base de datos.

Los scripts en `init-db/` los corre PostgreSQL solo la primera vez que el contenedor se crea (volumen vacío). Con Flyway activo no hacen falta para el esquema — Flyway lo construye desde `V1__init_schema.sql` al arrancar el backend contra una base vacía.

| Entorno | ddl-auto | Flyway |
|---------|----------|--------|
| Local (Docker dev) | `validate` | habilitado |
| Staging | `validate` | habilitado |
| Producción | `validate` | habilitado |

---

## Regla de oro

**Nunca edites una migración que ya fue mergeada a `main` (o que ya corrió en la base de datos de alguien más).** Flyway calcula un checksum de cada archivo aplicado; si el contenido cambia después, la próxima persona que arranque el backend contra una base que ya tiene esa versión aplicada va a ver un error de checksum mismatch, no un DDL actualizado.

Cada cambio de esquema, por chico que sea, es un **archivo nuevo**:

```
V2__descripcion_snake_case.sql
V3__otra_descripcion.sql
```

### Nomenclatura obligatoria

```
V{número}__{descripción_snake_case}.sql
         ^^
     doble guión bajo
```

Ejemplos:
- `V2__privacy_documents_bytea.sql`
- `V3__agreements_integrity_log.sql`

El número debe ser el siguiente disponible — revisa `backend/src/main/resources/db/migration/` antes de nombrar tu archivo para no chocar con una migración que alguien más subió en paralelo.

---

## Flujo paso a paso para un cambio de esquema

1. **Crea el archivo** en `backend/src/main/resources/db/migration/` con el siguiente número de versión y una descripción clara.
2. **Escribe el DDL** (`CREATE TABLE`, `ALTER TABLE`, etc.) en ese archivo.
3. **Actualiza las entidades JPA** que correspondan para que coincidan exactamente con el nuevo esquema — columnas, tipos, nullability. Como `ddl-auto=validate`, si la entidad y la tabla no coinciden el backend no arranca.
4. **Levanta el backend localmente** (`./mvnw spring-boot:run`). Flyway aplica automáticamente cualquier migración pendiente al arrancar, y Hibernate valida contra el resultado. Si algo no cuadra, falla ahí mismo — es la señal de que hay que corregir la entidad o el DDL antes de subir el cambio.
5. **Commitea el `.sql` junto con los cambios de entidades** en el mismo PR. No tiene sentido separarlos: uno sin el otro deja el repo en un estado que no arranca.

---

## Cuando el equipo hace `git pull`

Si alguien trajo migraciones nuevas (`V3`, `V4`, etc.), **no hace falta resetear nada**. Solo:

```bash
git pull
cd backend
export $(cat ../.env | xargs) && ./mvnw clean spring-boot:run
```

Flyway detecta las migraciones pendientes y las aplica en orden sobre la base de datos local existente, sin tocar los datos que ya tenías. El `clean` es buena práctica después de un pull con cambios en `pom.xml`, `.java` o `application.properties`, para evitar clases compiladas viejas mezcladas con las nuevas.

---

## Cuándo SÍ hay que resetear la base local

Solo en estos casos:

- Alguien editó (en vez de crear una nueva) una migración que tu base ya tenía aplicada → error de checksum.
- Cambiaste de rama y la otra rama tiene un baseline (`V1`) distinto — pasa si estás migrando de "sin Flyway" a "con Flyway" en distintas ramas.
- Quieres partir de una base de datos limpia para probar el flujo completo desde cero.

Reset completo:

```bash
docker-compose down -v
rm -rf ./postgres_data ./postgres_replica_data
docker-compose up -d
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

El script de Keycloak genera un **nuevo `KC_BACKEND_SECRET`** cada vez que se recrea el volumen — hay que actualizar el `.env` con el valor que imprime al final, si no `POST /api/users` falla con 500.

---

## Troubleshooting

### `FlywaySqlUnableToConnectToDbException` / `password authentication failed`

No es un problema de Flyway — es que las credenciales (`DB_USER`/`DB_PASS`) que exportaste no coinciden con las que tiene el rol de Postgres ya creado en el contenedor. Postgres solo aplica `POSTGRES_USER`/`POSTGRES_PASSWORD` la primera vez que el volumen está vacío; cambiar el `.env` después no actualiza la contraseña del rol existente. Si esto pasa, o el `.env` está mal, o hace falta el reset de la sección anterior.

**Windows/Git Bash:** si editaste `.env` en un editor que lo guardó con terminadores de línea CRLF, `export $(cat ../.env | xargs)` puede dejar un `\r` invisible pegado al final de cada valor, rompiendo la autenticación aunque el archivo se vea correcto. Solución sin tocar el archivo:

```bash
export $(cat ../.env | xargs | tr -d '\r') && ./mvnw spring-boot:run
```

O convierte el archivo a LF desde el editor (en VS Code: click en `CRLF` en la barra inferior → `LF` → guardar) para no tener que repetir el `tr -d '\r'` cada vez.

### Checksum mismatch en una migración

Significa que el contenido de un `.sql` que ya está en `flyway_schema_history` cambió después de aplicarse. No se corrige editando el archivo otra vez — hay que:
1. Revertir el archivo a como estaba cuando se aplicó (o crear una migración nueva que corrija lo que hacía falta), **o**
2. Resetear la base local si es un entorno de desarrollo y no importa perder los datos.

### El backend arranca pero Hibernate se queja de columnas/tablas que no coinciden

La entidad JPA no coincide con el esquema real. Revisa que el `.sql` de la migración y la entidad se hayan actualizado juntos — es el error más común cuando se olvida el paso 3 del flujo de arriba.
