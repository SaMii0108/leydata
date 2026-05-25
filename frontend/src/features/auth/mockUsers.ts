/**
 * Roles del sistema — alineados con la API backend:
 *  ADMIN          → ROLE_ADMIN
 *  DPO            → ROLE_DPO
 *  JEFE_DOMINIO   → ROLE_JEFE_DOMINIO
 *  TITULAR        → solo frontend (portal de titulares, sin API equivalente)
 */
export type Role = 'ADMIN' | 'DPO' | 'JEFE_DOMINIO' | 'TITULAR';

export interface AppUser {
  id: string;
  name: string;
  email: string;
  password: string;
  /** Todos los roles asignados al usuario */
  roles: Role[];
  /** Rol activo para la sesión actual (elegido en pantalla multirol o único rol) */
  role: Role;
  /** Dominio(s) asignados — aplica solo a JEFE_DOMINIO */
  domains: string[];
  /** Cuenta habilitada (false = desactivada temporalmente por admin) */
  active: boolean;
  /** Bloqueada permanentemente (true = no puede iniciar sesión) */
  blocked: boolean;
}

export const MOCK_USERS: AppUser[] = [
  // Usuarios internos – rol único
  { id: 'u1', name: 'Ana Torres',          email: 'ana@leydata.cl',      password: 'ley2024', roles: ['ADMIN'],         role: 'ADMIN',        domains: [],            active: true,  blocked: false },
  { id: 'u2', name: 'Carlos Ruiz',         email: 'carlos@leydata.cl',   password: 'ley2024', roles: ['DPO'],           role: 'DPO',          domains: [],            active: true,  blocked: false },
  { id: 'u3', name: 'María López',         email: 'maria@leydata.cl',    password: 'ley2024', roles: ['JEFE_DOMINIO'],  role: 'JEFE_DOMINIO', domains: ['Recursos Humanos'], active: true, blocked: false },
  { id: 'u4', name: 'Pedro Soto',          email: 'pedro@leydata.cl',    password: 'ley2024', roles: ['JEFE_DOMINIO'],  role: 'JEFE_DOMINIO', domains: ['Marketing'],       active: true, blocked: false },
  { id: 'u5', name: 'Lucía Vargas',        email: 'lucia@leydata.cl',    password: 'ley2024', roles: ['JEFE_DOMINIO'],  role: 'JEFE_DOMINIO', domains: ['Tecnología'],      active: true, blocked: false },
  // Usuario multirol demo (ADMIN + DPO)
  { id: 'u6', name: 'Aurora González',     email: 'aurora@leydata.cl',   password: 'ley2024', roles: ['ADMIN', 'DPO'], role: 'ADMIN',        domains: [],            active: true,  blocked: false },
  // Titulares de datos (solo portal titular)
  { id: 't1', name: 'María José Fuentes',  email: 'mjfuentes@email.com', password: 'ley2024', roles: ['TITULAR'],      role: 'TITULAR',      domains: [],            active: true,  blocked: false },
  { id: 't2', name: 'Carlos Andrés Pérez', email: 'caperez@email.com',   password: 'ley2024', roles: ['TITULAR'],      role: 'TITULAR',      domains: [],            active: true,  blocked: false },
  { id: 't3', name: 'Valentina Rojas',     email: 'vrojas@email.com',    password: 'ley2024', roles: ['TITULAR'],      role: 'TITULAR',      domains: [],            active: true,  blocked: false },
];

/** Busca por credenciales — no verifica active/blocked (responsabilidad del llamador) */
export const findUser = (email: string, password: string): AppUser | undefined =>
  MOCK_USERS.find((u) => u.email === email && u.password === password);

/** Actualiza campos de un usuario en el array (persiste durante la sesión) */
export const updateMockUser = (id: string, updates: Partial<AppUser>) => {
  const idx = MOCK_USERS.findIndex((u) => u.id === id);
  if (idx !== -1) Object.assign(MOCK_USERS[idx], updates);
};

/** Agrega un nuevo usuario al array para que pueda iniciar sesión */
export const addMockUser = (user: AppUser) => {
  MOCK_USERS.push(user);
};
