# config/ — Configuración central del backend

Este paquete contiene la configuración de infraestructura transversal a toda la aplicación.

---

## Archivos

| Archivo | Responsabilidad |
|---------|----------------|
| `SecurityConfig.java` | Filtro JWT, CORS, rutas públicas vs protegidas, roles por endpoint |
| `OpenApiConfig.java` | Swagger UI — esquema OAuth2 para autenticación interactiva |
| `GlobalExceptionHandler.java` | Mapeo global de excepciones → respuestas HTTP con código correcto |
| `DataSourceType.java` | Enum `WRITE / READ` — clave de routing entre primary y réplica |
| `ReadWriteRoutingDataSource.java` | Extiende `AbstractRoutingDataSource`; decide el pool según el flag de transacción |
| `DataSourceConfig.java` | Declara los dos pools HikariCP y el `@Primary DataSource` con `LazyConnectionDataSourceProxy` |

---

## Read/Write Routing

### Cómo funciona

```
@Transactional(readOnly = true)  →  pool HikariRead  →  Réplica   :5434
@Transactional                   →  pool HikariWrite →  PgBouncer :5435 → Primary :5433
```

`ReadWriteRoutingDataSource.determineCurrentLookupKey()` consulta
`TransactionSynchronizationManager.isCurrentTransactionReadOnly()` en cada adquisición de conexión.

`LazyConnectionDataSourceProxy` garantiza que esa consulta ocurra **después** de que
`JpaTransactionManager` haya activado el flag `readOnly`, no antes.

### Requisito en services

Todo método de solo lectura debe llevar `@Transactional(readOnly = true)`.
Sin esa anotación, la query va al primary aunque sea un SELECT.

```java
@Transactional(readOnly = true)   // ← activa el routing a la réplica
public List<AgreementResponse> findAll() { ... }
```

### Propiedades (application.properties)

```properties
spring.datasource.write.jdbc-url=jdbc:postgresql://localhost:5435/...
spring.datasource.read.jdbc-url=jdbc:postgresql://localhost:5434/...
```

> Usar `jdbc-url` (con guión), no `url`. HikariCP no reconoce `url` via `@ConfigurationProperties`.

### DDL en arranque

Durante `ddl-auto=update`, no hay transacción activa → `isCurrentTransactionReadOnly()` devuelve `false`
→ `defaultTargetDataSource` es `writeDataSource` → Hibernate hace DDL solo en el primary. Correcto.

---

## Ver también

- `documentacionback/DATASOURCE-ROUTING.md` — doc completo con diagrama, verificación y pendientes
- `documentacionback/ARQUITECTURA-PROBLEMAS-PENDIENTES.md` — checklist pre-producción
