// app.routes.ts (Angular 21, standalone)
import { Routes } from '@angular/router';
import { ListVideoGames } from './features/video-games/ui/list-video-games/list-video-games';
import { Home } from './pages/home/home';
import { NotFound } from './pages/not-found/not-found';
import { About } from './pages/about/about';
import { CaresComponent } from './features/cares/cares.component';
import { CategoriesComponent } from './features/categories/categories.component';
import { UsersComponent } from './features/users/users.component';
import { BookingsComponent } from './features/bookings/bookings.component';
import { LoginComponent } from './pages/auth/login/login.component';
import { OAuth2RedirectComponent } from './pages/auth/oauth2-redirect/oauth2-redirect.component';
import { RegisterComponent } from './pages/auth/register/register.component';
import { ForgotPasswordComponent } from './pages/auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/auth/reset-password/reset-password.component';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'about', component: About },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'video-games', component: ListVideoGames },
  { path: 'cares', component: CaresComponent },
  { path: 'categories', component: CategoriesComponent },
  { path: 'users', component: UsersComponent },
  { path: 'bookings', component: BookingsComponent },
  { path: 'oauth2/redirect', component: OAuth2RedirectComponent },
  { path: '**', component: NotFound },
];
