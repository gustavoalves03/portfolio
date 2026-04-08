import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: 'salon/:slug',
    renderMode: RenderMode.Server
  },
  {
    path: 'pro/clients/:userId',
    renderMode: RenderMode.Server
  },
  {
    path: 'employee/clients/:userId',
    renderMode: RenderMode.Server
  },
  {
    path: 'my-evolution',
    renderMode: RenderMode.Server
  },
  {
    path: 'feed',
    renderMode: RenderMode.Server
  },
  {
    path: 'pro/manage',
    renderMode: RenderMode.Server
  },
  {
    path: 'pro/camera',
    renderMode: RenderMode.Server
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender
  },
];
