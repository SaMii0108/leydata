# Módulo: Dominios

## Descripción

El módulo de Dominios gestiona las áreas organizacionales de la empresa — la unidad de segmentación sobre la que se agrupan las Finalidades y las Solicitudes de Finalidad, y a la que se asigna un `JEFE_DOMINIO`. Es un módulo de administración base: no tiene workflow de aprobación ni versionado, solo un ciclo de vida activo/inactivo.

---

## Reglas de negocio

1. `CODE` es único e inmutable — se usa como identificador en logs de auditoría.
2. Los dominios nunca se eliminan físicamente, solo se desactivan (`ACTIVE = false`).
3. Desactivar un dominio es una señal organizacional: no cancela `PurposeRequests` pendientes ni desactiva las `Purposes` del dominio.
4. `JEFE_ID` es opcional al crear el dominio — puede asignarse después desde la edición de usuario.
5. Solo un usuario con rol `JEFE_DOMINIO` puede asignarse como jefe de un dominio.
6. La asignación de jefe no se guarda en `Domains` — vive en `user_domains` a través del módulo `userdomain/`, y desde la iteración de un solo dominio por jefe, un `JEFE_DOMINIO` no puede administrar más de un dominio a la vez.
7. No se puede desactivar un dominio ya inactivo, ni reactivar uno ya activo.

---

## Estados

```
ACTIVE = true  ⇄  ACTIVE = false
```

No hay workflow de aprobación como en Templates o PrivacyDocuments — es un simple toggle bidireccional (`deactivate` / `reactivate`), sin más restricción de transición que no repetir el estado actual.

---

## Casos de uso

1. Crear dominio (con o sin `JEFE_ID` asignado)
2. Listar todos los dominios (activos e inactivos)
3. Desactivar dominio
4. Reactivar dominio

---

## Endpoints

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/domains` | Crear dominio [ADMIN] |
| `GET` | `/api/domains/all` | Listar todos los dominios, incluidos inactivos [ADMIN] |
| `POST` | `/api/domains/{domainId}/deactivate` | Desactivar dominio [ADMIN] |
| `POST` | `/api/domains/{domainId}/reactivate` | Reactivar dominio [ADMIN] |

---

## Estructura del módulo

```
orgdomain/
  application/
    dto/
      CreateDomainRequest.java
      DomainResponse.java
    service/
      DomainService.java
  domain/
    exception/
      DomainNotFoundException.java
  infrastructure/
    persistence/
      DomainsRepository.java
  web/
    DomainController.java
```
