## Ley Data: Gestor de Consentimientos

Ley Data es una solución tecnológica diseñada para dar cumplimiento integral a la Ley 21.719 de Protección de Datos Personales en Chile.

El sistema permite a las organizaciones capturar, administrar y auditar el consentimiento informado de manera trazable, inmutable y con soberanía total de los datos mediante una infraestructura On-Premise.

## Propósito del Proyecto

Desarrollar un ecosistema que garantice la transparencia y legalidad en el tratamiento de datos personales.

Acceso
Rectificación
Cancelación
Oposición

Todo esto de forma autónoma y segura.

## Set Tecnológico

**Frontend**

React 18 + Vite (TypeScript)

Widget ligero y embebible

**Backend**

Java 17

Spring Boot 3

Spring Security (JWT RS256)

**Base de Datos**

PostgreSQL 16

Triggers para inmutabilidad del log de auditoría

**Infraestructura**

Docker Compose

Despliegue portable Cloud Agnostic

## Estructura del Proyecto (Frontend)

Para garantizar la mantenibilidad y una organización modular, el repositorio está estructurado de la siguiente forma:

```text
leydata/
├── public/              # Archivos estáticos (robots.txt, favicon, etc.)
├── src/                 # Código fuente principal
│   ├── assets/          # Imágenes, fuentes y multimedia
│   ├── components/      # Componentes reutilizables
│   │   ├── common/      # Uso global
│   │   └── layout/      # Header, Footer, Sidebar
│   ├── features/        # Módulos funcionales por dominio
│   ├── hooks/           # Custom Hooks
│   ├── layouts/         # Layouts principales
│   ├── services/        # Integración con APIs
│   ├── store/           # Manejo de estado
│   ├── utils/           # Funciones auxiliares
│   ├── App.tsx          # Componente raíz
│   ├── main.tsx         # Entry point
│   └── index.css        # Estilos globales
├── .gitignore
├── package.json
└── tsconfig.json
```

## Características Principales

**Captura Granular**

Selección de consentimientos independientes por finalidad (Art. 12)

**Personalización Dinámica**

Cambio de colores, logos y textos sin redespliegue (v2.1)

**Auditoría Inmutable**

Registro de evidencia (PDF + Hash SHA-256) inalterable

**Soberanía de Datos**

Almacenamiento local sin dependencia de servicios cloud (RNF-01)

## Equipo de Trabajo

Samantha Munizaga — Gerente de Proyectos

Bastián Burgos — Backend & Base de Datos

Victor Alvarado — Quality Assurance & Compliance Officer

Aurora Gonzalez — Frontend & UI

## Instalación y Despliegue

Este proyecto utiliza Docker Compose para levantar todos los servicios:

```text
docker compose up -d
```

Nota: El sistema está diseñado para operar bajo infraestructura controlada por el cliente, garantizando la confidencialidad de los datos.

## Enfoque

Ley Data implementa el principio de:

Privacy by Design & Privacy by Default

Protección de datos desde el diseño y por defecto.

# Guía Levantando backend LeyData

Esta guía explica cómo correr el proyecto en **Windows (PowerShell)**, **Windows (WSL)** y **macOS**.

## 1. Requisitos Previos (Para todos)

- **Docker Desktop:** Instalado y corriendo.
- **Java 17:** Instalado (pueden verificar con `java -version`).
- **Archivo `.env`**

## 2. Paso 1: Levantar la Base de Datos

En cualquier sistema, abre la terminal en la raíz del proyecto (`/leydata`) y ejecuta:
`docker-compose up -d`
_Si usas Docker Desktop en Windows o Mac, asegúrate de que el motor esté encendido._

## 3. Paso 2: Correr el Backend (Spring Boot)

Entra a la carpeta `backend/`. El comando cambia según tu sistema:

### macOS y Windows (WSL / Ubuntu)

1. **Dar permisos (Solo la primera vez):**
   ```
   chmod +x mvnw
   ```
2. **Ejecutar:**
   ```
   DB_USER= DB_PASS= DB_NAME=leydata_db ./mvnw spring-boot:run
   ```

### Windows (PowerShell) - Nativo

En la terminal azul de Windows:

1. **Configurar variables y ejecutar:**
   ```
   $env:DB_USER=""; $env:DB_PASS=""; $env:DB_NAME="leydata_db"; .\mvnw.cmd spring-boot:run
   ```

- **Uso de VS Code (Recomendado):**
  Para no estar escribiendo las variables en la terminal a cada rato, se recomienda instalar la extensión **"Spring Boot Extension Pack"**.
  Luego, pueden ir a la pestaña **Spring Boot Dashboard**, darle click derecho al proyecto -> **Debug** o **Run**.
