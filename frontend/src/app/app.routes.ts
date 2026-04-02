// app.routes.ts (Angular 21, standalone)
import { Routes } from '@angular/router';
import { ListVideoGames } from './features/video-games/ui/list-video-games/list-video-games';
import { Home } from './pages/home/home';
import { NotFound } from './pages/not-found/not-found';
import { About } from './pages/about/about';
import { CaresComponent } from './features/cares/cares.component';
import { UsersComponent } from './features/users/users.component';
import { BookingsComponent } from './features/bookings/bookings.component';
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
      { path: 'cares', component: CaresComponent },
      {
        path: 'planning',
        loadComponent: () => import('./pages/pro/pro-planning.component').then(m => m.ProPlanningComponent),
      },
      { path: 'bookings', component: BookingsComponent },
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
    path: 'users',
    canActivate: [authGuard, roleGuard(Role.ADMIN)],
    component: UsersComponent,
  },

  { path: '**', component: NotFound },
];
