# Arquitectura Frontend — Ley Data

**Proyecto:** Ley Data — Sistema de gestión de consentimiento (Ley 21.719)  
**Rol:** Frontend Developer  
**Fecha:** 2026-06-15  

---

## Índice

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Stack tecnológico](#2-stack-tecnológico)
3. [Arquitectura en capas](#3-arquitectura-en-capas)
4. [Estructura de carpetas](#4-estructura-de-carpetas)
5. [Portales del sistema](#5-portales-del-sistema)
6. [Sistema de enrutamiento](#6-sistema-de-enrutamiento)
7. [Gestión de estado](#7-gestión-de-estado)
8. [Sistema de diseño](#8-sistema-de-diseño)
9. [Decisiones de arquitectura](#9-decisiones-de-arquitectura)
10. [Estado de integración con el backend](#10-estado-de-integración-con-el-backend)

---

## 1. Resumen ejecutivo

El frontend de Ley Data es una Single Page Application (SPA) construida con React 19 que implementa un sistema de doble portal: uno operativo para los administradores, DPOs y jefes de dominio de la organización, y otro para los titulares de datos que desean consultar o revocar sus consentimientos.

La arquitectura prioriza simplicidad y mantenibilidad por sobre abstracciones prematuras. No se utilizan librerías de estado global pesadas ni frameworks de estilos externos — el estado se gestiona con React Context y los estilos con CSS Modules sobre un sistema de variables CSS propio.

---

## 2. Stack tecnológico

| Tecnología | Versión | Rol |
|---|---|---|
| React | 19.2.4 | Librería de UI |
| TypeScript | 6.0.2 | Tipado estático |
| Vite | 8.0.4 | Build tool y dev server |
| React Router DOM | 7.15.0 | Enrutamiento SPA |
| CSS Modules | nativo | Estilos con scope local |
| fetch API | nativo del browser | Llamadas HTTP |

**Librerías de desarrollo:**

| Herramienta | Versión | Rol |
|---|---|---|
| ESLint | 9.x | Linting |
| eslint-plugin-react-hooks | 7.x | Reglas React |
| typescript-eslint | 8.x | Reglas TypeScript |

**No se utilizan** librerías de estado global (Redux, Zustand, Jotai), frameworks de estilos (Tailwind, styled-components), librerías de formularios (React Hook Form, Formik), librerías de validación (Zod, Yup) ni clientes HTTP adicionales (axios, ky). Todas estas decisiones están justificadas en la [sección 9](#9-decisiones-de-arquitectura).

---

## 3. Arquitectura en capas

```
┌─────────────────────────────────────────────────────────────────────┐
│                          PÁGINAS (pages/)                           │
│  DashboardPage · ConsentimientosPage · UsuariosPage · AuditPage    │
│  TemplatesPage · CompliancePage · PerfilPage · TitularPortalPage   │
├─────────────────────────────────────────────────────────────────────┤
│                       LAYOUTS (layouts/)                            │
│           DashboardLayout              TitularLayout                │
├─────────────────────────────────────────────────────────────────────┤
│                     COMPONENTES (components/)                       │
│  common/: Button · Badge · Modal · LoginForm · ConsentCard ...     │
│  layout/: Header · Sidebar                                          │
├─────────────────────────────────────────────────────────────────────┤
│                       FEATURES (features/)                          │
│  auth/: AuthContext · usePermissions · mockUsers                   │
│  shared/: ProtectedRoute                                            │
├─────────────────────────────────────────────────────────────────────┤
│                      UTILIDADES (utils/ · constants/)               │
│  formatters · mockData · labels                                     │
├─────────────────────────────────────────────────────────────────────┤
│                    ESTILOS GLOBALES (styles/)                       │
│              variables.css · index.css · App.css                   │
└─────────────────────────────────────────────────────────────────────┘
         ↕ autenticación                    ↕ datos (futuro)
┌──────────────────────┐        ┌──────────────────────────────────┐
│  Keycloak :8180      │        │  Spring Boot API :8080           │
│  /realms/leydata     │        │  /api/users · /api/domains ...   │
└──────────────────────┘        └──────────────────────────────────┘
```

**Flujo de datos:**

```
Usuario interactúa
      │
      ▼
Página (estado local con useState)
      │
      ├─── lee contexto de auth ─────► AuthContext (usuario + accessToken)
      │
      ├─── lee permisos ─────────────► usePermissions (canCreate, canViewAll…)
      │
      └─── renderiza componentes ────► components/common/ + layout/
```

---

## 4. Estructura de carpetas

```
frontend/
├── public/                        # Assets estáticos (favicon, etc.)
├── src/
│   ├── main.tsx                   # Punto de entrada — monta <App /> en el DOM
│   ├── App.tsx                    # Router raíz con todas las rutas y ProtectedRoutes
│   ├── App.css                    # Reset y estilos base de la app
│   ├── index.css                  # Variables CSS de uso global, body
│   │
│   ├── pages/                     # Una carpeta por vista completa de la app
│   │   ├── DashboardPage.tsx
│   │   ├── DashboardPage.module.css
│   │   ├── ConsentimientosPage.tsx
│   │   ├── ConsentimientosPage.module.css
│   │   ├── NuevoConsentimientoPage.tsx
│   │   ├── NuevoConsentimientoPage.module.css
│   │   ├── UsuariosPage.tsx
│   │   ├── UsuariosPage.module.css
│   │   ├── AuditTrailPage.tsx
│   │   ├── AuditTrailPage.module.css
│   │   ├── TemplatesPage.tsx
│   │   ├── TemplatesPage.module.css
│   │   ├── CompliancePage.tsx
│   │   ├── CompliancePage.module.css
│   │   ├── PerfilPage.tsx
│   │   ├── PerfilPage.module.css
│   │   ├── LoginPage.tsx
│   │   ├── LoginPage.module.css
│   │   ├── TitularLoginPage.tsx
│   │   ├── TitularPortalPage.tsx
│   │   ├── TitularPortalPage.module.css
│   │   └── NotFoundPage.tsx
│   │   └── NotFoundPage.module.css
│   │
│   ├── components/
│   │   ├── common/                # Componentes reutilizables entre páginas
│   │   │   ├── Badge.tsx          # Indicador de estado de consentimiento
│   │   │   ├── Button.tsx         # Botón con variantes (primary/secondary/ghost/danger)
│   │   │   ├── ConsentCard.tsx    # Tarjeta de consentimiento (portal titular)
│   │   │   ├── ConsentDrawer.tsx  # Panel lateral con detalle de consentimiento
│   │   │   ├── ConsentPreview.tsx # Vista previa de formulario de plantilla
│   │   │   ├── LoginForm.tsx      # Formulario de login (operativo y titular)
│   │   │   ├── Modal.tsx          # Modal versátil: centrado o drawer lateral
│   │   │   ├── PerfilForm.tsx     # Formulario de perfil con cambio de contraseña
│   │   │   └── RevokeModal.tsx    # Modal de revocación de consentimiento
│   │   │
│   │   └── layout/                # Componentes estructurales de la UI
│   │       ├── Header.tsx         # Encabezado: búsqueda, notificaciones, usuario
│   │       └── Sidebar.tsx        # Menú lateral con nav items filtrados por rol
│   │
│   ├── features/
│   │   ├── auth/                  # Todo lo relacionado con autenticación
│   │   │   ├── AuthContext.tsx    # Context + Provider + hook useAuth
│   │   │   ├── mockUsers.ts       # Tipos, datos mock y helpers de usuarios
│   │   │   └── usePermissions.ts  # Hook que expone permisos según rol
│   │   │
│   │   └── shared/
│   │       └── ProtectedRoute.tsx # HOC para proteger rutas por rol
│   │
│   ├── layouts/
│   │   ├── DashboardLayout.tsx    # Shell del portal operativo (Header + Sidebar + Outlet)
│   │   ├── DashboardLayout.module.css
│   │   ├── TitularLayout.tsx      # Shell del portal titular
│   │   └── TitularLayout.module.css
│   │
│   ├── constants/
│   │   └── labels.ts              # Mapeos de roles y acciones a texto legible
│   │
│   ├── utils/
│   │   ├── formatters.ts          # Funciones de formato: fechas, iniciales
│   │   └── mockData.ts            # Datos mock: consentimientos, auditoría, métricas
│   │
│   └── styles/
│       └── variables.css          # Tokens CSS: colores, tipografía, espaciado, sombras
│
├── documentacionfront/            # Documentación técnica del frontend
├── .env                           # Variables de entorno (no se versiona)
├── .env.example                   # Plantilla de variables de entorno
├── package.json
├── tsconfig.json
├── tsconfig.app.json
└── vite.config.ts
```

**Convención de colocación:**

- Cada página vive en `pages/` junto a su `.module.css`.
- Un componente va a `common/` si es usado por más de una página.
- Si un componente es solo para una página, vive en esa misma página (como función interna o archivo separado en la misma carpeta).
- Los hooks que solo tienen sentido en el contexto de auth van en `features/auth/`.

---

## 5. Portales del sistema

El sistema tiene dos portales completamente independientes, cada uno con su propio layout, login y flujo de sesión.

```
                        LEY DATA
                           │
           ┌───────────────┴───────────────┐
           │                               │
    Portal Operativo                 Portal Titular
    /login                           /titular/login
    DashboardLayout                  TitularLayout
           │                               │
    Roles permitidos:            Roles permitidos:
    · ADMIN                          · TITULAR
    · DPO
    · JEFE_DOMINIO
```

| Característica | Portal Operativo | Portal Titular |
|---|---|---|
| Ruta de login | `/login` | `/titular/login` |
| Autenticación | Keycloak (real) | Mock en memoria |
| Layout | `DashboardLayout` | `TitularLayout` |
| Roles | ADMIN, DPO, JEFE_DOMINIO | TITULAR |
| Rutas disponibles | `/`, `/consentimientos`, `/usuarios`, `/auditoria`, `/cumplimiento`, `/plantillas`, `/perfil` | `/titular/mis-consentimientos`, `/titular/perfil` |

---

## 6. Sistema de enrutamiento

El enrutamiento usa **React Router DOM v7** con `BrowserRouter`. La protección de rutas se implementa con el componente `ProtectedRoute`.

### Árbol de rutas completo

```
BrowserRouter
├── /login                          → LoginPage [público]
├── /titular/login                  → TitularLoginPage [público]
│
├── ProtectedRoute [ADMIN | DPO | JEFE_DOMINIO]
│   └── DashboardLayout (Outlet)
│       ├── / (index)               → ProtectedRoute [ADMIN | DPO] → DashboardPage
│       │                             fallback: /consentimientos
│       ├── /consentimientos        → ProtectedRoute [DPO | JEFE_DOMINIO] → ConsentimientosPage
│       ├── /consentimientos/nuevo  → ProtectedRoute [DPO] → NuevoConsentimientoPage
│       ├── /usuarios               → ProtectedRoute [ADMIN] → UsuariosPage
│       ├── /auditoria              → ProtectedRoute [ADMIN | DPO] → AuditTrailPage
│       ├── /cumplimiento           → ProtectedRoute [ADMIN | DPO] → CompliancePage
│       ├── /plantillas             → ProtectedRoute [DPO] → TemplatesPage
│       └── /perfil                 → ProtectedRoute [ADMIN | DPO | JEFE_DOMINIO] → PerfilPage
│
├── /titular                        → ProtectedRoute [TITULAR] fallback: /titular/login
│   └── TitularLayout (Outlet)
│       ├── /titular (index)        → Navigate to /titular/mis-consentimientos
│       ├── /titular/mis-consentimientos → TitularPortalPage
│       └── /titular/perfil         → PerfilPage
│
└── *                               → NotFoundPage
```

### ProtectedRoute

```tsx
// src/features/shared/ProtectedRoute.tsx
interface Props {
  roles?: Role[];
  fallback?: string;   // ruta de redirección si el rol no tiene acceso (default: /consentimientos)
  children: ReactNode;
}
```

Lógica:
1. Si no hay usuario autenticado → redirige a `/login`
2. Si hay usuario pero su rol no está en `roles[]` → redirige a `fallback`
3. Si el rol está permitido → renderiza `children`

---

## 7. Gestión de estado

El proyecto no utiliza librerías de estado global. El estado se divide en dos niveles:

### Estado global — React Context

Solo el estado de autenticación se gestiona globalmente, a través de `AuthContext`.

```
AuthContext (global)
├── user: AppUser | null          → quién está logueado
├── accessToken: string | null    → JWT para llamadas al backend
├── login(user, tokens?)          → inicia sesión (Keycloak o mock)
└── logout()                      → limpia sesión
```

El `AuthProvider` envuelve toda la aplicación en `main.tsx`, lo que hace el contexto disponible desde cualquier componente.

### Estado local — useState por componente

Cada página gestiona su propio estado con `useState`:

| Tipo de estado | Ejemplos | Patrón |
|---|---|---|
| Filtros | filtro por área, búsqueda de texto, estado seleccionado | `const [filtro, setFiltro] = useState('')` |
| UI | modal abierto/cerrado, tab activa, ítem seleccionado | `const [open, setOpen] = useState(false)` |
| Formularios | campos de input, errores de validación | `const [form, setForm] = useState({...})` |
| Datos | lista de usuarios, registros filtrados | `const [items, setItems] = useState([])` |

No se usa `useReducer` — los formularios son lo suficientemente simples para `useState` individual.

### Flujo de datos

```
AuthContext
    │
    ├── useAuth()         → páginas acceden a user y accessToken
    └── usePermissions()  → páginas verifican permisos

Página
    │
    ├── useState()        → estado local de la página
    ├── datos de mockData → mientras el backend no está integrado
    └── renderiza → Componentes (reciben props, no acceden al contexto directamente)
```

Los componentes reutilizables (`Badge`, `Button`, `Modal`, etc.) son **stateless** o manejan solo su estado interno de UI. No acceden al `AuthContext` directamente.

---

## 8. Sistema de diseño

El sistema de diseño se basa en variables CSS definidas en `src/styles/variables.css` e importadas globalmente desde `src/index.css`.

### Tokens de color

```css
/* Primarios */
--color-primary:        #4361ee   /* Azul principal — botones, links, activos */
--color-primary-dark:   #3451d1   /* Hover de primary */
--color-primary-light:  #eef1fd   /* Fondos de elementos activos */

/* Superficies */
--color-bg:             #f8f9fc   /* Fondo general de la app */
--color-surface:        #ffffff   /* Cards, paneles, modales */
--color-border:         #e8ecf0   /* Bordes de inputs, cards, divisores */

/* Texto */
--color-text-primary:   #111827   /* Títulos, texto principal */
--color-text-secondary: #6b7280   /* Labels, texto secundario */
--color-text-muted:     #9ca3af   /* Placeholders, texto deshabilitado */

/* Semánticos */
--color-success:        #16a34a   /* Éxito, estado "activo" */
--color-warning:        #d97706   /* Advertencia, estado "pendiente" */
--color-danger:         #dc2626   /* Error, estado "revocado", acciones destructivas */
--color-info:           #0284c7   /* Información */

/* Gráficos */
--color-chart-granted:  #22c55e
--color-chart-rejected: #ef4444
--color-chart-revoked:  #f97316

/* Sidebar */
--sidebar-width:        256px
--sidebar-bg:           #0f172a
--sidebar-text:         #94a3b8
```

### Tokens de tipografía

```css
--font-sans:   'Inter', system-ui, -apple-system, sans-serif

--text-xs:     0.75rem    /* 12px — labels, badges, metadatos */
--text-sm:     0.875rem   /* 14px — texto general de la app */
--text-base:   1rem       /* 16px — texto de contenido */
--text-lg:     1.125rem   /* 18px — subtítulos de sección */
--text-xl:     1.25rem    /* 20px — títulos de card */
--text-2xl:    1.5rem     /* 24px — subtítulos de página */
--text-3xl:    1.875rem   /* 30px — títulos de página */
```

### Tokens de espaciado y forma

```css
--radius-sm:   8px
--radius-md:   12px
--radius-lg:   16px

--shadow-sm:   0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04)
--shadow-md:   0 4px 16px rgba(0,0,0,0.08)
--shadow-lg:   0 8px 32px rgba(0,0,0,0.12)

--header-height: 72px
```

### CSS Modules — convenciones

Cada componente y página tiene su propio archivo `.module.css`. Las clases usan **camelCase**:

```css
/* ✅ Correcto */
.pageHeader { ... }
.cardTitle { ... }
.filterBar { ... }

/* Modificadores de estado — con guión bajo */
.badge_activo { ... }
.badge_revocado { ... }
.check_ok { ... }
.alert_high { ... }
```

Las variantes de componente se aplican como clases adicionales:
```tsx
// Button.tsx
<button className={[styles.btn, styles[variant], styles[size]].join(' ')}>
```

---

## 9. Decisiones de arquitectura

### ¿Por qué React Context en lugar de Redux o Zustand?

El único estado verdaderamente global es el usuario autenticado y su token. No hay estado compartido entre páginas no relacionadas. React Context es suficiente y no añade dependencias externas.

### ¿Por qué CSS Modules en lugar de Tailwind?

CSS Modules permite estilos con scope automático sin conflictos, mantiene la separación de concerns, y el resultado final son clases CSS convencionales que cualquier integrante del equipo puede leer. El sistema de variables CSS propio garantiza consistencia visual sin necesitar un framework de utilidades.

### ¿Por qué fetch nativo en lugar de axios?

La aplicación tiene muy pocas llamadas HTTP directas (actualmente solo el login en Keycloak). No se justifica añadir una dependencia de ~15KB para ese caso de uso. Cuando se integren los endpoints del backend, se evaluará `openapi-fetch` como wrapper tipado sobre fetch.

### ¿Por qué no hay librerías de formularios?

Los formularios del proyecto son de complejidad baja a media. El patrón `useState` por campo con validación inline es suficiente y más legible que la abstracción que añaden librerías como React Hook Form en formularios sencillos.

### ¿Por qué Vite en lugar de Create React App?

Create React App está abandonado. Vite ofrece HMR instantáneo, build production significativamente más rápido y configuración mínima. Es el estándar actual del ecosistema React.

---

## 10. Estado de integración con el backend

| Módulo | Estado | Fuente de datos |
|---|---|---|
| Login operativo | ✅ Integrado | Keycloak `/protocol/openid-connect/token` |
| Auto-refresh de token | ✅ Implementado | Keycloak, cada 4.5 minutos |
| Gestión de usuarios | ⏳ Pendiente | Mock en memoria (MOCK_USERS) |
| Consentimientos | ⏳ Pendiente | Mock en memoria (consentRecords) |
| Auditoría | ⏳ Pendiente | Mock en memoria (auditEvents) |
| Métricas del dashboard | ⏳ Pendiente | Mock en memoria (summaryCards, chartData) |
| Plantillas | ⏳ Pendiente | Estado local en TemplatesPage |
| Cumplimiento legal | ⏳ Pendiente | Hardcoded en CompliancePage |
| Login titular | ⏳ Pendiente | Mock en memoria (MOCK_USERS TITULAR) |

La estrategia de integración progresiva está documentada en `INTEGRACION-BACKEND.md`.
