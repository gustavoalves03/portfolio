// app.routes.ts (Angular 21, standalone)
import { Routes } from '@angular/router';
import { ListVideoGames } from './features/video-games/ui/list-video-games/list-video-games';
import { Home } from './pages/home/home';
import { NotFound } from './pages/not-found/not-found';
import { About } from './pages/about/about';
import { Cares } from './features/cares/cares';
import { Categories } from './features/categories/categories';
import { Users } from './features/users/users';
import { Bookings } from './features/bookings/bookings';

export const routes: Routes = [
  { path: '', component: Home },            // pas de redirect
  { path: 'about', component: About },
  { path: 'video-games', component: ListVideoGames },
  { path: 'cares', component: Cares },
  { path: 'categories', component: Categories },
  { path: 'users', component: Users },
  { path: 'bookings', component: Bookings },
  { path: '**', component: NotFound },      // ou supprime-le si pas encore prêt
];
