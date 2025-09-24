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

export const routes: Routes = [
  { path: '', component: Home },            // pas de redirect
  { path: 'about', component: About },
  { path: 'video-games', component: ListVideoGames },
  { path: 'cares', component: CaresComponent },
  { path: 'categories', component: CategoriesComponent },
  { path: 'users', component: UsersComponent },
  { path: 'bookings', component: BookingsComponent },
  { path: '**', component: NotFound },      // ou supprime-le si pas encore prêt
];
