// app.routes.ts (Angular 21, standalone)
import { Routes } from '@angular/router';
import { ListVideoGames } from './features/video-games/components/list-video-games/list-video-games';
import {Home} from './pages/home/home';
import {NotFound} from './pages/not-found/not-found';

export const routes: Routes = [
  { path: '', component: Home },            // pas de redirect
  { path: 'video-games', component: ListVideoGames },
  { path: '**', component: NotFound },      // ou supprime-le si pas encore prêt
];
