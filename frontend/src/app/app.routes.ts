// app.routes.ts (Angular 21, standalone)
import { Routes } from '@angular/router';
import { ListVideoGames } from './features/video-games/ui/list-video-games/list-video-games';
import { Home } from './pages/home/home';
import { NotFound } from './pages/not-found/not-found';
import { About } from './pages/about/about';
import { CaresComponent } from './features/cares/cares.component';
import { UsersComponent } from './features/users/users.component';
import { LoginComponent } from './pages/auth/login/login.component';
import { OAuth2RedirectComponent } from './pages/auth/oauth2-redirect/oauth2-redirect.component';
import { RegisterComponent } from './pages/auth/register/register.component';
import { ForgotPasswordComponent } from './pages/auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/auth/reset-password/reset-password.component';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { Role } from './core/auth/auth.model';

export const routes: Routes = [
  // Public routes
  { path: '', component: Home },
  { path: 'about', component: About },
  { path: 'login', component: LoginComponent },
  {
    path: 'pricing',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
  {
    path: 'register/pro',
    loadComponent: () => import('./pages/auth/register-pro/register-pro.component').then(m => m.RegisterProComponent),
  },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'video-games', component: ListVideoGames },
  { path: 'oauth2/redirect', component: OAuth2RedirectComponent },
  {
    path: 'discover',
    loadComponent: () => import('./pages/discover/discover-page.component').then(m => m.DiscoverPageComponent),
  },
  {
    path: 'salon/:slug',
    loadComponent: () => import('./pages/salon/salon-page.component').then(m => m.SalonPageComponent),
  },

  // Protected pro routes
  {
    path: 'pro',
    canActivate: [authGuard, roleGuard(Role.PRO)],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/pro/pro-dashboard.component').then(m => m.ProDashboardComponent),
      },
      {
        path: 'salon',
        loadComponent: () => import('./features/salon-profile/salon-profile.component').then(m => m.SalonProfileComponent),
      },
      {
        path: 'posts',
        loadComponent: () =>
          import('./pages/pro/pro-posts.component').then(m => m.ProPostsComponent),
      },
      { path: 'cares', component: CaresComponent },
      {
        path: 'planning',
        loadComponent: () => import('./pages/pro/pro-planning.component').then(m => m.ProPlanningComponent),
      },
      {
        path: 'bookings',
        loadComponent: () =>
          import('./pages/pro/pro-bookings.component').then(
            (m) => m.ProBookingsComponent
          ),
      },
      {
        path: 'employees',
        loadComponent: () =>
          import('./pages/pro/pro-employees.component').then(
            (m) => m.ProEmployeesComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./pages/pro/pro-settings.component').then(
            (m) => m.ProSettingsComponent
          ),
      },
      {
        path: 'manage',
        loadComponent: () =>
          import('./pages/pro/pro-manage.component').then(
            (m) => m.ProManageComponent
          ),
        data: { hideBottomNav: true },
      },
      {
        path: 'camera',
        loadComponent: () =>
          import('./pages/pro/pro-camera.component').then(
            (m) => m.ProCameraComponent
          ),
      },
      {
        path: 'clients/:userId',
        loadComponent: () =>
          import('./pages/pro/pro-client-detail.component').then(
            (m) => m.ProClientDetailComponent
          ),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },

  // Protected employee routes
  {
    path: 'employee',
    canActivate: [authGuard, roleGuard(Role.EMPLOYEE)],
    children: [
      {
        path: 'bookings',
        loadComponent: () =>
          import('./pages/employee/employee-bookings.component').then(
            (m) => m.EmployeeBookingsComponent
          ),
      },
      {
        path: 'leaves',
        loadComponent: () =>
          import('./pages/employee/employee-leaves.component').then(
            (m) => m.EmployeeLeavesComponent
          ),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./pages/employee/employee-documents.component').then(
            (m) => m.EmployeeDocumentsComponent
          ),
      },
      { path: '', redirectTo: 'bookings', pathMatch: 'full' },
    ],
  },

  // Protected authenticated routes
  {
    path: 'bookings',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/client-bookings/client-bookings.component').then(m => m.ClientBookingsComponent),
  },
  {
    path: 'my-evolution',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/client-evolution/client-evolution.component').then(m => m.ClientEvolutionComponent),
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/notifications/notifications.component').then(m => m.NotificationsComponent),
  },
  {
    path: 'users',
    canActivate: [authGuard, roleGuard(Role.ADMIN)],
    component: UsersComponent,
  },

  {
    path: 'feed',
    loadComponent: () => import('./pages/feed/feed.component').then(m => m.FeedComponent),
  },

  { path: '**', component: NotFound },
];
