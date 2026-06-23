# Guía de Desarrollo Frontend — Ley Data

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)  
**Fecha:** 2026-06-15  

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Setup del entorno local](#2-setup-del-entorno-local)
3. [Variables de entorno](#3-variables-de-entorno)
4. [Scripts disponibles](#4-scripts-disponibles)
5. [Crear una nueva página](#5-crear-una-nueva-página)
6. [Crear un nuevo componente reutilizable](#6-crear-un-nuevo-componente-reutilizable)
7. [Agregar una ruta protegida](#7-agregar-una-ruta-protegida)
8. [Agregar un permiso](#8-agregar-un-permiso)
9. [Convenciones de código TypeScript](#9-convenciones-de-código-typescript)
10. [Convenciones de CSS Modules](#10-convenciones-de-css-modules)
11. [Flujo de trabajo con Git](#11-flujo-de-trabajo-con-git)
12. [Errores frecuentes y soluciones](#12-errores-frecuentes-y-soluciones)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Node.js | 20 | `node --version` |
| npm | 10 | `npm --version` |
| Git | cualquiera | `git --version` |

**No se usa pnpm ni yarn.** Todos los comandos del proyecto usan `npm`.

---

## 2. Setup del entorno local

### Instalar dependencias

```bash
cd frontend
npm install
```

### Crear el archivo de variables de entorno

```bash
cp .env.example .env
```

Editar `.env` con los valores del entorno local. Ver la [sección 3](#3-variables-de-entorno) para detalles.

### Levantar el servidor de desarrollo

```bash
npm run dev
```

La app estará disponible en `http://localhost:5173`.

> El backend debe estar corriendo en `http://localhost:8080` y Keycloak en `http://localhost:8180` para que el login operativo funcione. Ver `GUIA-INSTALACION.md` del backend para levantar la infraestructura completa.

---

## 3. Variables de entorno

El archivo `.env` en la raíz de `frontend/` contiene las URLs de los servicios externos:

```env
VITE_API_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8180/realms/leydata
```

| Variable | Descripción | Default local |
|---|---|---|
| `VITE_API_URL` | URL base del backend Spring Boot | `http://localhost:8080` |
| `VITE_KEYCLOAK_URL` | URL del realm de Keycloak | `http://localhost:8180/realms/leydata` |

**Reglas de Vite:**
- Solo las variables con prefijo `VITE_` son accesibles desde el código del frontend.
- Se acceden con `import.meta.env.VITE_NOMBRE_VARIABLE`.
- El archivo `.env` no se versiona (está en `.gitignore`). El archivo `.env.example` sí se versiona y sirve de plantilla.

---

## 4. Scripts disponibles

Desde la carpeta `frontend/`:

```bash
# Servidor de desarrollo con HMR
npm run dev

# Compilar para producción
npm run build

# Vista previa del build de producción
npm run preview

# Verificar errores de TypeScript sin compilar
npx tsc --noEmit

# Linting
npm run lint
```

---

## 5. Crear una nueva página

Seguir estos pasos en orden.

### 5.1 Crear el archivo de la página

```bash
# Ejemplo: nueva página de solicitudes
touch src/pages/SolicitudesPage.tsx
touch src/pages/SolicitudesPage.module.css
```

### 5.2 Estructura base de una página

```tsx
// src/pages/SolicitudesPage.tsx
import styles from './SolicitudesPage.module.css';

const SolicitudesPage = () => {
  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <h2 className={styles.title}>Solicitudes</h2>
        <p className={styles.subtitle}>Descripción de la sección</p>
      </div>

      {/* contenido de la página */}
    </div>
  );
};

export default SolicitudesPage;
```

```css
/* src/pages/SolicitudesPage.module.css */
.page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.pageHeader {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.title {
  font-size: var(--text-3xl);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.subtitle {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  margin: 0;
}
```

### 5.3 Registrar la ruta en App.tsx

Ver la [sección 7](#7-agregar-una-ruta-protegida) para los detalles.

### 5.4 Agregar el link en el Sidebar

```tsx
// src/components/layout/Sidebar.tsx
import IconSolicitudes from '...'; // o usar un SVG inline

const navItems: NavItem[] = [
  // ... items existentes
  {
    to:    '/solicitudes',
    label: 'Solicitudes',
    icon:  <IconSolicitudes />,
    roles: ['DPO', 'JEFE_DOMINIO'],
  },
];
```

---

## 6. Crear un nuevo componente reutilizable

Un componente va en `src/components/common/` solo si es usado en más de una página.

### 6.1 Estructura base de un componente

```tsx
// src/components/common/StatusBadge.tsx
import styles from './StatusBadge.module.css';

interface StatusBadgeProps {
  status: 'activo' | 'inactivo';
  label?: string;
}

const StatusBadge = ({ status, label }: StatusBadgeProps) => {
  return (
    <span className={[styles.badge, styles[`badge_${status}`]].join(' ')}>
      {label ?? status}
    </span>
  );
};

export default StatusBadge;
```

```css
/* src/components/common/StatusBadge.module.css */
.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
}

.badge_activo {
  background: #dcfce7;
  color: #16a34a;
}

.badge_inactivo {
  background: #f3f4f6;
  color: #6b7280;
}
```

### 6.2 Exportar el componente

El componente se importa directamente desde su archivo:

```tsx
import StatusBadge from '../components/common/StatusBadge';
```

No hay un archivo `index.ts` de barrel exports — cada componente se importa directamente.

---

## 7. Agregar una ruta protegida

### Importar la página en App.tsx

```tsx
// src/App.tsx
import SolicitudesPage from './pages/SolicitudesPage';
```

### Agregar la ruta dentro del portal operativo

```tsx
// src/App.tsx — dentro del bloque <Route element={<ProtectedRoute roles={[...]}><DashboardLayout /></ProtectedRoute>}>

<Route path="solicitudes" element={
  <ProtectedRoute roles={['DPO', 'JEFE_DOMINIO']}>
    <SolicitudesPage />
  </ProtectedRoute>
} />
```

### Tabla de roles disponibles

| Valor | Descripción |
|---|---|
| `'ADMIN'` | Administrador del sistema |
| `'DPO'` | Data Protection Officer |
| `'JEFE_DOMINIO'` | Responsable de área |
| `'TITULAR'` | Titular de datos (solo portal titular) |

### Agregar el fallback si es necesario

```tsx
// Si JEFE_DOMINIO accede sin permiso, redirigir a /consentimientos
<ProtectedRoute roles={['DPO']} fallback="/consentimientos">
  <SolicitudesPage />
</ProtectedRoute>
```

---

## 8. Agregar un permiso

Los permisos se definen en `src/features/auth/usePermissions.ts`.

### Agregar un nuevo permiso

```typescript
// src/features/auth/usePermissions.ts
export const usePermissions = () => {
  const { user } = useAuth();
  return {
    // ... permisos existentes

    // Nuevo permiso: puede revisar solicitudes (DPO y ADMIN)
    canReviewRequests: user?.role === 'DPO' || user?.role === 'ADMIN',
  };
};
```

### Usar el permiso en una página

```tsx
const { canReviewRequests } = usePermissions();

return (
  <>
    {canReviewRequests && (
      <Button onClick={handleAprobar}>Aprobar solicitud</Button>
    )}
  </>
);
```

---

## 9. Convenciones de código TypeScript

### Naming

| Elemento | Convención | Ejemplo |
|---|---|---|
| Componentes | PascalCase | `ConsentDrawer`, `UserCard` |
| Hooks | camelCase con prefijo `use` | `usePermissions`, `useAuth` |
| Tipos e interfaces | PascalCase | `AppUser`, `ConsentRecord` |
| Tipos union | PascalCase | `Role`, `ConsentStatus` |
| Variables y funciones | camelCase | `handleSubmit`, `filteredItems` |
| Constantes de módulo | SCREAMING_SNAKE_CASE | `MOCK_USERS`, `ROLE_LABEL` |
| Archivos de componente | PascalCase | `LoginForm.tsx` |
| Archivos de hook/util | camelCase | `formatters.ts`, `usePermissions.ts` |

### Tipado

```typescript
// ✅ Tipar siempre los props de los componentes con interface
interface Props {
  name:     string;
  role:     Role;
  onClose:  () => void;
  children?: ReactNode;
}

// ✅ Usar type para unions y aliases
type ConsentStatus = 'activo' | 'revocado' | 'pendiente' | 'expirado';

// ✅ Tipar los retornos de funciones cuando no son obvios
const parseToken = (token: string): AppUser | null => { ... };

// ❌ No usar 'any'
const data: any = ...;  // prohibido
```

### Imports

```typescript
// Orden de imports:
// 1. React y librerías externas
import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';

// 2. Features internas
import { useAuth } from '../features/auth/AuthContext';

// 3. Componentes
import Button from '../components/common/Button';

// 4. Utilidades y tipos
import { formatDate } from '../utils/formatters';
import type { ConsentRecord } from '../utils/mockData';

// 5. Estilos (siempre al final)
import styles from './MiComponente.module.css';
```

### Manejo de eventos

```typescript
// ✅ Tipar los handlers
const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => { ... };
const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => { ... };
const handleSubmit = (e: React.FormEvent) => {
  e.preventDefault();
  // ...
};
```

### Renderizado condicional

```tsx
// ✅ Operador && para bloques opcionales
{canCreate && <Button>Crear</Button>}

// ✅ Ternario para alternativas
{loading ? <Spinner /> : <ContenidoPrincipal />}

// ✅ Early return para casos vacíos
if (!user) return null;
if (items.length === 0) return <EmptyState />;
```

---

## 10. Convenciones de CSS Modules

### Naming de clases

```css
/* ✅ camelCase para clases descriptivas */
.pageHeader { }
.cardTitle { }
.filterBar { }
.tableWrapper { }

/* ✅ guión bajo para modificadores de estado/variante */
.badge_activo { }
.badge_revocado { }
.check_ok { }
.alert_high { }
.role_admin { }

/* ❌ No usar kebab-case */
.page-header { }    /* incorrecto */
.card-title { }     /* incorrecto */
```

### Uso de variables CSS

```css
/* ✅ Siempre usar variables del sistema de diseño */
.card {
  background:    var(--color-surface);
  border:        1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow:    var(--shadow-sm);
}

.title {
  font-size:  var(--text-2xl);
  color:      var(--color-text-primary);
  font-weight: 700;
}

/* ❌ No usar valores hardcodeados salvo casos específicos */
.card {
  background: #ffffff;    /* incorrecto */
  color: #111827;         /* incorrecto */
}
```

### Estructura de un archivo CSS Module

```css
/* 1. Layout principal de la página/componente */
.page { }
.card { }

/* 2. Header y acciones */
.pageHeader { }
.title { }
.subtitle { }
.headerActions { }

/* 3. Contenido principal */
.content { }
.section { }
.sectionTitle { }

/* 4. Elementos de tabla */
.tableWrapper { }
.table { }

/* 5. Estado vacío */
.empty { }
.emptyIcon { }
.emptyTitle { }

/* 6. Modificadores de estado */
.badge_activo { }
.badge_revocado { }

/* 7. Responsive */
@media (max-width: 768px) {
  .layout { grid-template-columns: 1fr; }
}
```

### Combinar clases

```tsx
// ✅ Usando join
<span className={[styles.badge, styles[`badge_${status}`]].join(' ')}>

// ✅ Usando template literal
<div className={`${styles.card} ${isActive ? styles.cardActive : ''}`}>

// ✅ Filtrando valores falsy
<div className={[styles.item, isSelected && styles.itemSelected].filter(Boolean).join(' ')}>
```

---

## 11. Flujo de trabajo con Git

### Ramas del proyecto

| Rama | Propósito |
|---|---|
| `main` | Código estable, solo merge de ramas completadas |
| `feature/nombre-feature` | Nueva funcionalidad |
| `fix/nombre-fix` | Corrección de bugs |
| `docs/nombre-doc` | Documentación |

### Flujo para un cambio

```bash
# 1. Asegurarse de estar en main y actualizado
git checkout main
git pull

# 2. Crear la rama desde main
git checkout -b feature/nueva-funcionalidad

# 3. Desarrollar con commits descriptivos
git add src/pages/NuevaPagina.tsx src/pages/NuevaPagina.module.css
git commit -m "feat: agrega página de solicitudes de propósito"

# 4. Verificar que compila sin errores antes de subir
npx tsc --noEmit

# 5. Subir la rama
git push -u origin feature/nueva-funcionalidad
```

### Mensajes de commit

```bash
# Formato: tipo: descripción en minúsculas

feat: agrega tabla de auditoría con filtros por acción
fix: corrige redirección incorrecta para JEFE_DOMINIO
style: ajusta padding del sidebar en mobile
refactor: extrae lógica de parseo de JWT a función separada
docs: agrega documentación del flujo de autenticación
```

---

## 12. Errores frecuentes y soluciones

### Error: `Cannot find module '../features/auth/AuthContext'`

**Causa:** Alias de rutas no configurado en Vite.  
**Solución:** Usar rutas relativas correctas. El proyecto no tiene alias (`@/`) configurados.

```tsx
// ✅ Correcto (ruta relativa)
import { useAuth } from '../../features/auth/AuthContext';

// ❌ Incorrecto (alias no configurado)
import { useAuth } from '@/features/auth/AuthContext';
```

---

### Error: `Property 'password' does not exist on type 'AppUser'`

**Causa:** `password` es opcional en `AppUser` (solo existe en usuarios mock titulares).  
**Solución:** Usar operador de optional chaining o verificar antes de acceder.

```tsx
// ✅ Correcto
currentPassword={user.password}   // TypeScript acepta string | undefined

// ✅ Correcto con fallback
const pwd = user.password ?? '';
```

---

### Error: TypeScript `noUnusedLocals` o `noUnusedParameters`

**Causa:** El `tsconfig.app.json` tiene activados `noUnusedLocals` y `noUnusedParameters`.  
**Solución:** Eliminar la variable/parámetro o prefijarlo con `_` si es intencional.

```typescript
// ✅ Si el parámetro es requerido por la firma pero no se usa
const handleSave = (_current: string, next: string) => { ... };
```

---

### Error: `useAuth must be used within AuthProvider`

**Causa:** Un componente intenta usar `useAuth()` fuera del árbol de `AuthProvider`.  
**Solución:** Verificar que `<AuthProvider>` envuelva el componente en `main.tsx` o `App.tsx`.

---

### El login retorna `401` con credenciales correctas

**Causas posibles:**
1. Keycloak no está corriendo → verificar `docker ps`
2. El realm `leydata` no existe → ejecutar el script `setup-keycloak.sh`
3. La URL de Keycloak en `.env` no es correcta → verificar `VITE_KEYCLOAK_URL`

```bash
# Verificar que Keycloak responde
curl http://localhost:8180/realms/leydata/.well-known/openid-configuration
```

---

### La app compila pero el login no redirige al backend

**Causa:** El backend no está corriendo.  
**Solución:** El login llama a Keycloak (puerto 8180), no al backend (puerto 8080). Keycloak puede estar corriendo sin el backend. El problema puede ser en llamadas posteriores de la API.

---

### HMR no funciona / cambios no se reflejan

**Solución:**

```bash
# Limpiar cache de Vite
rm -rf node_modules/.vite
npm run dev
```

---

### `npm install` falla con errores de peer dependencies

**Causa:** Conflicto de versiones de `@types/react-router-dom` (v5) con React Router v7.  
**Solución:** Es una incompatibilidad de tipos conocida, no afecta el funcionamiento. Si el error bloquea, usar:

```bash
npm install --legacy-peer-deps
```
