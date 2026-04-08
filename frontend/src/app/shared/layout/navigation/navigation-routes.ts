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
    label: 'nav.home',
    path: '/',
    icon: 'home'
  },
  {
    label: 'nav.discover',
    path: '/discover',
    icon: 'explore'
  },
  {
    label: 'nav.about',
    path: '/about',
    icon: 'info'
  }
];

export const PRO_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'nav.pro.dashboard',
    path: '/pro/dashboard',
    icon: 'dashboard',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'nav.pro.cares',
    path: '/pro/cares',
    icon: 'spa',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'nav.pro.planning',
    path: '/pro/planning',
    icon: 'calendar_month',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'nav.pro.posts',
    path: '/pro/posts',
    icon: 'photo_library',
    requiresAuth: true,
    requiredRole: 'PRO',
  },
  {
    label: 'nav.pro.salon',
    path: '/pro/salon',
    icon: 'storefront',
    requiresAuth: true,
    requiredRole: 'PRO'
  },
  {
    label: 'nav.pro.employees',
    path: '/pro/employees',
    icon: 'groups',
    requiresAuth: true,
    requiredRole: 'PRO',
  },
  {
    label: 'nav.pro.settings',
    path: '/pro/settings',
    icon: 'settings',
    requiresAuth: true,
    requiredRole: 'PRO',
  },
];

export const CLIENT_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'nav.client.bookings',
    path: '/bookings',
    icon: 'calendar_month',
    requiresAuth: true
  },
  {
    label: 'nav.client.evolution',
    path: '/my-evolution',
    icon: 'auto_awesome',
    requiresAuth: true
  }
];

export const EMPLOYEE_NAVIGATION_ROUTES: NavigationRoute[] = [
  {
    label: 'nav.employee.bookings',
    path: '/employee/bookings',
    icon: 'event',
    requiresAuth: true,
  },
  {
    label: 'nav.employee.leaves',
    path: '/employee/leaves',
    icon: 'beach_access',
    requiresAuth: true,
  },
  {
    label: 'nav.employee.documents',
    path: '/employee/documents',
    icon: 'folder',
    requiresAuth: true,
  },
];
