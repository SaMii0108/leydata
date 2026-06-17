export type Role = 'ADMIN' | 'DPO' | 'JEFE_DOMINIO' | 'TITULAR';

export interface AppUser {
  id: string;
  name: string;
  email: string;
  password?: string;
  /** Todos los roles asignados. Cuando length > 1, se muestra pantalla de selección. */
  roles?: Role[];
  role: Role;
  area: string | null;
}

export const MOCK_USERS: AppUser[] = [
  // Usuarios internos — un único rol
  { id: 'u1', name: 'Ana Torres',           email: 'ana@leydata.cl',      password: 'ley2024', role: 'ADMIN',        area: null },
  { id: 'u2', name: 'Carlos Ruiz',          email: 'carlos@leydata.cl',   password: 'ley2024', role: 'DPO',          area: null },
  { id: 'u3', name: 'María López',          email: 'maria@leydata.cl',    password: 'ley2024', role: 'JEFE_DOMINIO', area: 'Recursos Humanos' },
  { id: 'u4', name: 'Pedro Soto',           email: 'pedro@leydata.cl',    password: 'ley2024', role: 'JEFE_DOMINIO', area: 'Marketing' },
  { id: 'u5', name: 'Lucía Vargas',         email: 'lucia@leydata.cl',    password: 'ley2024', role: 'JEFE_DOMINIO', area: 'Tecnología' },
  // Usuario multirol (ADMIN + DPO)
  { id: 'u6', name: 'Aurora González',      email: 'aurora@leydata.cl',   password: 'ley2024', roles: ['ADMIN', 'DPO'], role: 'ADMIN', area: null },
  // Titulares de datos (portal mock mientras el back no está listo)
  { id: 't1', name: 'María José Fuentes',   email: 'mjfuentes@email.com', password: 'ley2024', role: 'TITULAR', area: null },
  { id: 't2', name: 'Carlos Andrés Pérez',  email: 'caperez@email.com',   password: 'ley2024', role: 'TITULAR', area: null },
  { id: 't3', name: 'Valentina Rojas',      email: 'vrojas@email.com',    password: 'ley2024', role: 'TITULAR', area: null },
];

export const AREAS = ['Marketing', 'Recursos Humanos', 'Tecnología', 'Legal', 'Finanzas'] as const;
export type Area = typeof AREAS[number];

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
