# Read/Write Routing — AbstractRoutingDataSource

**Fecha:** 2026-06-29
**Branch:** `feature/orchestrator-module`
**Estado:** ✅ Implementado y compilado

---

## Qué hace

Separa automáticamente las consultas de lectura (SELECT) hacia la **réplica de PostgreSQL** y las de escritura (INSERT/UPDATE/DELETE) hacia el **primary vía PgBouncer**. El enrutamiento es transparente: el código de negocio no cambia, Spring decide el pool según el flag de la transacción activa.

---

## Topología de conexiones

```
Spring Boot
    │
    ├─ @Transactional(readOnly = true)  ──►  HikariRead  ──►  Réplica   :5434
    │
    └─ @Transactional  (o sin anotación)  ──►  HikariWrite  ──►  PgBouncer :5435  ──►  Primary :5433
```

En Docker Compose los hostnames son `db` (primary), `db-replica` (réplica) y `pgbouncer` (pooler).

---

## Archivos del módulo

| Archivo | Rol |
|---------|-----|
| `config/DataSourceType.java` | Enum `WRITE / READ` — clave de routing |
| `config/ReadWriteRoutingDataSource.java` | Extiende `AbstractRoutingDataSource`; devuelve la clave según `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` |
| `config/DataSourceConfig.java` | Crea los dos beans `HikariDataSource` y el `@Primary DataSource` final |

---

## Cómo funciona el routing

```
1. Controller recibe request
2. Spring intercepta @Transactional — marca readOnly=true en TransactionSynchronizationManager
3. Código de servicio ejecuta query JPA
4. Hibernate necesita una conexión JDBC → llama DataSource.getConnection()
5. LazyConnectionDataSourceProxy posterga hasta este momento (no antes)
6. AbstractRoutingDataSource.determineCurrentLookupKey() consulta isCurrentTransactionReadOnly()
7. Devuelve DataSourceType.READ → pool de réplica
   Devuelve DataSourceType.WRITE → pool de PgBouncer
```

### Por qué LazyConnectionDataSourceProxy es obligatorio

Sin él, `determineCurrentLookupKey()` se podría ejecutar antes de que `JpaTransactionManager` haya activado el flag `readOnly` en el `TransactionSynchronizationManager`, especialmente cuando el `EntityManager` es inicializado al comienzo de la transacción. El resultado sería que todo va a WRITE aunque el método tenga `@Transactional(readOnly = true)`.

---

## Configuración en application.properties

```properties
# Write: entra por PgBouncer (connection pooling, localhost dev / pgbouncer en Docker)
spring.datasource.write.jdbc-url=jdbc:postgresql://localhost:5435/${DB_NAME:leydata_db}
spring.datasource.write.username=${DB_USER:admin}
spring.datasource.write.password=${DB_PASS:admin}
spring.datasource.write.driver-class-name=org.postgresql.Driver
spring.datasource.write.pool-name=HikariWrite
spring.datasource.write.maximum-pool-size=10
spring.datasource.write.minimum-idle=2

# Read: directo a la réplica
spring.datasource.read.jdbc-url=jdbc:postgresql://localhost:5434/${DB_NAME:leydata_db}
spring.datasource.read.username=${DB_USER:admin}
spring.datasource.read.password=${DB_PASS:admin}
spring.datasource.read.driver-class-name=org.postgresql.Driver
spring.datasource.read.pool-name=HikariRead
spring.datasource.read.maximum-pool-size=20
spring.datasource.read.minimum-idle=5
```

> **Atención:** HikariCP usa `jdbc-url` (con guión), NO `url`. Con `url` no da error en arranque pero no conecta.

---

## Dependencias requeridas

Todas ya están en `pom.xml` — no se agregó ninguna dependencia nueva:

| Dependencia | Razón |
|-------------|-------|
| `spring-boot-starter-data-jpa` | Incluye `AbstractRoutingDataSource` (Spring JDBC) |
| `spring-boot-starter-data-jpa` | Incluye `LazyConnectionDataSourceProxy` (Spring JDBC) |
| `com.zaxxer:HikariCP` | Incluido automáticamente por Spring Boot |
| `org.postgresql:postgresql` | Driver JDBC (scope runtime) |

---

## Requisito en los services: @Transactional(readOnly = true)

El routing es activado únicamente si el método tiene `@Transactional(readOnly = true)`. Sin esa anotación, la query va a WRITE aunque sea un SELECT.

**Estado actual (verificado):** todos los services de lectura ya tienen la anotación correcta.

```java
// ✅ Va a la réplica
@Transactional(readOnly = true)
public List<AgreementResponse> findAll() { ... }

// ✅ Va al primary (escritura)
@Transactional
public AgreementResponse create(CreateAgreementRequest req) { ... }

// ⚠️ Va al primary (sin anotación = usa el default del pool)
public List<AgreementResponse> findAll() { ... }
```

---

## Comportamiento en arranque

Durante el arranque, Hibernate ejecuta `ddl-auto=update` para actualizar el esquema. En ese momento no hay transacción activa, `isCurrentTransactionReadOnly()` devuelve `false`, y el `defaultTargetDataSource` configurado como `writeDataSource` recibe esas queries. Es el comportamiento correcto — DDL solo va al primary.

---

## Cómo verificar que funciona

Con la réplica corriendo (`docker-compose up -d db db-replica`), conectarse directamente a la réplica y verificar que llegan queries de lectura:

```sql
-- En la réplica (psql -U admin -d leydata_db -h localhost -p 5434)
SELECT query, state FROM pg_stat_activity WHERE datname = 'leydata_db';
```

Si el routing funciona, las queries SELECT aparecen aquí. Si todo va al primary, las queries aparecen en el puerto 5433.

Alternativamente, activar logging de Hikari en `application.properties`:
```properties
logging.level.com.zaxxer.hikari=DEBUG
```
Los logs mostrarán `HikariRead` o `HikariWrite` por cada conexión adquirida.

---

## Pendiente — Fase 3

- **Patroni:** failover automático del primary. Si el primary cae, la réplica se promueve automáticamente. Sin Patroni, el failover es manual (cambiar la URL del write datasource y reiniciar). Ver `ARQUITECTURA-PROBLEMAS-PENDIENTES.md`.
- **HikariCP tuning:** los valores de `maximum-pool-size` son defaults razonables. Ajustar según carga real medida con Micrometer/Grafana cuando se implemente observabilidad.
