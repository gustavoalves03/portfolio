export interface NavigationRoute {
  label: string;
  path: string;
  icon?: string; // Material icon name
  requiresAuth?: boolean;
  requiredRole?: 'PRO' | 'ADMIN' | 'EMPLOYEE';
  children?: NavigationRoute[];
}

/**
 * Configuration des routes de navigation pour le menu burger.
 * Modifiez ce fichier pour ajouter, supprimer ou réorganiser les routes.
 */
export const NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'Accueil',
    path: '/',
    icon: 'home'
  },
  {
    label: 'À propos',
    path: '/about',
    icon: 'info'
  }
];

export const PRO_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'Dashboard',
    path: '/pro/dashboard',
    icon: 'dashboard',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'Soins',
    path: '/pro/cares',
    icon: 'spa',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'Planning',
    path: '/pro/planning',
    icon: 'calendar_month',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'Mon salon',
    path: '/pro/salon',
    icon: 'storefront',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'Équipe',
    path: '/pro/employees',
    icon: 'groups',
    requiresAuth: true,
    requiredRole: 'PRO',
  }
];

export const CLIENT_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'Mes rendez-vous',
    path: '/bookings',
    icon: 'calendar_month',
    requiresAuth: true
  }
];

export const EMPLOYEE_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'Dashboard',
    path: '/employee/dashboard',
    icon: 'dashboard',
    requiresAuth: true,
  },
  {
    label: 'Planning',
    path: '/employee/planning',
    icon: 'calendar_month',
    requiresAuth: true,
  },
  {
    label: 'Réservations',
    path: '/employee/bookings',
    icon: 'event',
    requiresAuth: true,
  },
  {
    label: 'Congés',
    path: '/employee/leaves',
    icon: 'beach_access',
    requiresAuth: true,
  },
  {
    label: 'Documents',
    path: '/employee/documents',
    icon: 'folder',
    requiresAuth: true,
  },
];
