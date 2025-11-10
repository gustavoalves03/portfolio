export interface NavigationRoute {
  label: string;
  path: string;
  icon?: string; // Material icon name
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
    label: 'Soins',
    path: '/cares',
    icon: 'spa'
  },
  {
    label: 'Rendez-vous',
    path: '/bookings',
    icon: 'calendar_month'
  },
  {
    label: 'Boutique',
    path: '/shop',
    icon: 'shopping_bag'
  },
  {
    label: 'Mon profil',
    path: '/profile',
    icon: 'person'
  },
  {
    label: 'À propos',
    path: '/about',
    icon: 'info'
  }
];
