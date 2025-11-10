import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { serverRoutes } from './app.routes.server';

const serverApiBase = (globalThis as any).process?.env?.API_BASE_URL ?? 'http://host.docker.internal:8080';

const serverConfig: ApplicationConfig = {
  providers: [
    // Use Docker network host for SSR to reach the backend container
    { provide: API_BASE_URL, useValue: serverApiBase },
    provideServerRendering(withRoutes(serverRoutes))
  ]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
